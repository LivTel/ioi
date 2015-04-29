// IOILogFormatter.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.util.Date;

import ngat.util.logging.*;

/**
 * This is a custom log formatter for IO:I, that attempts to put the calling thread into the log message. This
 * should make it easier to trace the asynchronous behavior of IO:I.
 * @author Chris Mottram
 * @version $Revision: 50 $
 */
public class IOILogFormatter extends LogFormatter 
{
 	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	public IOILogFormatter()
	{
		super();
	}

	/**
	 * Format the log message.
	 * Gets the current thread and extracts it's name and id. Formats the log message
	 * as: "<date> : <id> : <threadName> : <record message>".
	 * @param record The log record to format.
	 */
	public String format(LogRecord record)
	{
		Thread t = null;
		String threadName = null;
		long id;

		t = Thread.currentThread();
		id = t.getId();
		threadName = t.getName();
		return df.format(new Date(record.getTime()))+" : "+id+" : "+threadName+" : "+record.getMessage();
	}

	/**
	 * Define start of logging message.
	 * @return A string, the start of logging message.
	 */
	public String getHead()
	{
		return "Logging START--->";
	}
    
	/**
	 * Define end of logging message.
	 * @return A string, the end of logging message.
	 */
	public String getTail()
	{
		return "<---END Logging.";
	}

	/** 
	 * Return the file name extension for the formatter.
	 * @return A string, "txt".
	 */
	public String getExtensionName() 
	{ 
		return "txt"; 
	}
}
