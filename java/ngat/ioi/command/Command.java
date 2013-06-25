// Command.java
// $Header$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The Command class is the base class for sending a command and getting a reply from the
 * IO:I IDL server socket. This is a telnet - type socket interaction.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * ngat.net.TelnetConnection instance.
	 */
	protected TelnetConnection telnetConnection = null;
	/**
	 * The command to send to the tiptilt.
	 */
	protected String commandString = null;
	/**
	 * Exception generated by errors generated in sendCommand, if called via the run method.
	 * @see #sendCommand
	 * @see #run
	 */
	protected Exception runException = null;
	/**
	 * Boolean set to true, when a command has been sent to the server and
	 * a reply string has been sent.
	 * @see #sendCommand
	 */
	protected boolean commandFinished = false;
	/**
	 * A string containing the reply from the server socket.
	 */
	protected String replyString = null;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor. Construct the TelnetConnection and set this object to be the listener.
	 * Constructs the logger.
	 * @see #logger
	 * @see #telnetConnection
	 */
	public Command()
	{
		super();
		telnetConnection = new TelnetConnection();
		//telnetConnection.setListener(this);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor. Construct the TelnetConnection and set this object to be the listener.
	 * Setup the commandString.
	 * @param address A string representing the address of the server socket, i.e. "ioi",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server socket is receiving command on.
	 * @param commandString The string to send to the C layer as a command.
	 * @see #telnetConnection
	 * @see #commandString
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public Command(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super();
		telnetConnection = new TelnetConnection(address,portNumber);
		//telnetConnection.setListener(this);
		this.commandString = commandString;
	}

	/**
	 * Set the telnet connection used for communications to an externally created one.
	 * @param tc The telnet connection.
	 * @see #telnetConnection
	 */
	public void setTelnetConnection(TelnetConnection tc)
	{
		telnetConnection = tc;
	}

	/**
	 * Set the address.
	 * @param address A string representing the address of the server, i.e. "ioi",
	 *     "localhost", "192.168.1.62"
	 * @exception UnknownHostException Thrown if the address in unknown.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setAddress
	 */
	public void setAddress(String address) throws UnknownHostException
	{
		telnetConnection.setAddress(address);
	}

	/**
	 * Set the address.
	 * @param address A instance of InetAddress representing the address of the server.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setAddress
	 */
	public void setAddress(InetAddress address)
	{
		telnetConnection.setAddress(address);
	}

	/**
	 * Set the port number.
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setPortNumber
	 */
	public void setPortNumber(int portNumber)
	{
		telnetConnection.setPortNumber(portNumber);
	}

	/**
	 * Set the command.
	 * @param command The string to send to the server as a command.
	 * @see #commandString
	 */
	public void setCommand(String command)
	{
		commandString = command;
	}

	/**
	 * Run thread. Uses sendCommand to send the specified command over a telnet connection to the specified
	 * address and port number.
	 * Catches any errors and puts them into runException. commandFinished indicates when the command
	 * has finished processing, replyString contains the server replies.
	 * @see #commandString
	 * @see #sendCommand
	 * @see #replyString
	 * @see #runException
	 * @see #commandFinished
	 */
	public void run()
	{
		try
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":run:Calling sendCommand.");
			sendCommand();
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":run:sendCommand finished.");
		}
		catch(Exception e)
		{
			runException = e;
			commandFinished = true;
		}
	}

	/**
	 * Open a connection to the IDL Server socket.
	 * @see #telnetConnection
	 * @exception IOException Thrown if an IO error occurs when creating the socket.
	 * @exception NullPointerException Thrown if the address is null.
	 */
	public void open()  throws IOException,NullPointerException
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":open:Opening telnet connection.");
		telnetConnection.open();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":open:telnet connection opened.");
	}

	/**
	 * Routine to send the specified command over a telnet connection to the specified
	 * address and port number, wait for a reply from the server, and try to parse the reply.
	 * @exception Exception Thrown if an error occurs.
	 * @see #telnetConnection
	 * @see #commandString
	 * @see #commandFinished
	 * @see #parseReplyString
	 */
	public void sendCommand() throws Exception
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":sendCommand:Started.");
		commandFinished = false;
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":sendCommand:Sending Command:"+commandString);
		telnetConnection.sendLine(commandString);
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":sendCommand:Awaiting reply.");
		replyString = telnetConnection.readLine();
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":sendCommand:Received Reply:"+replyString);
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":sendCommand:Parsing reply.");
		parseReplyString();
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":sendCommand:Reply parsed.");
		commandFinished = true;
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":sendCommand:Finished.");
	}

	/**
	 * Close a connection to the IDL Server socket.
	 * @see #telnetConnection
	 * @exception IOException Thrown if the output stream flush or close fails.
	 * @exception NullPointerException Thrown if outputWriter or inputReader is null.
	 */
	public void close() throws IOException,NullPointerException
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":close:Closing telnet connection.");
		telnetConnection.close();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":open:telnet connection closed.");
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #replyString
	 */
	public void parseReplyString() throws Exception
	{
		if(replyString == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Reply string to command '"+commandString+"'was null.");
		}
	}

	/**
	 * Return the reply string
	 * @return The FULL string returned from the server.
	 * @see #replyString
	 */
	public String getReply()
	{
		return replyString;
	}

	/**
	 * Get any exception resulating from running the command.
	 * This is only filled in if the command was sent using the run method, rather than the sendCommand method.
	 * @return An exception if the command failed in some way, or null if no error occured.
	 * @see #run
	 * @see #sendCommand
	 * @see #runException
	 */
	public Exception getRunException()
	{
		return runException;
	}

	/**
	 * Get whether the command has been completed.
	 * @return A Boolean, true if a command has been sent, and a reply received and parsed. false if the
	 *     command has not been sent yet, or we are still waiting for a reply.
	 * @see #commandFinished
	 */
	public boolean getCommandFinished()
	{
		return commandFinished;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		Command command = null;
		int portNumber = 1234;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.Command <hostname> <port number> <command>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			command = new Command(args[0],portNumber,args[2]);
			command.open();
			command.run();
			command.close();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command "+args[2]+" failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Reply:"+command.getReply());
			System.out.println("Finished:"+command.getCommandFinished());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}

		System.exit(0);

	}
}
//
// $Log$
//