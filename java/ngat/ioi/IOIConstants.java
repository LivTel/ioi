// IOIConstants.java
// $Header: /home/dev/src/tiptilt/java/ngat/tiptilt/thor/RCS/THORConstants.java,v 1.1 2010/10/07 13:23:24 cjm Exp $
package ngat.ioi;

import java.lang.*;
import java.io.*;

/**
 * This class holds some constant values for the IOI program. 
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class IOIConstants
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Error code. No error.
	 */
	public final static int IOI_ERROR_CODE_NO_ERROR 		= 0;
	/**
	 * The base Error number, for all IO:I error codes. 
	 * See http://ltdevsrv.livjm.ac.uk/~dev/errorcodes.html for details.
	 */
	public final static int IOI_ERROR_CODE_BASE 			= 1400000;
	/**
	 * Default thread priority level. This is for the server thread. Currently this has the highest priority,
	 * so that new connections are always immediately accepted.
	 * This number is the default for the <b>ioi.thread.priority.server</b> property, if it does not exist.
	 */
	public final static int IOI_DEFAULT_THREAD_PRIORITY_SERVER      = Thread.NORM_PRIORITY+2;
	/**
	 * Default thread priority level. 
	 * This is for server connection threads dealing with sub-classes of the INTERRUPT
	 * class. Currently these have a higher priority than other server connection threads,
	 * so that INTERRUPT commands are always responded to even when another command is being dealt with.
	 * This number is the default for the <b>ioi.thread.priority.interrupt</b> property, if it does not exist.
	 */
	public final static int IOI_DEFAULT_THREAD_PRIORITY_INTERRUPT	= Thread.NORM_PRIORITY+1;
	/**
	 * Default thread priority level. This is for most server connection threads. 
	 * Currently this has a normal priority.
	 * This number is the default for the <b>ioi.thread.priority.normal</b> property, if it does not exist.
	 */
	public final static int IOI_DEFAULT_THREAD_PRIORITY_NORMAL	= Thread.NORM_PRIORITY;
	/**
	 * Default thread priority level. This is for the Telescope Image Transfer server/client threads. 
	 * Currently this has the lowest priority, so that the camera control is not interrupted by image
	 * transfer requests.
	 * This number is the default for the <b>ioi.thread.priority.tit</b> property, if it does not exist.
	 */
	public final static int IOI_DEFAULT_THREAD_PRIORITY_TIT		= Thread.MIN_PRIORITY;
}
//
// $Log$
//
