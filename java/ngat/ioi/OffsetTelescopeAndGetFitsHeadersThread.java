// OffsetTelescopeAndGetFitsHeadersThread.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * This thread is started by a MULTRUN. It enables a telescope offset to sent to the RCS whilst
 * an AcquireRamp is udnerway, and the FITS headers collected sequentially after the offset has been completed
 * from the RCS, so that they contain the latest offset RA/Dec.
 * @see HardwareImplementation
 * @author Chris Mottram
 * @version $Revision: 50 $
 */
public class OffsetTelescopeAndGetFitsHeadersThread extends Thread
{
 	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_UNKNOWN                   = 0;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_STARTED                   = 1;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_OFFSETING_TELESCOPE       = 2;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_FAILED_OFFSET_TELESCOPE   = 3;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_GET_FITS                  = 4;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_FAILED_GET_FITS           = 5;
	/**
	 * What the running thread is doing.
	 */
	public final static int THREAD_STATE_FINISHED                   = 6;
	/**
	 * Internal constant used when the order number offset defined in the property
	 * 'ioi.get_fits.order_number_offset' is not found or is not a valid number.
	 * @see #run
	 */
	private final static int DEFAULT_ORDER_NUMBER_OFFSET = 255;
	/**
	 * A reference to the IOI class instance.
	 */
	protected IOI ioi = null;
	/**
	 * A reference to the IOIStatus class instance that holds status information for IO:I.
	 */
	protected IOIStatus status = null;
	/**
	 * A local reference to the FitsHeader object held in IO:I. This is used for writing FITS headers to disk
	 * and setting the values of card images within the headers.
	 */
	protected FitsHeader ioiFitsHeader = null;
	/**
	 * What the running thread is doing.
	 */
	protected int threadState = THREAD_STATE_UNKNOWN;
	/**
	 * Internal error number used when an error occurs during the procedure.
	 */
	protected int errorNum = 0;
	/**
	 * Internal error string describing the error that has occured.
	 */
	protected String errorString = null;
	/**
	 * The server connection thread used by the command that has initiated this thread.
	 */
	protected IOITCPServerConnectionThread serverConnectionThread = null;
	/**
	 * The index into the offset list of the OFFSET_RA_DEC to perform. Used to get the right dither.
	 */
	protected int offsetIndex = -1;

	/**
	 * Default constructor.
	 * @see #threadState
	 */
	public OffsetTelescopeAndGetFitsHeadersThread()
	{
		super("OffsetTelescopeAndGetFitsHeadersThread");
		threadState = THREAD_STATE_UNKNOWN;
	}
	
	/**
	 * Routine to set this objects pointer to the IOI object.
	 * @param o The IOI object.
	 */
	public void setIOI(IOI o)
	{
		this.ioi = o;
	}

	/**
	 * Initialise the thread's internal data. Should be called after the ioi
	 * pointer is set, before the thread is started.
	 * @see #ioi
	 * @see #status
	 * @see #ioiFitsHeader
	 */
	public void init()
	{
		status = ioi.getStatus();
		ioiFitsHeader = ioi.getFitsHeader();
	}

	/**
	 * Routine to set the server connection thread used by the command implementation that
	 * is invoking this thread.
	 * @param o The server connection thread object.
	 * @see #serverConnectionThread
	 */
	public void setServerConnectionThread(IOITCPServerConnectionThread o)
	{
		serverConnectionThread = o;
	}

	/**
	 * Set which index offset to perform in the offset list.
	 * @param i The index to use.
	 * @see #offsetIndex
	 */
	public void setOffsetIndex(int i)
	{
		offsetIndex = i;
	}

	/**
	 * Get the current thread state.
	 * @return The current thread state.
	 * @see #threadState
	 * @see #THREAD_STATE_STARTED
	 * @see #THREAD_STATE_OFFSETING_TELESCOPE
	 * @see #THREAD_STATE_FAILED_OFFSET_TELESCOPE
	 * @see #THREAD_STATE_GET_FITS
	 * @see #THREAD_STATE_FAILED_GET_FITS
	 * @see #THREAD_STATE_FINISHED
	 */
	public int getThreadState()
	{
		return threadState;
	}

	/**
	 * Get a string decribing the specified thread state.
	 * @param state The state to return a description string for: 
	 * @return A string describing the thread state. One of:
	 *        "STARTED","OFFSETING_TELESCOPE","FAILED_OFFSET_TELESCOPE","GET_FITS","FAILED_GET_FITS",
	 *        "FINISHED","UNKNOWN".
	 * @see #THREAD_STATE_STARTED
	 * @see #THREAD_STATE_OFFSETING_TELESCOPE
	 * @see #THREAD_STATE_FAILED_OFFSET_TELESCOPE
	 * @see #THREAD_STATE_GET_FITS
	 * @see #THREAD_STATE_FAILED_GET_FITS
	 * @see #THREAD_STATE_FINISHED
	 */
	public static String threadStateToString(int state)
	{
		switch(state)
		{
			case THREAD_STATE_STARTED:
				return "STARTED";
			case THREAD_STATE_OFFSETING_TELESCOPE:
				return "OFFSETING_TELESCOPE";
			case THREAD_STATE_FAILED_OFFSET_TELESCOPE:
				return "FAILED_OFFSET_TELESCOPE";
			case THREAD_STATE_GET_FITS:
				return "GET_FITS";
			case THREAD_STATE_FAILED_GET_FITS:
				return "FAILED_GET_FITS";
			case THREAD_STATE_FINISHED:
				return "FINISHED";
			case THREAD_STATE_UNKNOWN:
			default:
				return "UNKNOWN";
		}
	}

	/**
	 * Get an error number generated during the running of the thread.
	 * @return An integer error number. This will be zero if no error occurred.
	 * @see #errorNum
	 */
	public int getErrorNum()
	{
		return errorNum;
	}

	/**
	 * Get an error string generated during the running of the thread.
	 * @return An string describing the error that occured.. This will be null if no error occurred.
	 * @see #errorString
	 */
	public String getErrorString()
	{
		return errorString;
	}

	/**
	 * Main thread run method.
	 * <ul>
	 * <li>We set the thread state to STARTED.
	 * <li>We initialise some data.
	 * <li>We set the thread state to THREAD_STATE_OFFSETING_TELESCOPE
	 * <li>We call offsetTelescope. If this fails an error is logged and the threadState set to 
	 *     THREAD_STATE_FAILED_OFFSET_TELESCOPE
	 * <li>e set the thread state to THREAD_STATE_GET_FITS.
	 * <li>We create a GET_FITS object.
	 * <li>We call sendISSCommand to send the GET_FITS object to the ISS.
	 * <li>On return we check whether an error occured. If an error has occurred, 
	 *     the errorNum and errorString is set and the threadState is set to THREAD_STATE_FAILED_GET_FITS.
	 * <li>We extract a list of FITS headers from the returned object.
	 * <li>We add the FITS headers to the ioiFitsHeader.
	 * <li>We set the thread state to FINISHED.
	 * </ul>
	 * @see #init
	 * @see #status
	 * @see #done
	 * @see #quit
	 * @see #offsetTelescope
	 * @see #threadState
	 * @see #ioiFitsHeader
	 * @see #offsetIndex
	 * @see #THREAD_STATE_STARTED
	 * @see #THREAD_STATE_OFFSETING_TELESCOPE
	 * @see #THREAD_STATE_FAILED_OFFSET_TELESCOPE
	 * @see #THREAD_STATE_GET_FITS
	 * @see #THREAD_STATE_FAILED_GET_FITS
	 * @see #THREAD_STATE_FINISHED
	 * @see #errorNum
	 * @see #errorString
	 */
	public void run()
	{
		INST_TO_ISS_DONE instToISSDone = null;
		GET_FITS getFits = null;
		GET_FITS_DONE getFitsDone = null;
		FitsHeaderCardImage cardImage = null;
		Vector list = null;
		int orderNumberOffset;

		threadState = THREAD_STATE_STARTED;
		errorNum = 0;
		errorString = null;
		threadState = THREAD_STATE_OFFSETING_TELESCOPE;
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Offseting telescope.");
		if(offsetTelescope() == false)
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":run:offsetTelescope failed for index "+offsetIndex+".");
			ioi.error(this.getClass().getName()+":run:offsetTelescope failed for index "+offsetIndex+".");
			threadState = THREAD_STATE_FAILED_OFFSET_TELESCOPE;
			return;
		}
		// send GET_FITS command
		threadState = THREAD_STATE_GET_FITS;
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":run:send GET_FITS command.");
		getFits = new GET_FITS("GET_FITS");
		instToISSDone = ioi.sendISSCommand(getFits,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:GET_FITS command failed.");
			ioi.error(this.getClass().getName()+":run:GET_FITS command failed.");
			errorNum = 1;
			errorString = new String(this.getClass().getName()+":run:GET_FITS command failed.");
			threadState = THREAD_STATE_FAILED_GET_FITS;
			return;
		}
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":run:GET_FITS command finished: Extracting data.");
		// Get the returned FITS header information into the FitsHeader object.
		getFitsDone = (ngat.message.ISS_INST.GET_FITS_DONE)instToISSDone;
		// extract specific FITS headers 
		list = getFitsDone.getFitsHeader();
		// get an ordernumber offset
		try
		{
			orderNumberOffset = status.getPropertyInteger("ioi.get_fits.iss.order_number_offset");
		}
		catch(NumberFormatException e)
		{
			orderNumberOffset = DEFAULT_ORDER_NUMBER_OFFSET;
			ioi.error(this.getClass().getName()+":run:Getting order number offset failed.",e);
		}
		// Add the list, which is a Vector containing FitsHeaderCardImage objects, 
		// to ioiFitsHeader
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":run:Adding GET_FITS FITS headers to Fits Header.");
		ioiFitsHeader.addKeywordValueList(list,orderNumberOffset);
		// finish thread
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Finished.");
		threadState = THREAD_STATE_FINISHED;
	}

	/**
	 * Method to offset the telescope by a small amount in RA/Dec, to dither the sky.
	 * @return The method returns true on success and false on failure.	 
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 * @see #errorNum
	 * @see #errorString
	 * @see #offsetIndex
	 */
	protected boolean offsetTelescope() 
	{
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		int raDecOffsetCount,raDecOffsetIndex,offsetSleepTime;
		float raOffset,decOffset;
		boolean doRADecOffset,waitForOffsetToComplete;

	 // get configuration
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":offsetTelescope:Starting, retrieveing config.");
		try
		{
			doRADecOffset = status.getPropertyBoolean("ioi.multrun.offset.enable");
			waitForOffsetToComplete = status.getPropertyBoolean("ioi.multrun.offset.wait_for_complete");
			offsetSleepTime =  status.getPropertyInteger("ioi.multrun.offset.wait_sleep_time");
			raDecOffsetCount = status.getPropertyInteger("ioi.multrun.offset.count");
			raDecOffsetIndex = offsetIndex % raDecOffsetCount;
			raOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".ra");
			decOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".dec");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":offsetTelescope:Retrieving config failed:"+e.toString());
			errorNum = 2;
			errorString = new String(this.getClass().getName()+
						 ":offsetTelescope:Retrieving config failed:"+e.toString());
			return false;
		}
		if(doRADecOffset)
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:We are going to physically move the telescope ("+
				raOffset+","+decOffset+").");
			// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC("OFFSET_RA_DEC("+raOffset+","+decOffset+")");
			offsetRaDecCommand.setRaOffset(raOffset);
			offsetRaDecCommand.setDecOffset(decOffset);
			instToISSDone = ioi.sendISSCommand(offsetRaDecCommand,serverConnectionThread,true,
							   waitForOffsetToComplete);
			// if we are waiting for the offset to complete, and it returns an error, return an error.
			if(waitForOffsetToComplete && (instToISSDone.getSuccessful() == false))
			{
				String errorString = null;
				
				errorNum = 3;
				errorString = new String(this.getClass().getName()+
							 ":offsetTelescope:Offset Ra Dec failed:ra = "+raOffset+
							 ", dec = "+decOffset+":"+instToISSDone.getErrorString());
				ioi.error(errorString);
				return false;
			}
			// if we have not waited for the offset to complete, and we are supposed to be waiting a
			// configured time for the offset to have been done, wait a bit.
			if(!waitForOffsetToComplete)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":offsetTelescope:We have sent the telescope offset, "+
					"but have NOT waited for the DONE.");
				if(offsetSleepTime > 0)
				{
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":offsetTelescope:We have sent the telescope offset, "+
						"but have NOT waited for the DONE, so are waiting here for "+
						offsetSleepTime+" ms.");
					try
					{
						Thread.sleep(offsetSleepTime);
					}
					catch(InterruptedException e)
					{
						ioi.error(this.getClass().getName()+
							  ":offsetTelescope:Offset sleep time was interrupted.",e);
					}
				}
			}
		}// end if
		else
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:Offsets NOT enabled:"+
				"We are NOT going to physically move the telescope ("+raOffset+","+decOffset+").");
		}
		return true;
	}
}
