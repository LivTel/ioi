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
 * This class provides the implementation for the BIAS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class BIASImplementation extends EXPOSEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public BIASImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.BIAS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.BIAS";
	}

	/**
	 * This method returns the BIAS command's acknowledge time. 
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see #serverConnectionThread
	 * @see #status
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the BIAS command. 
	 * @see EXPOSEImplementation#sendACK
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		BIAS biasCommand = (BIAS)command;
		BIAS_DONE biasDone = new BIAS_DONE(command.getId());
		SetFSParamCommand setFSParamCommand = null;
		SetRampParamCommand setRampParamCommand = null;
		AcquireRampCommand acquireRampCommand = null;
		FitsFilename fitsFilename = null;
		List<File> fitsFileList = null;
		DataProcessingThread dataProcessingThread = null;
		String filename = null;
		long acquireRampCommandCallTime;
		int index,bFS,nReset,nRead,nGroup,nDrop,groupExecutionTime;
		boolean fitsFilenameRename;

		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			":processCommand:Starting BIAS command.");
		if(testAbort(biasCommand,biasDone) == true)
			return biasDone;
	// setup exposure status.
		status.setExposureCount(1);
		status.setExposureNumber(0);
		// retrieve the data processing thread
		dataProcessingThread = ioi.getDataProcessingThread();
		// if we are renaming the FITS images, increment the MULTRUN number
		fitsFilenameRename = status.getPropertyBoolean("ioi.file.fits.rename");
		if(fitsFilenameRename)
		{
			fitsFilename = ioi.getFitsFilename();
			fitsFilename.nextMultRunNumber();
		}
		// Find out which sampling mode the array is using
		try
		{
			bFS = getFSMode();
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":processCommand:getFSMode failed:"+command,e);
			biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+700);
			biasDone.setErrorString(e.toString());
			biasDone.setSuccessful(false);
			return biasDone;
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
				// 1 group, 0.0 exposure length, 1 ramp
				setFSParamCommand.setCommand(nReset,nRead,1,0.0,1);
				setFSParamCommand.sendCommand();
				if(setFSParamCommand.getReplyErrorCode() != 0)
				{
					ioi.error(this.getClass().getName()+":processCommand:SetFSParam failed:"+
						  setFSParamCommand.getReplyErrorCode()+":"+
						  setFSParamCommand.getReplyErrorString());
					biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+701);
					biasDone.setErrorString("processCommand:SetFSParam failed:"+
								setFSParamCommand.getReplyErrorCode()+":"+
								setFSParamCommand.getReplyErrorString());
					biasDone.setSuccessful(false);
					return biasDone;
				}
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:SetFSParam failed:"+command,e);
				biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+702);
				biasDone.setErrorString(":processCommand:SetFSParam failed:"+e);
				biasDone.setSuccessful(false);
				return biasDone;
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
				nGroup = 1;
				// 1 group, 1 ramp
				setRampParamCommand.setCommand(nReset,nRead,1,nDrop,1);
				// or 1 read, 1 group, 0 drops, 1 ramp
				//setRampParamCommand.setCommand(nReset,1,1,0,1);				
				setRampParamCommand.sendCommand();
				if(setRampParamCommand.getReplyErrorCode() != 0)
				{
					ioi.error(this.getClass().getName()+":processCommand:SetRampParam failed:"+
						  setRampParamCommand.getReplyErrorCode()+":"+
						  setRampParamCommand.getReplyErrorString());
					biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+703);
					biasDone.setErrorString("processCommand:SetRampParam failed:"+
								setRampParamCommand.getReplyErrorCode()+":"+
								setRampParamCommand.getReplyErrorString());
					biasDone.setSuccessful(false);
					return biasDone;
				}
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+
					  ":processCommand:SetRampParam failed:"+command,e);
				biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+704);
				biasDone.setErrorString("processCommand:SetRampParam failed:"+e);
				biasDone.setSuccessful(false);
				return biasDone;
			}
		}
		// do bias
		// get fits headers
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":processCommand:Retrieving FITS headers.");
		clearFitsHeaders();
		if(setFitsHeaders(biasCommand,biasDone,FitsHeaderDefaults.OBSTYPE_VALUE_BIAS,0) == false)
		{
			return biasDone;
		}
		if(getFitsHeadersFromISS(biasCommand,biasDone) == false)
		{
			return biasDone;
		}
		if(testAbort(biasCommand,biasDone) == true)
		{
			return biasDone;
		}
		if(getFitsHeadersFromBSS(biasCommand,biasDone) == false)
		{
			return biasDone;
		}
		if(testAbort(biasCommand,biasDone) == true)
		{
			return biasDone;
		}
		// get a timestamp before taking an exposure
		// we will use this to find the generated directory
		acquireRampCommandCallTime = System.currentTimeMillis();
		// do exposure.
		try
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Acquiring ramp.");
			acquireRampCommand = new AcquireRampCommand();
			acquireRampCommand.sendCommand();
			if(acquireRampCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+":processCommand:AcquireRamp failed:"+
					  acquireRampCommand.getReplyErrorCode()+":"+
					  acquireRampCommand.getReplyErrorString());
				biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+705);
				biasDone.setErrorString("processCommand:AcquireRamp failed:"+
							acquireRampCommand.getReplyErrorCode()+":"+
							acquireRampCommand.getReplyErrorString());
				biasDone.setSuccessful(false);
				return biasDone;
			}
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":processCommand:AcquireRampCommand failed:"+command+":",e);
			biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+706);
			biasDone.setErrorString("processCommand:AcquireRampCommand failed:"+e);
			biasDone.setSuccessful(false);
			return biasDone;
		}
		// find the data just acquired
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":processCommand:Adding exposure to the data processing list.");
		try
		{
			if(fitsFilenameRename)
				fitsFilename.nextRunNumber();
			dataProcessingThread.addDataForProcessing(bFS,acquireRampCommandCallTime,
								  ioiFitsHeader,fitsFilename);
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":processCommand:rocessing acquired data failed:"+command+":"+e.toString());
			biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+707);
			biasDone.setErrorString(e.toString());
			biasDone.setSuccessful(false);
			return biasDone;
		}
		biasDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		biasDone.setErrorString("");
		biasDone.setSuccessful(true);
	// return done object.
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":processCommand:BIAS command completed.");
		return biasDone;
	}
}
