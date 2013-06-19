// AcquireRampAndGuideCallbackInterface.java
// $Header$
package ngat.ioi.test;
import java.lang.*;
import ngat.util.logging.*;

/**
 * This interface defines a method that is called from within the expose method of AcquireRampAndGuide,
 * whenever a science or guide sub-ramp has been completed.
 * @author Chris Mottram
 * @version $Revision: 1.2 $
 */
public interface AcquireRampAndGuideCallbackInterface
{
	/**
	 * Type of data returned in the callback, this is science data.
	 */
	public final static int DATA_TYPE_SCIENCE = 0;
	/**
	 * Type of data returned in the callback, this is guide data.
	 */
	public final static int DATA_TYPE_GUIDE = 1;
	/**
	 * The callback method invoked whenever a science or guide sub-ramp has been completed.
	 * @param dataType Which kind of data is being returned, one of DATA_TYPE_SCIENCE or DATA_TYPE_GUIDE .
	 * @param directory The directory containing the data.
	 */
	public void newData(int dataType,String directory);
}
//
// $Log$
//
