// EXPOSEImplementation.java
// $HeadURL$
package ngat.ioi;

import java.io.IOException;
import java.util.List;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation for EXPOSE commands sent to a server using the
 * Java Message System. It extends FITSImplementation, as EXPOSE commands needs access to
 * resources to make FITS files.
 * @see FITSImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class EXPOSEImplementation extends FITSImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * This method gets the EXPOSE command's acknowledge time. It returns the server connection 
	 * threads min acknowledge time. This method should be over-written in sub-classes.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getMinAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getMinAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method is a generic implementation for the EXPOSE command, that does nothing.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
	       	// do nothing 
		EXPOSE_DONE exposeDone = new EXPOSE_DONE(command.getId());

		exposeDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		exposeDone.setErrorString("");
		exposeDone.setSuccessful(true);
		return exposeDone;
	}

	/**
	 * This routine calls the Real Time Data Pipeline to process the expose FITS image we have just captured.
	 * If an error occurs the done objects field's are set accordingly. If the operation succeeds, and the
	 * done object is of class EXPOSE_DONE, the done object is filled with data returned from the 
	 * reduction command.
	 * @param command The command being implemented that made this call to the DP(RT). This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see IOI#sendDpRtCommand
	 */
	public boolean reduceExpose(COMMAND command,COMMAND_DONE done,String filename)
	{
		EXPOSE_REDUCE reduce = new EXPOSE_REDUCE(command.getId());
		INST_TO_DP_DONE instToDPDone = null;
		EXPOSE_REDUCE_DONE reduceDone = null;
		EXPOSE_DONE exposeDone = null;

		reduce.setFilename(filename);
		reduce.setWcsFit(false);
		instToDPDone = ioi.sendDpRtCommand(reduce,serverConnectionThread);
		if(instToDPDone.getSuccessful() == false)
		{
			ioi.error(this.getClass().getName()+":reduce:"+
				command+":"+instToDPDone.getErrorNum()+":"+instToDPDone.getErrorString());
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+600);
			done.setErrorString(instToDPDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
		// Copy the DP REDUCE DONE parameters to the EXPOSE DONE parameters
		if(instToDPDone instanceof EXPOSE_REDUCE_DONE)
		{
			reduceDone = (EXPOSE_REDUCE_DONE)instToDPDone;
			if(done instanceof EXPOSE_DONE)
			{
				exposeDone = (EXPOSE_DONE)done;
				exposeDone.setFilename(reduceDone.getFilename());
				exposeDone.setSeeing(reduceDone.getSeeing());
				exposeDone.setCounts(reduceDone.getCounts());
				exposeDone.setXpix(reduceDone.getXpix());
				exposeDone.setYpix(reduceDone.getYpix());
				exposeDone.setPhotometricity(reduceDone.getPhotometricity());
				exposeDone.setSkyBrightness(reduceDone.getSkyBrightness());
				exposeDone.setSaturation(reduceDone.getSaturation());
			}
		}
		return true;
	}

	/**
	 * Method to send an ACK to the to ensure the client connection is kept open.
	 * @param command The command we are implementing.
	 * @param done The COMMAND_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @param timeToComplete The length of time before the command is due to finish,
	 *      or before the next ACK is to be sent, in milliseconds. The client should hold open the
	 *      socket connection for the command for at least this length of time before giving up.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see #serverConnectionThread
	 * @see ngat.ioi.IOI#log
	 * @see ngat.ioi.IOI#error
	 * @see ngat.message.base.ACK
	 */
	protected boolean sendACK(COMMAND command,COMMAND_DONE done,int timeToComplete)
	{
		ACK ack = null;

		// send acknowledge to say frames are completed.
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":sendACK:Sending ACK with timeToComplete "+timeToComplete+" and default ACK time "+
			(long)(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime()));
		ack = new ACK(command.getId());
		ack.setTimeToComplete(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime());
		try
		{
			serverConnectionThread.sendAcknowledge(ack);
		}
		catch(IOException e)
		{
			ioi.error(this.getClass().getName()+":sendACK:sendAcknowledge failed:",e);
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+601);
			done.setErrorString("sendACK:sendAcknowledge failed:"+e.toString());
			done.setSuccessful(false);
			return false;
		}
		return true;
	}
}

