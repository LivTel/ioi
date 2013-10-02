// GetConfigCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * Extension of the KeywordValueReplyCommand class to send a GetConfig command to the IDL Socket Server
 * and receive a hashtable of keyword/value's as a reply.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetConfigCommand extends KeywordValueReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Set the command string to "GetConfig".
	 * @see #commandString
	 */
	public GetConfigCommand()
	{
		super();
		commandString = new String("GETCONFIG");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetConfigCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 5000;

		if(args.length != 2)
		{
			System.out.println("java ngat.ioi.command.GetConfigCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new GetConfigCommand();
			// setup telnet connection
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.telnetConnection.open();
			// ensure reply broker is using same connection
			replyBroker = CommandReplyBroker.getInstance();
			replyBroker.setTelnetConnection(command.telnetConnection);
			// actually do command
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
			if(command.getReplyErrorCode() != 0)
			{
				System.out.println("Reply Error String:"+command.getReplyErrorString());
			}
			else
			{
				Enumeration keywords = command.getKeywords();
				while(keywords.hasMoreElements())
				{
					String keyword = (String)(keywords.nextElement());
					String value = command.getValue(keyword);
					System.out.println(keyword+" = "+value);
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
