// ABORTImplementation.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import ngat.ioi.command.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.ABORT_DONE;

/**
 * This class provides the implementation for the ABORT command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class ABORTImplementation extends INTERRUPTImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public ABORTImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.ABORT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.ABORT";
	}

	/**
	 * This method gets the ABORT command's acknowledge time. This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see OTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the ABORT command. 
	 * <ul>
	 * <li>It tells the currently executing thread to abort itself.
	 * </ul>
	 * An object of class ABORT_DONE is returned.
	 * @see IOIStatus#getCurrentThread
	 * @see IOITCPServerConnectionThread#setAbortProcessCommand
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		ngat.message.INST_DP.ABORT dprtAbort = new ngat.message.INST_DP.ABORT(command.getId());
		ABORT_DONE abortDone = new ABORT_DONE(command.getId());
		TelnetConnection idlTelnetConnection = null;
		IOITCPServerConnectionThread thread = null;
		IOIStatus status = null;
		StopAcquisitionCommand stopAcquisitionCommand = null;

		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
	// tell the thread itself to abort at a suitable point
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Tell thread to abort.");
		status = ioi.getStatus();
		thread = (OTCPServerConnectionThread)status.getCurrentThread();
		if(thread != null)
			thread.setAbortProcessCommand();
		// are we currently exposing? If so stop the acquisition
		// Use Ping to see if an exposure is in progress
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":processCommand:Use ping to get exposure status.");
		idlTelnetConnection = ioi.getIDLTelnetConnection();
		pingCommand = new PingCommand();
		pingCommand.setTelnetConnection(idlTelnetConnection);
		pingCommand.sendCommand();
		if(pingCommand.getReplyErrorCode() == -1) // an exposure is in progress
		{
			logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				   ":processCommand:Exposure is in progress:Send StopAcquisition command.");
			stopAcquisitionCommand = new StopAcquisitionCommand();
			stopAcquisitionCommand.setTelnetConnection(idlTelnetConnection);
			stopAcquisitionCommand.sendCommand();
			if(stopAcquisitionCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+":processCommand:StopAcquisition failed:"+
					  stopAcquisitionCommand.getReplyErrorCode()+":"+
					  stopAcquisitionCommand.getReplyErrorString());
				abortDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+2400);
				abortDone.setErrorString("processCommand:StopAcquisition failed:"+
							 stopAcquisitionCommand.getReplyErrorCode()+":"+
							 stopAcquisitionCommand.getReplyErrorString());
				abortDone.setSuccessful(false);
				return abortDone;
			}
		}
		else
		{
			logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				   ":processCommand:Exposure NOT in progress:NOT Sending StopAcquisition command.");
		}
	// abort the dprt
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Tell DpRt to abort.");
		ioi.sendDpRtCommand(dprtAbort,serverConnectionThread);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Finished.");
	// return done object.
		abortDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		abortDone.setErrorString("");
		abortDone.setSuccessful(true);
		return abortDone;
	}
}

//
// $Log$
//
