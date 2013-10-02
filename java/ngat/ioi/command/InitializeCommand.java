// InitializeCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the Initialize command to the
 * IO:I IDL Socket Server. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class InitializeCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 */
	public InitializeCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param level The level of initialisation required, an integer from 1 to 3 inclusive.
	 * @exception Exception Thrown if the level is out of range.
	 */
	public void setCommand(int level) throws Exception
	{
		if((level < 1)||(level > 3))
		{
			throw new Exception(this.getClass().getName()+":setCommand:Illegal level value:"+level);
		}
		commandString = new String("INITIALIZE"+level);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		InitializeCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 5000;
		int level;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.InitializeCommand <hostname> <port number> <level(1..3)>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			level = Integer.parseInt(args[2]);
			command = new InitializeCommand();
			// setup telnet connection
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.setCommand(level);
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
