// MULTRUNImplementation.java
// $HeadURL$
// $Revision$
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
	 *      <li>We issue a RA/DEC offset to the ISS, for sky dithering (<b>offsetTelescope</b>).
	 * 	<li>It generates some FITS headers from the CCD setup, ISS and BSS. 
	 * 	<li>Sets the time of exposure and saves the Fits headers.
	 * 	<li>It performs an exposure and saves the data from this to disc.
	 * 	<li>Keeps track of the generated filenames in the list.
	 * 	</ul>
	 * <li>We offset the telscope back to 0,0 using resetTelescopeOffset.
	 * <li>It sets up the return values to return to the client.
	 * </ul>
	 * The resultant filename or the relevant error code is put into the an object of class MULTRUN_DONE and
	 * returned. During execution of these operations the abort flag is tested to see if we need to
	 * stop the implementation of this command.
	 * @see #offsetTelescope
	 * @see #resetTelescopeOffset
	 * @see CommandImplementation#testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see FITSImplementation#getFitsHeadersFromBSS
	 * @see EXPOSEImplementation#reduceExpose
	 * @see HardwareImplementation#getFSMode
	 * @see IOIStatus#setExposureLength
	 * @see IOIStatus#setExposureCount
	 * @see IOIStatus#setExposureNumber
	 * @see IOIStatus#setExposureStartTime
	 * @see IOIStatus#setCurrentMode
	 * @see IOIStatus#getCurrentMode
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_EXPOSING
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_READING_OUT
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
		Vector reduceFilenameList = null;
		List fitsFileList = null;
		String obsType = null;
		String directory = null;
		String filename = null;
		double exposureLengthSeconds;
		long acquireRampCommandCallTime;
		int index,bFS,nReset,nRead,nGroup,nDrop,groupExecutionTime;
		char exposureCode;
		boolean retval = false;

		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			":processCommand:Starting MULTRUN with exposure length "+multRunCommand.getExposureTime()+
			" ms and number of exposures "+multRunCommand.getNumberExposures()+".");
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
	// setup exposure status.
		status.setExposureCount(multRunCommand.getNumberExposures());
		status.setExposureNumber(0);
		status.setExposureLength(multRunCommand.getExposureTime());
	// move the fold mirror to the correct location
		if(moveFold(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		if(multRunCommand.getStandard())
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_STANDARD;
			exposureCode = FitsFilename.EXPOSURE_CODE_STANDARD;
		}
		else
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_EXPOSURE;
			exposureCode = FitsFilename.EXPOSURE_CODE_EXPOSURE;
		}
		// configure the array 
		exposureLengthSeconds = ((double)(multRunCommand.getExposureTime())/1000.0);
		try
		{
			// Find out which sampling mode the array is using
			try
			{
				idlTelnetConnection = ioi.getIDLTelnetConnection();
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:Opening IDL Socket Server Conenction failed:"+command,e);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1215);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			try
			{
				bFS = getFSMode(idlTelnetConnection);
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
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":processCommand:Configuring Fowler sampling mode.");
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
						idlTelnetConnection.close();
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
					//idlTelnetConnection.close();
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
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":processCommand:Configuring read-up-the-ramp mode.");
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
						idlTelnetConnection.close();
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
					//idlTelnetConnection.close();
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
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Starting exposure "+index+".");
				// RA/Dec Offset for sky dithering.
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Offseting telescope.");
				if(offsetTelescope(multRunCommand,multRunDone,index) == false)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				// get fits headers
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Retrieving FITS headers.");
				clearFitsHeaders();
				if(setFitsHeaders(multRunCommand,multRunDone,obsType,
						  multRunCommand.getExposureTime(),multRunCommand.getNumberExposures()) == false)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				if(getFitsHeadersFromISS(multRunCommand,multRunDone) == false)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				if(getFitsHeadersFromBSS(multRunCommand,multRunDone) == false)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					return multRunDone;
				}
				// get a timestamp before taking an exposure
				// we will use this to find the generated directory
				acquireRampCommandCallTime = System.currentTimeMillis();
				status.setExposureStartTime(acquireRampCommandCallTime);
				status.setCurrentMode(GET_STATUS_DONE.MODE_EXPOSING);
				// do exposure.
				try
				{
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":processCommand:Acquiring ramp.");
					acquireRampCommand = new AcquireRampCommand();
					acquireRampCommand.setTelnetConnection(idlTelnetConnection);
					acquireRampCommand.sendCommand();
					if(acquireRampCommand.getReplyErrorCode() != 0)
					{
						ioi.error(this.getClass().getName()+
							  ":processCommand:AcquireRamp failed:"+
							  acquireRampCommand.getReplyErrorCode()+":"+
							  acquireRampCommand.getReplyErrorString());
						status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
						//moveFilterToBlank(multRunCommand,multRunDone);
						resetTelescopeOffset(multRunCommand,multRunDone);
						//idlTelnetConnection.close();
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
						  ":processCommand:AcquireRampCommand failed:"+command+":"+
						  e.toString());
					status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1200);
					multRunDone.setErrorString(e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				// We are not really reading out, but managing the acquired data
				status.setCurrentMode(GET_STATUS_DONE.MODE_READING_OUT);
				// find the data just acquired
				try
				{
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":processCommand:Finding ramp data.");
					directory = findRampData(idlTelnetConnection,acquireRampCommandCallTime);
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":processCommand:Listing FITS images in Ramp Data directory "+directory+".");
					fitsFileList = findFITSFilesInDirectory(directory);
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":processCommand:Adding FITS headers to "+fitsFileList.size()+" FITS images.");
					addFitsHeadersToFitsImages(fitsFileList);
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":processCommand:Rename generated FITS images to LT spec (if enabled).");
					renameFitsFiles(fitsFileList,exposureCode);
				}
				catch(Exception e)
				{
					retval = false;
					ioi.error(this.getClass().getName()+
						  ":processCommand:Processing acquired data failed:"+command+":",e);
					status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					//idlTelnetConnection.close();
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1201);
					multRunDone.setErrorString(this.getClass().getName()+
								   ":processCommand:findRampData failed:"+e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Ramp data found in directory:"+directory);
				// for now, the returned filename is set to the directory containing the result data set.
				filename = directory;
				// send acknowledge to say frames are completed.
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Sending ACK.");
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
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
				//idlTelnetConnection.close();
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1202);
					multRunDone.setErrorString(e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				status.setExposureNumber(index+1);
				// add filename to list for data pipeline processing.
				reduceFilenameList.addAll(fitsFileList);
				// test whether an abort has occured.
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					retval = false;
				}
				index++;
			}// end while
			// if a failure occurs, return now
			if(!retval)
			{
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				//idlTelnetConnection.close();
				return multRunDone;
			}
		}
		finally
		{
			try
			{
				idlTelnetConnection.close();
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:IDL Socket Server Telnet Connection close failed:",e);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1214);
				multRunDone.setErrorString("processCommand:IDL Socket Server Telnet Connection close failed:"+
							   e);
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
		}
		//moveFilterToBlank(multRunCommand,multRunDone);
	// reset telescope offsets
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":processCommand:Reseting telescope offset.");
		if(resetTelescopeOffset(multRunCommand,multRunDone) == false)
			return multRunDone;
		index = 0;
		retval = true;
	// call pipeline to process data and get results
		if(multRunCommand.getPipelineProcess())
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Data pipelining.");
			// diddly this is wrong
			while(retval&&(index < reduceFilenameList.size()))
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
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":processCommand:MULTRUN command completed.");
		return multRunDone;
	}

	/**
	 * Method to offset the telescope by a small amount in RA/Dec, to dither the sky.
	 * @param multRunCommand The command requiring this configuration to be done.
	 * @param multRunDone The done message, filled in if with a suitable error if the method failed.
	 * @param index The index in the list of exposures to do in the Multrun, from 0 to the number
	 *        of exposures specified by the MULTRUN. This number is used to calculate which
	 *        RA/Dec offset to use.
	 * @return The method returns true on success and false on failure.	 
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 */
	protected boolean offsetTelescope(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,int index) 
	{
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		int raDecOffsetCount,raDecOffsetIndex,offsetSleepTime;
		float raOffset,decOffset;
		boolean doRADecOffset,waitForOffsetToComplete;

	 // get configuration
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":offsetTelescope:Starting, retrieveing config.");
		try
		{
			doRADecOffset = status.getPropertyBoolean("ioi.multrun.offset.enable");
			waitForOffsetToComplete = status.getPropertyBoolean("ioi.multrun.offset.wait_for_complete");
			offsetSleepTime =  status.getPropertyInteger("ioi.multrun.offset.wait_sleep_time");
			raDecOffsetCount = status.getPropertyInteger("ioi.multrun.offset.count");
			raDecOffsetIndex = index % raDecOffsetCount;
			raOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".ra");
			decOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".dec");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":offsetTelescope:"+multRunCommand+":"+e.toString());
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1210);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		if(doRADecOffset)
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:We are going to physically move the telescope ("+
				raOffset+","+decOffset+").");
			// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC(multRunCommand.getId());
			offsetRaDecCommand.setRaOffset(raOffset);
			offsetRaDecCommand.setDecOffset(decOffset);
			instToISSDone = ioi.sendISSCommand(offsetRaDecCommand,serverConnectionThread,true,
							   waitForOffsetToComplete);
			// if we are waiting for the offset to complete, and it returns an error, return an error.
			if(waitForOffsetToComplete && (instToISSDone.getSuccessful() == false))
			{
				String errorString = null;
				
				errorString = new String("Offset Ra Dec failed:ra = "+raOffset+
							 ", dec = "+decOffset+":"+instToISSDone.getErrorString());
				ioi.error(errorString);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1211);
				multRunDone.setErrorString(this.getClass().getName()+":offsetTelescope:"+errorString);
				multRunDone.setSuccessful(false);
				return false;
			}
			// if we have not waited for the offset to complete, and we are supposed to be waiting a
			// configured time for the offset to have been done, wait a bit.
			if(!waitForOffsetToComplete)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":offsetTelescope:We have sent the telescope offset, "+
					"but have NOT waited for the DONE.");
				if(offsetSleepTime > 0)
				{
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":offsetTelescope:We have sent the telescope offset, "+
						"but have NOT waited for the DONE, so are waiting here for "+
						offsetSleepTime+" ms.");
					try
					{
						Thread.sleep(offsetSleepTime);
					}
					catch(InterruptedException e)
					{
						ioi.error(this.getClass().getName()+
							  ":offsetTelescope:Offset sleep time was interrupted.",e);
					}
				}
			}
		}// end if
		else
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:Offsets NOT enabled:"+
				"We are NOT going to physically move the telescope ("+raOffset+","+decOffset+").");
		}
		return true;
	}

	/**
	 * Method to reset the telescope offset to 0,0.
	 * @param multRunCommand The command requiring this configuration to be done.
	 * @param multRunDone The done message, filled in if with a suitable error if the method failed.
	 * @return The method returns true on success and false on failure.	 
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 */
	protected boolean resetTelescopeOffset(MULTRUN multRunCommand,MULTRUN_DONE multRunDone) 
	{
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		boolean doRADecOffset;

		try
		{
			doRADecOffset = status.getPropertyBoolean("ioi.multrun.offset.enable");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":offsetTelescope:"+multRunCommand+":"+e.toString());
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1212);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		if(doRADecOffset)
		{
			// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC(multRunCommand.getId());
			offsetRaDecCommand.setRaOffset(0.0f);
			offsetRaDecCommand.setDecOffset(0.0f);
			instToISSDone = ioi.sendISSCommand(offsetRaDecCommand,serverConnectionThread);
			if(instToISSDone.getSuccessful() == false)
			{
				String errorString = null;
				
				errorString = new String("Resetting Offset Ra Dec failed:"+
							 instToISSDone.getErrorString());
				ioi.error(errorString);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1213);
				multRunDone.setErrorString(this.getClass().getName()+
							   ":resetTelescopeOffset:"+errorString);
				multRunDone.setSuccessful(false);
				return false;
			}
		}// end if
		return true;
	}
}
//
// $Log$
//
