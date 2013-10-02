// KeywordValueReplyCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import ngat.util.logging.*;

/**
 * Extension of the StandardReplyCommand base class for sending a command and receiving a reply
 * to/from the IO:I IDL Socket Server. These commands expect a reply of a list of 'keyword=value' pairs,
 * or if the command failed a standard 'errCode:errString' may be returned.
 * @author Chris Mottram
 * @version $Revision$
 */
public class KeywordValueReplyCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * A hashtable containing  keyword hashs with values.
	 */
	protected Hashtable<String,String> keywordValueHashtable = null;

	/**
	 * Default constructor. Constructs the logger.
	 * @see #logger
	 */
	public KeywordValueReplyCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #errorCode
	 * @see #errorString
	 * @see #keywordValueHashtable
	 */
	public void parseReplyString() throws Exception
	{
		Pattern pattern = null;
		Matcher matcher = null;
		String errorCodeString;
		int sindex;

		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":parseReplyString:Started.");
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":parseReplyString:reply string:"+
			   replyString);
		keywordValueHashtable = new Hashtable<String,String>();
		sindex = replyString.indexOf(':');
		// if the reply contains a colon, we assume the reply is of the form 'errorCode:errorString'
		if(sindex > -1)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":parseReplyString:Parsing errorCode/errorString.");
			errorCodeString = replyString.substring(0,sindex);
			errorString = replyString.substring(sindex+1,replyString.length());
			try
			{
				errorCode = Integer.parseInt(errorCodeString);
			}
			catch(NumberFormatException e)
			{
				throw new Exception(this.getClass().getName()+
						    ":parseReplyString:Failed to parse reply '"+
						    replyString+"': Failed to parse error code string:'"+
						    errorCodeString+"'.");
			}
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":parseReplyString:errorCode:"+errorCode+", errorString:"+errorString);
		}
		// otherwise we are expecting 'keyword=value' pairs
		else
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":parseReplyString:Creating keyword/value hastable.");
			pattern = Pattern.compile("[a-zA-Z0-9_]*=[+-]?[a-z]?[0-9.]*");
			matcher = pattern.matcher(replyString);
			while(matcher.find())
			{
				String matchString = null;
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					   ":parseReplyString:Found keyword / value pair:"+matcher.group()+
					   " from reply string indexes "+matcher.start()+" to "+matcher.end());
				matchString = matcher.group();
				//logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				//	   ":parseReplyString:Found keyword / value pair:"+matchString);
				String keywordValueArray[] = matchString.split("=");
				if(keywordValueArray.length != 2 )
				{
					throw new Exception(this.getClass().getName()+
							    ":parseReplyString:Failed to parse keyword value:"+
							    matchString+":split returned wrong number of elements:"+
							    keywordValueArray.length);
				}
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					   ":parseReplyString:Found keyword:"+keywordValueArray[0]+", value:"+
					   keywordValueArray[1]);
				keywordValueHashtable.put(keywordValueArray[0],keywordValueArray[1]);
			}// end while
			if(keywordValueHashtable.size() < 1)
			{
				throw new Exception(this.getClass().getName()+
						    ":parseReplyString:Failed to parse any  keyword value pairs:"+
						    replyString);
			}
			// ensure the errorCode is set for success.
			errorCode = 0;
		}// end else
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":parseReplyString:Finished.");
	}

	/**
	 * Get the Hashtable of keyword value pairs.
	 * @return The Hashtable.
	 * @see #keywordValueHashtable
	 */
	public Hashtable getHashtable()
	{
		return keywordValueHashtable;
	}

	/**
	 * Get an enumeration of keywords returned by the command.
	 * @return An enumeration of keywords.
	 * @see #keywordValueHashtable
	 */
	public Enumeration getKeywords()
	{
		return keywordValueHashtable.keys();
	}

	/**
	 * Get the value associated with the specified keyword.
	 * @param keyword A string representing the keyword.
	 * @return The value associated with the particular keyword, as a string.
	 * @see #keywordValueHashtable
	 */
	public String getValue(String keyword)
	{
		return keywordValueHashtable.get(keyword);
	}

	/**
	 * Get the integer value associated with the specified keyword.
	 * @param keyword A string representing the keyword.
	 * @return The value associated with the particular keyword, as an integer.
	 * @see #keywordValueHashtable
	 */
	public int getValueInteger(String keyword)
	{
		String stringValue;

		stringValue = keywordValueHashtable.get(keyword);
		return Integer.parseInt(stringValue);
	}

	/**
	 * Get the double value associated with the specified keyword.
	 * @param keyword A string representing the keyword.
	 * @return The value associated with the particular keyword, as a double.
	 * @see #keywordValueHashtable
	 */
	public double getValueDouble(String keyword)
	{
		String stringValue;

		stringValue = keywordValueHashtable.get(keyword);
		return Double.parseDouble(stringValue);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		KeywordValueReplyCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 1234;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.KeywordValueReplyCommand <hostname> <port number> <command>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new KeywordValueReplyCommand();
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
