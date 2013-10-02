// CommandReplyBroker.java
// $HeadURL: svn://ltdevsrv/ioi/java/ngat/ioi/command/Command.java $
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The CommandReplyBroker class sits on the telnet connection to the IDL Socket Server
 * and reads reply string from it. Based on the reply, and Command sub-classes registered
 * against the broker, it will dispatch replies to the correct(!) command. This allows one JVM
 * to use one TelnetConnection for comms to the IDL Socket Server :- commands like STOPACQUISITION and PING
 * only work when they are sent on the same telnet conenction as the ACQUIRERAMP command, as the IDL
 * socket server will only process one conenction at a time.
 * @author Chris Mottram
 * @version $Revision: 4 $
 */
public class CommandReplyBroker implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Variable holding a mapping between known common reply strings, and the name of command classes that
	 * expect to receive the reply.
	 */
	protected Map<String,String> replyStringMap = null;
	/**
	 * Singleton instance (per-JVM) of the broker - using this ensures all commands in this JVM,
	 * using this TelnetConnection, use the same CommandReplyBroker.
	 */
	protected static CommandReplyBroker brokerInstance = null;
	/**
	 * Class variable set by the run method to indicate a run method (thread) has been started.
	 */
	protected static boolean isRunning = false;
	/**
	 * ngat.net.TelnetConnection instance.
	 */
	protected TelnetConnection telnetConnection = null;
	/**
	 * A list containing instances of Command subclasses awaiting a reply over the telnet connection.
	 */
	protected List<Command> commandList = null;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor.
	 * <ul>
	 * <li>The logger was initialised.
	 * <li>The commandList is initialised
	 * <li>The replyStringMap is initialised and some suitable mappings added.
	 * </ul>
	 * @see #logger
	 * @see #commandList
	 * @see #replyStringMap
	 */
	protected CommandReplyBroker()
	{
		super();
		logger = LogManager.getLogger(this);
		commandList = new Vector<ngat.ioi.command.Command>();
		// initialise the replyStringMap
		replyStringMap = new Hashtable<String,String>();
		replyStringMap.put("0:Ramp acquisition succeeded","ngat.ioi.command.AcquireRampCommand");
		replyStringMap.put("0:The system is idle","ngat.ioi.command.PingCommand");
		replyStringMap.put("-1:Exposure is in progress","ngat.ioi.command.PingCommand");
	}

	/**
	 * Static class method to return the CommandReplyBroker instance in use for this JVM/TelnetConnection.
	 * @see #brokerInstance
	 */
	public static CommandReplyBroker getInstance()
	{
		Thread t = null;

		//logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":getInstance:Started.");
		if(brokerInstance == null)
		{
			brokerInstance = new CommandReplyBroker();
			brokerInstance.logger.log(Logging.VERBOSITY_VERBOSE,
					    "ngat.ioi.command.CommandReplyBroker:getInstance:Created new instance.");
		}
		if(!isRunning)
		{
			t = new Thread(brokerInstance);
			t.start();
			brokerInstance.logger.log(Logging.VERBOSITY_VERBOSE,
			      "ngat.ioi.command.CommandReplyBroker:getInstance:Started CommandReplyBroker thread.");
		}
		return brokerInstance;
	}

	/**
	 * Set the telnet connection used for communications to the IDL Socket Server.
	 * @param tc The telnet connection.
	 * @see #telnetConnection
	 */
	public void setTelnetConnection(TelnetConnection tc)
	{
		telnetConnection = tc;
	}

	//public void addCommandReplyListener(Command c)
	//{
	//	logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":addCommandReplyListener:"+c);
	//	commandList.add(c);
	//}

	/**
	 * Method to send a command string over the telnet conenction to the IDL Socket Server. The command is added
	 * to the command list, so that when a reply is received it can be dispatched to the appropriate command.
	 * @param commandString The string to send to the IDL Socket Server.
	 * @param command The IDL Socket Server command object that generated the command string. This is added to the command list,
	 *        so that when a reply is received by the command reply broker it can direct it to the appropriate command.
	 * @see #commandList
	 * @see #telnetConnection
	 */
	public void sendCommand(String commandString,Command command)
	{
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":sendCommand:Started:"+command.getClass().getName()+":"+commandString);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":sendCommand:Waiting for synchronisation on instance.");
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":sendCommand:Adding command to list.");
		commandList.add(command);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":sendCommand:Sending command over telnet connection.");
		telnetConnection.sendLine(commandString);
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":sendCommand:Finished:"+command.getClass().getName()+":"+commandString);
	}

	/**
	 * Run method.
	 * <ul>
	 * <li>If the telnet connection is not set yet we stop.
	 * <li>We enter a loop:
	 *     <ul>
	 *     <li>We read a reply string from the telnet connection.
	 *     <li>
	 *     <li>
	 *     <li>
	 *     <li>
	 *     <li>
	 *     <li>
	 *     </ul>
	 * </ul>
	 * @see #telnetConnection
	 * @see #isRunning
	 * @see #sendReplyToCommand
	 * @see #commandList
	 */
	public void run()
	{
		Command command = null;
		String replyString;
		String classNameString = null;
		boolean done,processedReply;

		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Started.");
		isRunning = true;
		if(telnetConnection == null)
		{
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":run:telnetConnection was null:terminating.");
			isRunning = false;
			return;
		}
		done = false;
		while(done == false)
		{
			try
			{
				// get a reply
				replyString = telnetConnection.readLine();
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":run:Received Reply String:"+replyString);
				// telnetConnection.readLine calls BufferedReader.readLine
				// This can return null if the end of the stream has been reached 
				// This shouldn't happen if the connection is open, but has in the past
				// causing this loop to go into an infinite loop
				if(replyString == null)
				{
					logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
						   ":run:Received null Reply String:Stopping CommandReplyBroker.");
					done = true;
				}
				// initialise flag specifying whether we have processed the reply.
				processedReply = false;
				// Is this a common reply in the map, if so to which class of command does it belong
				classNameString = replyStringMap.get(replyString);
				if(classNameString != null)
				{
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						   ":run:Reply String '"+replyString+
						   "' usually sent to instances of class "+classNameString+".");
					// lets look for an instance of this class waiting for a reply.
					int index = 0; 
					while((processedReply == false) &&(index < commandList.size()))
					{
						command = commandList.get(index);
						// Does this command have the right class?
						if(command.getClass().getName().equals(classNameString))
						{
							logger.log(Logging.VERBOSITY_VERY_VERBOSE,
								   this.getClass().getName()+
								   ":run:Found instance of class "+classNameString+
								   " to send reply to.");
							sendReplyToCommand(replyString,command,index);
							processedReply = true;
						}// end if
						index++;
					}// end while
				}// end if replyStringMap contains an entry for this reply
				// check whether the reply is the start of the GetConfig reply.
				if((processedReply == false) && replyString.startsWith("nResets="))
				{
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,
						   this.getClass().getName()+
						   ":run:Reply string belongs to a GetConfig command:"+
						   "looking for a suitable instance.");
					// find a GetConfig command and return it to that
					int index = 0; 
					while((processedReply == false) &&(index < commandList.size()))
					{
						command = commandList.get(index);
						// Does this command have the right class?
						if(command.getClass().getName().
						   equals("ngat.ioi.command.GetConfigCommand"))
						{
							logger.log(Logging.VERBOSITY_VERY_VERBOSE,
								   this.getClass().getName()+
								   ":run:Found a GetConfig command.");
							sendReplyToCommand(replyString,command,index);
							processedReply = true;
						}// end if
						index++;
					}// end while
				}
				// if there is an interrupt command awaiting a reply, give the reply to that
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,
					   this.getClass().getName()+
					   ":run:Looking for an interrupt command instance for reply string:"+
					   replyString);
				int index = 0; 
				while((processedReply == false) &&(index < commandList.size()))
				{
					command = commandList.get(index);
					if(command.isInterruptCommand())
					{
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   this.getClass().getName()+
							  ":run:Found an interrupt command instance for reply string:"+
							   replyString);
						sendReplyToCommand(replyString,command,index);
						processedReply = true;
					}// end if interrupt command
					index++;
				}
				// if no interrupt command has been found, give the reply to
				// the first index in the list, if it exists
				if(processedReply == false)
				{
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,
						   this.getClass().getName()+
						   ":run:Looking for any command instance for reply string:"+
						   replyString);
					if(commandList.size() > 0)
					{
						command = commandList.get(0);
						sendReplyToCommand(replyString,command,0);
						processedReply = true;
					}// end if
				}
				// if there are no commands to process the reply, log the problem
				if(processedReply == false)
				{
					logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
						   ":run:No commands  sent Reply String:"+replyString+
						   " :command list has "+commandList.size()+" entries.");
				}
			}
			catch(IOException e)
			{
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":run:Reading line failed:",e);
				// telnetConnection has failed - quit loop
				done = true;
				// notify all waiting commands by returning null reply strings
				while(commandList.size() > 0)
				{
					command = commandList.get(0);
					sendReplyToCommand(null,command,0);
				}// end while commandList has commands 
			}// end catch exception
		}// end while not done
		isRunning = false;
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Finished.");
	}

	/**
	 * Send the specified reply string back to the command (and notify() it to wake it up), and
	 * remove it from the commandList (from the specified index).
	 * @param replyString The reply string to send.
	 * @param command The command to send the reply string to.
	 * @param index The index in the command list where the command resides, the command
	 *        should be removed from the command list.
	 * @see #logger
	 * @see #commandList
	 */
	public void sendReplyToCommand(String replyString,Command command,int index)
	{
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":sendReplyToCommand:Command:Sending Reply String:"+replyString+" to command:"+
			   command.getClass().getName()+" at index "+index);
		command.setReplyString(replyString);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":sendReplyToCommand:Waiting for synchronisation on command:"+command);
		synchronized(command)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":sendReplyToCommand:Notifying command:"+command);
			command.notify();
		}
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":sendReplyToCommand:Command:Removing:"+command.getClass().getName()+" at index "+index+
			   " from command list.");
		commandList.remove(index);
	}
}
