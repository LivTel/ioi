// HardwareImplementation.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_BSS.*;
import ngat.net.*;
import ngat.ioi.command.*;
import ngat.supircam.temperaturecontroller.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation of commands that use hardware to control a mechanism.
 * This is the Lakeshore Model 331 temperature controller.
 * This class provides some common hardware related routines to move folds, and FITS
 * interface routines needed by many command implementations
 * @version $Revision$
 */
public class HardwareImplementation extends CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The interface class to the temperature controller.
	 */
	protected TemperatureController tempControl = null;

	/**
	 * This method calls the super-classes method. It then tries to fill in the reference to the hardware
	 * objects.
	 * @param command The command to be implemented.
	 * @see #ioi
	 * @see #tempControl
	 * @see IOI#getTempControl
	 */
	public void init(COMMAND command)
	{
		super.init(command);
		if(ioi != null)
		{
			tempControl = ioi.getTempControl();
		}
	}

	/**
	 * This method is used to calculate how long an implementation of a command is going to take, so that the
	 * client has an idea of how long to wait before it can assume the server has died.
	 * @param command The command to be implemented.
	 * @return The time taken to implement this command, or the time taken before the next acknowledgement
	 * is to be sent.
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		return super.calculateAcknowledgeTime(command);
	}

	/**
	 * This routine performs the generic command implementation.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		return super.processCommand(command);
	}

	/**
	 * Return what sampling mode the array (IDL socket server layer) is currently configured to use.
	 * This is done by sending a GetConfig command to the IDL socket server and extracting the bFS config.
	 * @param idlTelnetConnection The IDL Socket Server Telnet Connection to send the GetConfig command over.
	 *        The Telnet Connection must have been initialised and opened.
	 * @return An integer. 1 means 'Fowler sampling mode' and 0 'Read Up The Ramp mode'.
	 * @exception Exception Thrown if the GetConfig fails, returns an error, or the bFS property is not present.
	 * @see #ioi
	 * @see ngat.ioi.command.GetConfigCommand
	 * @see ngat.ioi.command.GetConfigCommand#setTelnetConnection
	 * @see ngat.ioi.command.GetConfigCommand#sendCommand
	 * @see ngat.ioi.command.GetConfigCommand#getReplyErrorCode
	 * @see ngat.ioi.command.GetConfigCommand#getReplyErrorString
	 * @see ngat.ioi.command.GetConfigCommand#getValueInteger
	 */
	protected int getFSMode(TelnetConnection idlTelnetConnection) throws Exception
	{
		GetConfigCommand getConfigCommand = null;
		int bFS;

		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":getFSMode:started.");
		ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":getFSMode:Calling GetConfig.");
		getConfigCommand = new GetConfigCommand();
		getConfigCommand.setTelnetConnection(idlTelnetConnection);
		getConfigCommand.sendCommand();
		if(getConfigCommand.getReplyErrorCode() != 0)
		{
			ioi.error(this.getClass().getName()+":getFSMode:GetConfig failed:"+
				  getConfigCommand.getReplyErrorCode()+":"+
				  getConfigCommand.getReplyErrorString());
			throw new Exception(this.getClass().getName()+":getFSMode:GetConfig failed:"+
				  getConfigCommand.getReplyErrorCode()+":"+
				  getConfigCommand.getReplyErrorString());
		}
		// Are we using Fowler sampling or UpTheRamp?
		bFS = getConfigCommand.getValueInteger("bFS");
		if(bFS == 0)
		{
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":getFSMode:GetConfig returned bFS = "+bFS+": read up the ramp mode.");
		}
		else if (bFS == 1)
		{
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":getFSMode:GetConfig returned bFS = "+bFS+": fowler sampling mode.");
		}
		else
		{
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":getFSMode:GetConfig returned bFS = "+bFS+": UNKNOWN mode.");
			throw new Exception(this.getClass().getName()+":getFSMode:GetConfig returned illegal bFS = "+
					    bFS+".");
		}
		return bFS;
	}
}

//
// $Log$
//
