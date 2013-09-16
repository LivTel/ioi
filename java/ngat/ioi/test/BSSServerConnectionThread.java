// BSSServerConnectionThread.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;

import ngat.fits.*;
import ngat.net.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_BSS.*;
import ngat.util.logging.*;

/**
 * This class extends the TCPServerConnectionThread class for IO:I test programs. This
 * allows the test programs to emulate the ISS's response to the instrument sending it commands.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class BSSServerConnectionThread extends TCPServerConnectionThread
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Default time taken to respond to a command.
	 */
	private final static int DEFAULT_ACKNOWLEDGE_TIME = 60*1000;
	/**
	 * File name containing FITS defaults properties for the test programs.
	 */
	private final static String FITS_DEFAULTS_FILE_NAME = "./bss.fits.properties";
	/**
	 * The logger.
	 */
	private Logger logger = null;

	/**
	 * Constructor of the thread. This just calls the superclass constructors.
	 * @param connectionSocket The socket the thread is to communicate with.
	 * @see #logger
	 */
	public BSSServerConnectionThread(Socket connectionSocket)
	{
		super(connectionSocket);
		logger = LogManager.getLogger(this);
	}

	/**
	 * This method calculates the time it will take for the command to complete and is called
	 * from the classes inherited run method.
	 */
	protected ACK calculateAcknowledgeTime()
	{
		ACK acknowledge = null;
		int time;

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":calculateAcknowledgeTime:Calculating ACK time for command: "+command+".");
		acknowledge = new ACK(command.getId());
		if((command instanceof ngat.message.INST_BSS.GET_FITS)||
			(command instanceof ngat.message.INST_BSS.GET_FOCUS_OFFSET))
		{
			acknowledge.setTimeToComplete(DEFAULT_ACKNOWLEDGE_TIME);
		}
		else
			acknowledge.setTimeToComplete(DEFAULT_ACKNOWLEDGE_TIME);
		return acknowledge;
	}

	/**
	 * This method overrides the processCommand method in the ngat.net.TCPServerConnectionThread class.
	 * It is called from the inherited run method. It is responsible for performing the commands
	 * sent to it by the IO:I test programs. 
	 * It should also construct the done object to describe the results of the command.
	 */
	protected void processCommand()
	{
		if(command == null)
		{
			processError("processCommand:command was null.");
			done = new COMMAND_DONE(command.getId());
			done.setErrorNum(1);
			done.setErrorString("processCommand:command was null.");
			done.setSuccessful(false);
			return;
		}
		logger.log(Logging.VERBOSITY_INTERMEDIATE,"Command:"+command.getClass().getName()+
			   " received from the instrument.");
	// setup return object.
		if(command instanceof ngat.message.INST_BSS.GET_FITS)
		{
			ngat.message.INST_BSS.GET_FITS_DONE getFitsDone = 
				new ngat.message.INST_BSS.GET_FITS_DONE(command.getId());
			Vector fitsHeaderList = null;
			FitsHeaderDefaults getFitsDefaults = null;

			logger.log(Logging.VERBOSITY_INTERMEDIATE,command.getClass().getName()+" received.");
			try
			{
				getFitsDefaults = new FitsHeaderDefaults();
				//diddly BSS filename
				getFitsDefaults.load(FITS_DEFAULTS_FILE_NAME);
				fitsHeaderList = getFitsDefaults.getCardImageList();
				getFitsDone.setFitsHeader(fitsHeaderList);
				getFitsDone.setErrorNum(0);
				getFitsDone.setErrorString("");
				getFitsDone.setSuccessful(true);
			}
			catch(Exception e)
			{
				fitsHeaderList = new Vector();
				getFitsDone.setFitsHeader(fitsHeaderList);
				getFitsDone.setErrorNum(2);
				getFitsDone.setErrorString("GET_FITS:Getting FITS defaults failed:"+e);
				getFitsDone.setSuccessful(false);
			}
			done = getFitsDone;
		}
		if(command instanceof ngat.message.INST_BSS.GET_FOCUS_OFFSET)
		{
			ngat.message.INST_BSS.GET_FOCUS_OFFSET getFocusOffsetCommand = 
				(ngat.message.INST_BSS.GET_FOCUS_OFFSET)command;
			ngat.message.INST_BSS.GET_FOCUS_OFFSET_DONE getFocusOffsetDone = 
				new ngat.message.INST_BSS.GET_FOCUS_OFFSET_DONE(command.getId());

			getFocusOffsetDone.setFocusOffset(0.0f);
			getFocusOffsetDone.setErrorNum(0);
			getFocusOffsetDone.setErrorString("");
			getFocusOffsetDone.setSuccessful(true);
			done = getFocusOffsetDone;
		}
		logger.log(Logging.VERBOSITY_INTERMEDIATE,"Command:"+command.getClass().getName()+" Completed.");
	}

	/**
	 * This method is called when the thread generates an error.
	 * Currently, this just prints the string using the logger's error method.
	 * @param errorString The error string.
	 * @see ngat.util.logging.Logger#log
	 * @see #logger
	 */
	protected void processError(String errorString)
	{
		logger.log(Logging.VERBOSITY_TERSE,errorString);
	}

	/**
	 * This method is called when the thread generates an error due to an exception being thrown.
	 * This prints the string using the Logger's error method. It then prints the exception stack trace
	 * to the logger's error stream.
	 * @param errorString The error string.
	 * @param exception The exception that was thrown.
	 * @see ngat.util.logging.Logger#log
	 * @see #logger
	 */
	protected void processError(String errorString,Exception exception)
	{
		logger.log(Logging.VERBOSITY_TERSE,errorString+exception,exception);
	}
}
