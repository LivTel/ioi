// StandardReplyCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.util.logging.*;

/**
 * Extension of the Command base class for sending a command and receiving a reply
 * to/from the IO:I IDL Socket Server. The 'standard reply' of most of these commands is of the form:
 * 'errCode:errString' and this subclass assumes and parses replies of this type.
 * @author Chris Mottram
 * @version $Revision$
 */
public class StandardReplyCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The error code returned by the command.
	 */
	protected int errorCode = 1;
	/**
	 * The error string returned by the command.
	 */
	protected String errorString = null;

	/**
	 * Default constructor.
	 */
	public StandardReplyCommand()
	{
		super();
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #errorCode
	 * @see #errorString
	 */
	public void parseReplyString() throws Exception
	{
		String errorCodeString;
		int sindex;

		super.parseReplyString();
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":parseReplyString:Started.");
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":parseReplyString:reply string:"+
			   replyString);
		sindex = replyString.indexOf(':');
		if(sindex < 0)
		{
			throw new Exception(this.getClass().getName()+":parseReplyString:Failed to parse reply '"+
					    replyString+"': No colon found.");
		}
		errorCodeString = replyString.substring(0,sindex);
		errorString = replyString.substring(sindex+1,replyString.length());
		try
		{
			errorCode = Integer.parseInt(errorCodeString);
		}
		catch(NumberFormatException e)
		{
			throw new Exception(this.getClass().getName()+":parseReplyString:Failed to parse reply '"+
					    replyString+"': Failed to parse error code string:'"+
					    errorCodeString+"'.");
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":parseReplyString:reply error code:"+errorCode);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":parseReplyString:reply error string:"+errorString);
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":parseReplyString:Finished.");
	}

	/**
	 * Return the parsed reply error code.
	 * @return An integer, the error code.
	 * @see #errorCode
	 */
	public int getReplyErrorCode()
	{
		return errorCode;
	}

	/**
	 * Return the parsed reply error string.
	 * @return A string, the error string.
	 * @see #errorString
	 */
	public String getReplyErrorString()
	{
		return errorString;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		StandardReplyCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 1234;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.StandardReplyCommand <hostname> <port number> <command>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new StandardReplyCommand();
			// setup telnet connection
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.setCommand(args[2]);
			command.telnetConnection.open();
			// ensure reply broker is using same connection
			replyBroker = CommandReplyBroker.getInstance();
			replyBroker.setTelnetConnection(command.telnetConnection);
			command.run();
			command.telnetConnection.close();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command "+args[2]+" failed.");
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
