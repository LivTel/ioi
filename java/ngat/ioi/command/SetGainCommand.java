// SetGainCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetGaincommand to the
 * IO:I IDL Socket Server. This determines the preamp gain.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetGainCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 */
	public SetGainCommand()
	{
		super();
	}

	/**
	 * Method to set the command parameters to send to the server.
	 * @param gain The preamp gain, an integer [0..15]. 8 appears to be the default.
	 * @exception Exception Thrown if the gain is out of range.
	 * @see #commandString
	 */
	public void setCommand(int gain) throws Exception
	{
		// We could test gain is within the range 0..15 here, and throw an exception if not
		commandString = new String("SetGain("+gain+")");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetGainCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 5000;
		int gain;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.SetGainCommand <hostname> <port number> <gain>");
			System.out.println("\t<gain> is an integer from 0..15, 8 is the default.");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			gain = Integer.parseInt(args[2]);
			command = new SetGainCommand();
			// setup telnet connection
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.setCommand(gain);
			command.telnetConnection.open();
			// ensure reply broker is using same connection
			replyBroker = CommandReplyBroker.getInstance();
			replyBroker.setTelnetConnection(command.telnetConnection);
			command.run();
			command.telnetConnection.close();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Reply:"+command.getReplyString());
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Error Code:"+command.getReplyErrorCode());
			System.out.println("Reply Error String:"+command.getReplyErrorString());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
