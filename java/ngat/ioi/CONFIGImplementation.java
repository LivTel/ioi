// CONFIGImplementation.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;

import ngat.ioi.command.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.net.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the CONFIG command sent to a server using the
 * Java Message System. It extends SETUPImplementation.
 * @see SETUPImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class CONFIGImplementation extends SETUPImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor. 
	 */
	public CONFIGImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.CONFIG&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.CONFIG";
	}

	/**
	 * This method gets the CONFIG command's acknowledge time.
	 * This method returns an ACK with timeToComplete set to the &quot; ioi.config.acknowledge_time &quot;
	 * held in the IO:I configuration file. 
	 * If this cannot be found/is not a valid number the default acknowledge time is used instead.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set to a time (in milliseconds).
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;
		int timeToComplete = 0;

		acknowledge = new ACK(command.getId());
		try
		{
			timeToComplete += ioi.getStatus().getPropertyInteger("ioi.config.acknowledge_time");
		}
		catch(NumberFormatException e)
		{
			ioi.error(this.getClass().getName()+":calculateAcknowledgeTime:"+e);
			timeToComplete += serverConnectionThread.getDefaultAcknowledgeTime();
		}
		acknowledge.setTimeToComplete(timeToComplete);
		return acknowledge;
	}

	/**
	 * This method implements the CONFIG command. 
	 * <ul>
	 * <li>It checks the message contains a suitable IRCamConfig object to configure the controller.
	 * <li>If filter wheels are enabled, we call setFocusOffset to send a focus offset to the ISS.
	 * <li>It increments the unique configuration ID.
	 * </ul>
	 * An object of class CONFIG_DONE is returned. If an error occurs a suitable error message is returned.
	 * @see ngat.phase2.IRCamConfig
	 * @see IOI#getStatus
	 * @see IOIStatus#setCurrentMode
	 * @see FITSImplementation#setFocusOffset
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_CONFIGURING
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		CONFIG configCommand = null;
		IRCamConfig config = null;
		Detector detector = null;
		CONFIG_DONE configDone = null;
		IOIStatus status = null;
		SetFSModeCommand setFSModeCommand = null;
		TelnetConnection idlTelnetConnection = null;
		String fsModeString = null;
		int fsMode;
		int filterWheelPosition;
		boolean filterWheelEnable;

	// test contents of command.
		configCommand = (CONFIG)command;
		configDone = new CONFIG_DONE(command.getId());
		status = ioi.getStatus();
		if(testAbort(configCommand,configDone) == true)
			return configDone;
		if(configCommand.getConfig() == null)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+command+":Config was null.");
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+800);
			configDone.setErrorString(":Config was null.");
			configDone.setSuccessful(false);
			return configDone;
		}
		if((configCommand.getConfig() instanceof IRCamConfig) == false)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+command+":Config has wrong class:"+
				configCommand.getConfig().getClass().getName());
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+801);
			configDone.setErrorString(":Config has wrong class:"+
				configCommand.getConfig().getClass().getName());
			configDone.setSuccessful(false);
			return configDone;
		}
	// get ioiConfig from configCommand.
		config = (IRCamConfig)configCommand.getConfig();
	// get local detector copy
		detector = config.getDetector(0);
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
	// check xbin and ybin: they must equal 1
		if((detector.getXBin()!=1)||(detector.getYBin()!=1))
		{
			String errorString = null;

			errorString = new String("Illegal xBin and yBin:xBin="+detector.getXBin()+",yBin="+
						detector.getYBin());
			ioi.error(this.getClass().getName()+":processCommand:"+command+":"+errorString);
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+807);
			configDone.setErrorString(errorString);
			configDone.setSuccessful(false);
			return configDone;
		}
		// Get config data
		try
		{
			fsModeString = status.getProperty("ioi.config.fs_mode");
			fsMode = SetFSModeCommand.parseMode(fsModeString);
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+
				  command+":Getting configuration data from property file failed:",e);
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+802);
			configDone.setErrorString("Getting configuration data from property file failed:"+
						  e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
		status.setCurrentMode(GET_STATUS_DONE.MODE_CONFIGURING);
		try
		{
			// configure array readout mode (fowler sample mode/read up the ramp mode)
			idlTelnetConnection = ioi.getIDLTelnetConnection();
			setFSModeCommand = new SetFSModeCommand();
			setFSModeCommand.setTelnetConnection(idlTelnetConnection);
			setFSModeCommand.setCommand(fsMode);
			setFSModeCommand.sendCommand();
			if(setFSModeCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+":processCommand:"+command+":SetFSMode failed:"+
					  setFSModeCommand.getReplyErrorCode()+":"+
					  setFSModeCommand.getReplyErrorString());
				status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
				configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+804);
				configDone.setErrorString("SetFSMode failed:"+setFSModeCommand.getReplyErrorCode()+":"+
					  setFSModeCommand.getReplyErrorString());
				configDone.setSuccessful(false);
				return configDone;
			}
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+command+":SetFSMode failed:",e);
			status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+803);
			configDone.setErrorString("SetFSMode failed:"+e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
		finally
		{
			status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
			try
			{
				idlTelnetConnection.close();
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+":processCommand:"+command+
					  ":Closing IDL Socket Server Telnet Connection failed:",e);
				configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+808);
				configDone.setErrorString("Closing IDL Socket Server Telnet Connection failed:"+e);
				configDone.setSuccessful(false);
				return configDone;
			}
		}
	// send focus offset 
		try
		{
			setFocusOffset(configCommand.getId());
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+
				command+":setFocusOffset failed:",e);
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+805);
			configDone.setErrorString("setFocusOffset failed:"+e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Increment unique config ID.
	// This is queried when saving FITS headers to get the CONFIGID value.
		try
		{
			status.incConfigId();
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":processCommand:"+
				  command+":Incrementing configuration ID:"+e.toString());
			configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+806);
			configDone.setErrorString("Incrementing configuration ID:"+e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Store name of configuration used in status object.
	// This is queried when saving FITS headers to get the CONFNAME value.
		status.setConfigName(config.getId());
	// setup return object.
		configDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		configDone.setErrorString("");
		configDone.setSuccessful(true);
	// return done object.
		return configDone;
	}
}
//
// $Log$
//
