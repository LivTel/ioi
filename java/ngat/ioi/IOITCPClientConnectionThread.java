// IOITCPClientConnectionThread.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.Date;

import ngat.net.*;
import ngat.message.base.*;

/**
 * The IOITCPClientConnectionThread extends TCPClientConnectionThread. 
 * It implements the generic ISS/DP(RT) instrument command protocol with multiple acknowledgements. 
 * The instrument starts one of these threads each time
 * it wishes to send a message to the ISS/DP(RT).
 * @author Chris Mottram
 * @version $Revision$
 */
public class IOITCPClientConnectionThread extends TCPClientConnectionThreadMA
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The commandThread was spawned by the IO:I to deal with a IO:I command request. 
	 * As part of the running of
	 * the commandThread, this client connection thread was created. We need to know the server thread so
	 * that we can pass back any acknowledge times from the ISS/DpRt back to the IO:I client (ISS/IcsGUI etc).
	 */
	private IOITCPServerConnectionThread commandThread = null;
	/**
	 * The IOI object.
	 */
	private IOI ioi = null;

	/**
	 * A constructor for this class. Currently just calls the parent class's constructor.
	 * @param address The internet address to send this command to.
	 * @param portNumber The port number to send this command to.
	 * @param c The command to send to the specified address.
	 * @param ct The IO:I command thread, the implementation of which spawned this command.
	 */
	public IOITCPClientConnectionThread(InetAddress address,int portNumber,COMMAND c,
					    IOITCPServerConnectionThread ct)
	{
		super(address,portNumber,c);
		commandThread = ct;
		setName(c.getClass().getName());
	}

	/**
	 * Routine to set this objects pointer to the ioi object.
	 * @param o The ioi object.
	 */
	public void setIOI(IOI o)
	{
		this.ioi = o;
	}

	/**
	 * This routine processes the acknowledge object returned by the server. It
	 * prints out a message, giving the time to completion if the acknowledge was not null.
	 * It sends the acknowledgement to the IO:I client for this sub-command of the command,
	 * so that the IO:I's client does not time out if,say, a zero is returned.
	 * @see IOITCPServerConnectionThread#sendAcknowledge
	 * @see #commandThread
	 */
	protected void processAcknowledge()
	{
		if(acknowledge == null)
		{
			ioi.error(this.getClass().getName()+":processAcknowledge:"+
				  command.getClass().getName()+":acknowledge was null.");
			return;
		}
	// send acknowledge to IO:I client.
		try
		{
			commandThread.sendAcknowledge(acknowledge);
		}
		catch(IOException e)
		{
			ioi.error(this.getClass().getName()+":processAcknowledge:"+
				  command.getClass().getName()+":sending acknowledge to client failed:",e);
		}
	}

	/**
	 * This routine processes the done object returned by the server. 
	 * It prints out the basic return values in done.
	 */
	protected void processDone()
	{
		ACK acknowledge = null;

		if(done == null)
		{
			ioi.error(this.getClass().getName()+":processDone:"+
				  command.getClass().getName()+":done was null.");
			return;
		}
	// construct an acknowledgement to sent to the IO:I client to tell it how long to keep waiting
	// it currently returns the time the IO:I origianally asked for to complete this command
	// This is because the IO:I assumed zero time for all sub-commands.
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(commandThread.getAcknowledgeTime());
		try
		{
			commandThread.sendAcknowledge(acknowledge);
		}
		catch(IOException e)
		{
			ioi.error(this.getClass().getName()+":processDone:"+
				  command.getClass().getName()+":sending acknowledge to client failed:",e);
		}
	}
}
