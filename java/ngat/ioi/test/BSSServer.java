// BSSServer.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;

import ngat.net.*;
import ngat.util.logging.*;

/**
 * This class extends the TCPServer class for the IO:I test programs. The IO:I test programs sends
 * commands to an instrument. Some instrument commands involve sending commands back to the BSS, and
 * this class is designed to catch these requests and to spawn a BSSServerConnectionThread to deal with them.
 * @author Chris Mottram
 * @version $Revision$
 */
public class BSSServer extends TCPServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Logger.
	 */
	private Logger logger = null;

	/**
	 * The constructor. Call the inherited constrctor.
	 */
	public BSSServer(String name,int portNumber)
	{
		super(name,portNumber);
	}

	/**
	 * This routine spawns threads to handle connection to the server. This routine
	 * spawns <a href="BSSServerConnectionThread.html">BSSServerConnectionThread</a> thread.
	 * @see BSSServerConnectionThread
	 */
	public void startConnectionThread(Socket connectionSocket)
	{
		BSSServerConnectionThread thread = null;

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":Starting BSS client connection thread.");
		thread = new BSSServerConnectionThread(connectionSocket);
		thread.start();
	}

	/**
	 * Overwritten processError method, that receives error messages when an error occurs.
	 * The error is dealt with using the parent's error method.
	 * @param errorString The error string generated.
	 * @see ngat.util.logging.Logger#log
	 * @see #logger
	 */
	protected void processError(String errorString)
	{
		logger.log(Logging.VERBOSITY_TERSE,errorString);
	}

	/**
	 * This method is called when the thread generates an error due to an exception being thrown.
	 * This prints the string using the logger's error method. 
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
