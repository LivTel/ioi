// AcquireRampCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the AcquireRamp command to the
 * IO:I IDL Socket Server. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class AcquireRampCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Set the command string to "ACQUIRERAMP".
	 * @see #commandString
	 */
	public AcquireRampCommand()
	{
		super();
		commandString = new String("ACQUIRERAMP");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		AcquireRampCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 5000;
		int level;

		if(args.length != 2)
		{
			System.out.println("java ngat.ioi.command.AcquireRampCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			initialiseLogging();
			portNumber = Integer.parseInt(args[1]);
			command = new AcquireRampCommand();
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.telnetConnection.open();
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
