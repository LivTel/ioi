// MULTRUNImplementation.java
// $Header$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.net.*;
import ngat.supircam.temperaturecontroller.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class MULTRUNImplementation extends EXPOSEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public MULTRUNImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTRUN&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTRUN";
	}

	/**
	 * This method returns the MULTRUN command's acknowledge time. Each frame in the MULTRUN takes 
	 * the exposure time plus the status's max readout time plus the default acknowledge time to complete. 
	 * The default acknowledge time
	 * allows time to setup the camera, get information about the telescope and save the frame to disk.
	 * This method returns the time for the first frame in the MULTRUN only, as a MULTRUN_ACK message
	 * is returned to the client for each frame taken.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see #serverConnectionThread
	 * @see #status
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see MULTRUN#getExposureTime
	 * @see MULTRUN#getNumberExposures
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(multRunCommand.getExposureTime()+
			serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTRUN command. 
	 * <ul>
	 * <li>It moves the fold mirror to the correct location.
	 * <li>For each exposure it performs the following:
	 *	<ul>
	 * 	<li>It generates some FITS headers from the CCD setup, ISS and BSS. 
	 * 	<li>Sets the time of exposure and saves the Fits headers.
	 * 	<li>It performs an exposure and saves the data from this to disc.
	 * 	<li>Keeps track of the generated filenames in the list.
	 * 	</ul>
	 * <li>It sets up the return values to return to the client.
	 * </ul>
	 * The resultant filename or the relevant error code is put into the an object of class MULTRUN_DONE and
	 * returned. During execution of these operations the abort flag is tested to see if we need to
	 * stop the implementation of this command.
	 * @see CommandImplementation#testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see FITSImplementation#getFitsHeadersFromBSS
	 * @see EXPOSEImplementation#reduceExpose
	 * @see HardwareImplementation#getFSMode
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		MULTRUN_ACK multRunAck = null;
		MULTRUN_DP_ACK multRunDpAck = null;
		MULTRUN_DONE multRunDone = new MULTRUN_DONE(command.getId());
		TelnetConnection idlTelnetConnection = null;
		SetFSParamCommand setFSParamCommand = null;
		SetRampParamCommand setRampParamCommand = null;
		AcquireRampCommand acquireRampCommand = null;
		Vector filenameList = null;
		Vector reduceFilenameList = null;
		String obsType = null;
		String directory = null;
		String filename = null;
		double exposureLengthSeconds;
		long acquireRampCommandCallTime;
		int index,bFS,nReset,nRead,nGroup,nDrop,groupExecutionTime;
		boolean retval = false;

		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
	// setup exposure status.
		status.setExposureCount(multRunCommand.getNumberExposures());
		status.setExposureNumber(0);
	// move the fold mirror to the correct location
		if(moveFold(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		if(multRunCommand.getStandard())
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_STANDARD;
		else
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_EXPOSURE;
		// configure the array 
		idlTelnetConnection = ioi.getIDLTelnetConnection();
		exposureLengthSeconds = ((double)(multRunCommand.getExposureTime())/1000.0);
		// Find out which sampling mode the array is using
		try
		{
			bFS = getFSMode();
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":processCommand:getFSMode failed:"+command,e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1206);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return multRunDone;
		}
		if(bFS == 1)// Fowler sampling mdoe
		{
			try
			{
				nReset = status.getPropertyInteger("ioi.config.FOWLER.nreset");
				nRead = status.getPropertyInteger("ioi.config.FOWLER.nread");
				setFSParamCommand = new SetFSParamCommand();
				setFSParamCommand.setTelnetConnection(idlTelnetConnection);
				setFSParamCommand.setCommand(nReset,nRead,1,exposureLengthSeconds,1);
				setFSParamCommand.sendCommand();
				if(setFSParamCommand.getReplyErrorCode() != 0)
				{
					ioi.error(this.getClass().getName()+":processCommand:SetFSParam failed:"+
						  setFSParamCommand.getReplyErrorCode()+":"+
						  setFSParamCommand.getReplyErrorString());
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1204);
					multRunDone.setErrorString("processCommand:SetFSParam failed:"+
								   setFSParamCommand.getReplyErrorCode()+":"+
								   setFSParamCommand.getReplyErrorString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:SetFSParam failed:"+command,e);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1207);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
		}
		else if(bFS == 0)// Read Up the Ramp mode
		{
			try
			{
				nReset = status.getPropertyInteger("ioi.config.UP_THE_RAMP.nreset");
				nRead = status.getPropertyInteger("ioi.config.UP_THE_RAMP.nread");
				nDrop = status.getPropertyInteger("ioi.config.UP_THE_RAMP.ndrop");
				groupExecutionTime = status.getPropertyInteger("ioi.config.UP_THE_RAMP.group_execution_time");
				setRampParamCommand = new SetRampParamCommand();
				setRampParamCommand.setTelnetConnection(idlTelnetConnection);
				nGroup = ((int)(exposureLengthSeconds*1000/groupExecutionTime));
				setRampParamCommand.setCommand(nReset,nRead,nGroup,nDrop,1);
				setRampParamCommand.sendCommand();
				if(setRampParamCommand.getReplyErrorCode() != 0)
				{
					ioi.error(this.getClass().getName()+":processCommand:SetRampParam failed:"+
						  setRampParamCommand.getReplyErrorCode()+":"+
						  setRampParamCommand.getReplyErrorString());
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1205);
					multRunDone.setErrorString("processCommand:SetRampParam failed:"+
								   setRampParamCommand.getReplyErrorCode()+":"+
								   setRampParamCommand.getReplyErrorString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:SetRampParam failed:"+command,e);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1208);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
		}
	// do exposures
		index = 0;
		retval = true;
		reduceFilenameList = new Vector();
		while(retval&&(index < multRunCommand.getNumberExposures()))
		{
		// initialise list of FITS filenames for this frame
			filenameList = new Vector();
		// get fits headers
			clearFitsHeaders();
			if(setFitsHeaders(multRunCommand,multRunDone,obsType,
				multRunCommand.getExposureTime(),multRunCommand.getNumberExposures()) == false)
			{
				return multRunDone;
			}
			if(getFitsHeadersFromISS(multRunCommand,multRunDone) == false)
			{
				return multRunDone;
			}
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				return multRunDone;
			}
			if(getFitsHeadersFromBSS(multRunCommand,multRunDone) == false)
			{
				return multRunDone;
			}
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				return multRunDone;
			}
			// get a timestamp before taking an exposure
			// we will use this to find the generated directory
			acquireRampCommandCallTime = System.currentTimeMillis();
		// do exposure.
			try
			{
				acquireRampCommand = new AcquireRampCommand();
				acquireRampCommand.setTelnetConnection(idlTelnetConnection);
				acquireRampCommand.sendCommand();
				if(acquireRampCommand.getReplyErrorCode() != 0)
				{
					ioi.error(this.getClass().getName()+":processCommand:AcquireRamp failed:"+
						  acquireRampCommand.getReplyErrorCode()+":"+
						  acquireRampCommand.getReplyErrorString());
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1209);
					multRunDone.setErrorString("processCommand:AcquireRamp failed:"+
								   acquireRampCommand.getReplyErrorCode()+":"+
								   acquireRampCommand.getReplyErrorString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
			}
			catch(Exception e)
			{
				retval = false;
				ioi.error(this.getClass().getName()+
					":processCommand:AcquireRampCommand failed:"+command+":"+e.toString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1200);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			// find the data just acquired
			try
			{
				directory = findRampData(acquireRampCommandCallTime);
			}
			catch(Exception e)
			{
				retval = false;
				ioi.error(this.getClass().getName()+
					":processCommand:findRampData failed:"+command+":"+e.toString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1201);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			// for now, the returned filename is set to the directory containing the result data set.
			filename = directory;
		// send acknowledge to say frame is completed.
			multRunAck = new MULTRUN_ACK(command.getId());
			multRunAck.setTimeToComplete(multRunCommand.getExposureTime()+
						     serverConnectionThread.getDefaultAcknowledgeTime());
			multRunAck.setFilename(filename);
			try
			{
				serverConnectionThread.sendAcknowledge(multRunAck);
			}
			catch(IOException e)
			{
				retval = false;
				ioi.error(this.getClass().getName()+
					":processCommand:sendAcknowledge:"+command+":"+e.toString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1202);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			status.setExposureNumber(index+1);
		// add filename to list for data pipeline processing.
			reduceFilenameList.addAll(filenameList);
		// test whether an abort has occured.
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				retval = false;
			}
			index++;
		}// end while
	// if a failure occurs, return now
		if(!retval)
			return multRunDone;
		index = 0;
		retval = true;
	// call pipeline to process data and get results
		if(multRunCommand.getPipelineProcess())
		{
			while(retval&&(index < multRunCommand.getNumberExposures()))
			{
				filename = (String)reduceFilenameList.get(index);
			// do reduction.
				retval = reduceExpose(multRunCommand,multRunDone,filename);
			// send acknowledge to say frame has been reduced.
				multRunDpAck = new MULTRUN_DP_ACK(command.getId());
				multRunDpAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
			// copy Data Pipeline results from DONE to ACK
				multRunDpAck.setFilename(multRunDone.getFilename());
				multRunDpAck.setCounts(multRunDone.getCounts());
				multRunDpAck.setSeeing(multRunDone.getSeeing());
				multRunDpAck.setXpix(multRunDone.getXpix());
				multRunDpAck.setYpix(multRunDone.getYpix());
				multRunDpAck.setPhotometricity(multRunDone.getPhotometricity());
				multRunDpAck.setSkyBrightness(multRunDone.getSkyBrightness());
				multRunDpAck.setSaturation(multRunDone.getSaturation());
				try
				{
					serverConnectionThread.sendAcknowledge(multRunDpAck);
				}
				catch(IOException e)
				{
					retval = false;
					ioi.error(this.getClass().getName()+
						":processCommand:sendAcknowledge(DP):"+command+":"+e.toString());
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1203);
					multRunDone.setErrorString(e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					retval = false;
				}
				index++;
			}// end while on MULTRUN exposures
		}// end if Data Pipeline is to be called
		else
		{
		// no pipeline processing occured, set return value to something bland.
		// set filename to last filename exposed.
			multRunDone.setFilename(filename);
			multRunDone.setCounts(0.0f);
			multRunDone.setSeeing(0.0f);
			multRunDone.setXpix(0.0f);
			multRunDone.setYpix(0.0f);
			multRunDone.setPhotometricity(0.0f);
			multRunDone.setSkyBrightness(0.0f);
			multRunDone.setSaturation(false);
		}
	// if a failure occurs, return now
		if(!retval)
			return multRunDone;
	// setup return values.
	// setCounts,setFilename,setSeeing,setXpix,setYpix 
	// setPhotometricity, setSkyBrightness, setSaturation set by reduceExpose for last image reduced.
		multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		multRunDone.setErrorString("");
		multRunDone.setSuccessful(true);
	// return done object.
		return multRunDone;
	}
}
//
// $Log$
//
