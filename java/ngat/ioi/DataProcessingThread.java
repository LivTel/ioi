// DataProcessingThread.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * This thread is started by the main IOI robotic software. It sits and waits on a List containing data processing
 * items. 
 * @see HardwareImplementation
 * @author Chris Mottram
 * @version $Revision: 50 $
 */
public class DataProcessingThread extends Thread
{
 	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * A reference to the IOI class instance.
	 */
	protected IOI ioi = null;
	/**
	 * A reference to the IOIStatus class instance that holds status information for IO:I.
	 */
	protected IOIStatus status = null;
	/**
	 * A list of AcquireRamp calls, the data from which needs to be processed.
	 */
	protected List<DataProcessingItem> dataProcessingList = null;
	/**
	 * Boolean to control the termination of the thread.
	 */
	protected boolean done = false;

	/**
	 * Default constructor.
	 */
	public DataProcessingThread()
	{
		super();
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
	 * Initialise the DataProcessingThread's internal data. Should be called after the ioi
	 * pointer is set, before the thread is started.
	 * @exception Exception Throen if the ioi pointer has not been set.
	 * @see #ioi
	 * @see #status
	 * @see #dataProcessingList
	 */
	public void init() throws Exception
	{
		if(ioi == null)
			throw new Exception(this.getClass().getName()+":init:ioi was not set.");
		status = ioi.getStatus();
		dataProcessingList = new Vector<DataProcessingItem>();
	}

	/**
	 * Add a new data processing item to the list of items to be processed.
	 * @param bFS Whether the data was acquired read up the ramp (==0) or in Fowler Sampling Mode (==1).
	 * @param acquireRampCommandCallTime The timestamp of when AcquireRamp was called.
	 * @param f The FITS headers items associated with this data (to be added to the FITS headers of the acquired data).
	 *        This item will be copied as the original may change whilst data processing on this item is underway.
	 * @param exposureCode The exposure code to use when renaming the FITS images.
	 * @exception Exception Thrown if setBFS is given an out of range parameter.
	 * @see #dataProcessingList
	 * @see DataProcessingItem
	 * @see DataProcessingItem#setBFS
	 * @see DataProcessingItem#setAcquireRampCommandCallTime
	 * @see DataProcessingItem#setFitsHeader
	 */
	public void addDataForProcessing(int bFS,long acquireRampCommandCallTime,FitsHeader f,char exposureCode) throws Exception
	{
		DataProcessingItem item = null;

		item = new DataProcessingItem();
		item.setBFS(bFS);
		item.setAcquireRampCommandCallTime(acquireRampCommandCallTime);
		item.setFitsHeader(f);
		item.setExposureCode(exposureCode);
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":addDataForProcessing:"+
			"About to enter synchronised block to add item:"+item);
		synchronized(dataProcessingList)
		{
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":addDataForProcessing:"+
				"In synchronised block adding item:"+item);
			dataProcessingList.add(item);
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":addDataForProcessing:"+
				"Notifying list after adding item:"+item);
			dataProcessingList.notify();
		}
	}

	/**
	 * This attempts to quite the data processing thread by setting quit to true.
	 * @see #quit
	 */
	public void quit()
	{
		done = true;
	}

	/**
	 * Get the current size of the data processing list.
	 * @return The number of elements in the list.
	 * @see #dataProcessingList
	 */
	public int getListSize()
	{
		int listSize;

		synchronized(dataProcessingList)
		{
			listSize = dataProcessingList.size();
		}
		return listSize;
	}

	/**
	 * Main thread run method.
	 * <ul>
	 * <li>We check to ensure the dataProcessingList and status references were setup correctly (by init).
	 * <li>We enter a loop until <b>done</b> is true. <b>done</b> can be set true from the <b>quit</b> method.
	 *     <ul>
	 *     <li>We acquire the dataProcessingList and enter a wait loop for something to be put into the list.
	 *     <li>While the size of the dataProcessingList is greater than zero, we:
	 *         <ul>
	 *         <li>Reacquire the lock on the dataProcessingList and remove the first item from it.
	 *         <li>We call <b>processData</b> on this item. Any exceptions are caught and logged.
	 *         </ul>
	 *     </ul>
	 * <li>Any exceptions in the execution of this thread are caught and logged.
	 * </ul>
	 * @see #init
	 * @see #status
	 * @see #done
	 * @see #dataProcessingList
	 * @see #processData
	 * @see #quit
	 */
	public void run()
	{
		DataProcessingItem item = null;
		int listSize;

		try
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Started.");
			if(dataProcessingList == null)
			{
				throw new Exception(this.getClass().getName()+
						    ":run:dataProcessingList was not initialised.");
			}
			if(status == null)
			{
				throw new Exception(this.getClass().getName()+
						    ":run:status was not initialised.");
			}
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Entering main loop.");
			done = false;
			while(done == false)
			{
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":run:About to enter synchronised block to check list size.");
				synchronized(dataProcessingList)
				{
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":run:In synchronised block to check list size.");
					while((done == false)&&(dataProcessingList.size() == 0))
						dataProcessingList.wait(10000);
					listSize = dataProcessingList.size();
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":run:In synchronised block:list size = "+listSize);
				}
				while(listSize > 0)
				{
					ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						":run:About to enter synchronised block remove first item.");
					synchronized(dataProcessingList)
					{
						item = dataProcessingList.get(0);
						dataProcessingList.remove(0);
						listSize = dataProcessingList.size();
						ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
							":run:In synchronised block:removed first item:list size now = "+listSize);
					}
					try
					{
						ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
							":run:Processing item:"+item);
						processData(item);
					}
					catch(Exception e)
					{
						ioi.error(this.getClass().getName()+":run:Processing item: "+item+
							  " threw Exception:",e);
					}
				}// dataProcessingList.size() > 0
			}// end while (done =- false)
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Finished.");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":run:Threw Exception:",e);
		}
	}

	/**
	 * Process the data.
	 * <ul>
	 * <li>We call <b>findRampData</b> to find where the IDL Socket Server has created a new directory with the acquired data.
	 * <li>We call <b>findFITSFilesInDirectory</b> to locate all the generated FITS files from the ramp.
	 * <li>We call <b>addFitsHeadersToFitsImages</b> to add the previously retrieved ISS/BSS/IO:I headers
	 *     to the IDL Socket Server generated FITS images.
	 * <li>We call <b>flipFitsFiles</b> which, depending on a config option, 
	 *     flips the image data inside the FITS images to the correct orientation.
	 * <li>We call <b>renameFitsFiles</b> which, depending on a config option, 
	 *     renames the IDL Socket Server generated FITS images to LT standard filenames.
	 * <li>We call <b>deleteIDLDirectory</b> which deletes the IDL directory and any remaining data within it.
	 * </ul>
	 * @param item The data to be processed.
	 * @exception Throwen if an error occurs.
	 * @see #findRampData
	 * @see #findFITSFilesInDirectory
	 * @see #addFitsHeadersToFitsImages
	 * @see #flipFitsFiles
	 * @see #renameFitsFiles
	 * @see #deleteIDLDirectory
	 */
	protected void processData(DataProcessingItem item) throws Exception
	{
		FitsHeader fitsHeader;
		List<File> fitsFileList = null;
		String directory = null;
		int bFS;
		long acquireRampCommandCallTime;
		char exposureCode;

		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Starting processing item:"+item);
		acquireRampCommandCallTime = item.getAcquireRampCommandCallTime();
		bFS = item.getBFS();
		fitsHeader = item.getFitsHeader();
		exposureCode = item.getExposureCode();
		// findRampData
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Finding ramp data for exposure.");
		directory = findRampData(bFS,acquireRampCommandCallTime);
		// findFITSFilesInDirectory
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Listing FITS images in Ramp Data directory "+
			directory+".");
		fitsFileList = findFITSFilesInDirectory(bFS,directory);
		// addFitsHeadersToFitsImages
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Adding FITS headers to "+fitsFileList.size()+
			" FITS images.");
		addFitsHeadersToFitsImages(fitsHeader,fitsFileList);
		// flipFitsFiles
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Flip image data in FITS images (if enabled).");
		flipFitsFiles(fitsFileList);
		// renameFitsFiles
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":processData:Rename generated FITS images to LT spec (if enabled).");
		renameFitsFiles(fitsFileList,exposureCode);
		// deleteDirectory
		// We now want to delete the original IDL generated directory, to improve the 
		// speed of findRampData
		deleteIDLDirectory(directory);
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processData:Finished processing item:"+item);
	}

	/**
	 * Method to find the directory containing the ramp data which was initiatated at the time specified
	 * by acquireRampCommandCallTime.
	 * <ul>
	 * <li>The root directory to search from is found from the "ioi.data.directory.root" property.
	 * <li>Based on bFS we work out the Fowler Sample mode 
	 *     directory string to postpend to the root, and retrieve the relevant property, one of:
	 *     'ioi.data.directory.up_the_ramp' or 'ioi.data.directory.fowler'.
	 * <li>We create a file from the resultant string, and test it is a directory. If so, we list it's contents.
	 * <li>We loop over each file in the directory list:
	 *     <ul>
	 *     <li>If the file is a directory, we attempt to parse it as a date of the format ""yyyyMMDDHHmmss".
	 *         The IDL socket server creates a directory of this format, for each AcquireRamp command issued.
	 *     <li>We ensure the parsed date is after acquireRampCommandCallTime.
	 *     <li>If the parsed date is soonest one after acquireRampCommandCallTime we have found, we save the
	 *         file in smallestDiffFile and the diff time in smallestDiffTime.
	 *     </ul>
	 * <li>The smallestDiffFile is converted into a string and returned.
	 * </ul>
	 * @param bFS Whether we are in Fowler Sampling mode (bFS == 1) or Read up the Ramp mode (bFS == 0).
	 * @param acquireRampCommandCallTime A timestamp taken just before the AcquireRampCommand was started.
	 * @return A string, containing the directory containing the FITS images associated with the ACQUIRERAMP
	 *         just executed.
	 * @exception Exception Thrown if the GetConfigCommand command fails, or returns an error.
	 * @see #ioi
	 * @see #status
	 * @see IOIStatus#getProperty
	 */
	protected String findRampData(int bFS, long acquireRampCommandCallTime) throws Exception
	{
		Date fileDate = null;
		File directoryFile = null;
		File directoryList[];
		File smallestDiffFile = null;
		SimpleDateFormat dateFormat = null;
		String rootDirectoryString = null;
		String fsModeDirectoryString = null;
		String directoryString = null;
		long diffTime,smallestDiffTime;

		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":findRampData:started.");
		// remove milliseconds within the second from acquireRampCommandCallTime 
		// This is because the directory file date is accurate to 1 second, so
		// the directory can appear to have been created before acquireRampCommandCallTime by < 1 second
		acquireRampCommandCallTime -= (acquireRampCommandCallTime%1000);
		// get root directory
		rootDirectoryString = status.getProperty("ioi.data.directory.root");
		ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":findRampData:root directory:"+rootDirectoryString+".");
		// get the current configuration of the array
		if(bFS == 0)
			fsModeDirectoryString = status.getProperty("ioi.data.directory.up_the_ramp");
		else if(bFS == 1)
			fsModeDirectoryString = status.getProperty("ioi.data.directory.fowler");
		else
		{
			throw new Exception(this.getClass().getName()+":findRampData:Illegal bFS value:"+bFS);
		}
		directoryString = new String(rootDirectoryString+File.separator+fsModeDirectoryString+File.separator);
		ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":findRampData:Searching date stamp directories in:"+directoryString+".");
		// The directoryString should contain a list of date stamp directories containing the actual data.
		// Find the first date stamp after the acquireRampCommandCallTime
		directoryFile = new File(directoryString);
		if(directoryFile.isDirectory() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:specified directory is not a directory:"+directoryFile);
		}
		directoryList = directoryFile.listFiles();
		if(directoryList == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:Directory list was null:"+directoryFile);
		}
		ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			":findRampData:Found "+directoryList.length+" files in directory:"+directoryFile+".");
		// date stamped directories should be of the form: 20130424170309
		dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		smallestDiffTime = Integer.MAX_VALUE;
		smallestDiffFile = null;
		for(int i = 0; i < directoryList.length; i++)
		{
			if(directoryList[i].isDirectory())
			{
				// should be a datestamp directory of the form: 20130424170309
				fileDate = dateFormat.parse(directoryList[i].getName());
				// fileDate is null if the parse fails
				if(fileDate != null)
				{
					diffTime = fileDate.getTime()-acquireRampCommandCallTime;
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":findRampData:"+directoryList[i]+" has diff time "+
						(diffTime/1000.0)+" seconds after acquire ramp command call time.");
					// We are looking for the smmalest positive time 
					// after the acquireRampCommandCallTime
					if((diffTime >= 0)&&(diffTime < smallestDiffTime))
					{
						smallestDiffTime = diffTime;
						smallestDiffFile = directoryList[i];
						ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
							":findRampData:"+directoryList[i]+" has smallest diff time "+
							(smallestDiffTime/1000.0)+
							" seconds after acquire ramp command call time.");
					}
					else
					{
						ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
							":findRampData:"+directoryList[i]+" has diff time "+
							(diffTime/1000.0)+
							" seconds after acquire ramp command call time.");
					}
				}
				else
				{
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":findRampData:Failed to parse date stamp directory:"+
						directoryList[i]+".");
				}
			}
			else
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":findRampData:Not a directory:"+directoryList[i]+".");
			}
		}// end for
		if(smallestDiffFile == null)
		{
			throw new Exception(this.getClass().getName()+":findRampData:No suitable directory found.");
		}
		directoryString = smallestDiffFile.toString();
		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			":findRampData:finished and returning directory:"+directoryString+" with diff time "+
			(smallestDiffTime/1000.0)+".");
		return directoryString;
	}

	/**
	 * Given a directory, find all the FITS images in it.
	 * We no longer look at subdirectories. The Teledyne software puts a Result/CDSResult.fits in it's 
	 * date-stamped directory when running in FOWLER mode, however we don't want to annotate and rename that file.
	 * This method now checks "ioi.file.fits.rename.read_up_ramp_as_cds". If true, the first two fits images
	 * and the last two fits images in the read up the ramp directory are kept, and the rest are removed from the
	 * returned list of images. This allows us to use read up the ramp to create a set of 4 fits files that can be
	 * reduced as CDS images.
	 * @param bFS Whether we are in Fowler Sampling mode (bFS == 1) or Read up the Ramp mode (bFS == 0).
	 * @param directoryString A string containing the root directory to start the search at.
	 * @return A List, containing File object instances, where each item represents a FITS image
	 *        within the directory.
	 * @exception IllegalArgumentException Thrown if directoryString is not a string 
	 *            representing a valid directory.
	 * @exception Exception Thrown if listing a directory returns null.
	 */
	public List<File> findFITSFilesInDirectory(int bFS,String directoryString) throws Exception, IllegalArgumentException
	{
		File directoryFile = null;
		List<File> directoryList = new Vector<File>();
		File fileList[];
		List<File> fitsFileList = new Vector<File>();
		boolean readUpRampAsCDS;

		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":findFITSFilesInDirectory:Loading config.");
		if(bFS == 0)
		{
			readUpRampAsCDS =  status.getPropertyBoolean("ioi.file.fits.rename.read_up_ramp_as_cds");
		}
		else
			readUpRampAsCDS = false;
		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":findFITSFilesInDirectory:Starting from directory:"+directoryString+".");
		directoryFile = new File(directoryString);
		if(directoryFile.isDirectory() == false)
		{
			throw new IllegalArgumentException(this.getClass().getName()+":findFITSFilesInDirectory:"+
							   directoryString+" not a directory.");
		}
		// add the top directory to the list of directories to search
		directoryList.add(directoryFile);
		// iterate over the directories to search
		while(directoryList.size() > 0)
		{
			// get the directory from the directory list, and then remove it from the list
			directoryFile = (File)(directoryList.get(0));
			directoryList.remove(directoryFile);
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":findFITSFilesInDirectory:Currently listing directory:"+directoryFile+".");
			// get a list of files in that directory.
			fileList = directoryFile.listFiles();
			if(fileList == null)
			{
				throw new Exception(this.getClass().getName()+":findFITSFilesInDirectory:"+
						    "Directory list was null:"+directoryFile);
			}
			for(int i = 0; i < fileList.length; i++)
			{
				// no longer add sub-directories to the search list
				//if(fileList[i].isDirectory())
				//{
					// add the directory to the list of directories to search
				//	ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				//		":findFITSFilesInDirectory:Adding directory:"+fileList[i]+
				//		" to search directory list.");
				//	directoryList.add(fileList[i]);
				//}
				//else
				//{
				// is it a fits file?
				if(fileList[i].toString().endsWith(".fits"))
				{
					ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
						":findFITSFilesInDirectory:Adding FITS image:"+fileList[i]+
						" to results list.");
					fitsFileList.add(fileList[i]);	
				}
				else
				{
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":findFITSFilesInDirectory:File:"+fileList[i]+
						" not a FITS image.");
				}
				//}
			}// end for over files in that directory
		}// end while directories in the list
		if(readUpRampAsCDS)
		{
			int originalFitsFileListLength;

			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":findFITSFilesInDirectory:Doing Read up the Ramp as CDS: "+
				"Keep first two and last two FITS images and remove intermediate files.");
			originalFitsFileListLength = fitsFileList.size();
			for(int i = 0; i < 2; i++)
			{
				ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					":findFITSFilesInDirectory:Doing Read up the Ramp as CDS: Keeping:"+
					fitsFileList.get(i));
			}
			// remove index 2 originalFitsFileListLength-4 times
			// index 0 and 1 contains fist 2 images
			// remember list is shuffled up as index 2 is removed, and list.size() reduces.
			// therefore use originalFitsFileListLength-2.
			for(int i = 2; i < (originalFitsFileListLength-2); i++)
			{
				ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					":findFITSFilesInDirectory:Doing Read up the Ramp as CDS: Removing:"+
					fitsFileList.get(2));
				fitsFileList.remove(2);
			}
			// So now indexes 2 and 3 in the list should be the last 2 frames.
			for(int i = 2; i < fitsFileList.size();i++)
			{
				ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					":findFITSFilesInDirectory:Doing Read up the Ramp as CDS: Keeping:"+
					fitsFileList.get(i));
			}
		}
		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			":findFITSFilesInDirectory:directory:"+directoryString+" contained "+fitsFileList.size()+
			" FITS images.");
		return fitsFileList;
	}

	/**
	 * Method to add the FITS headers contained in ioiFitsHeader to the specified List of FITS images.
	 * @param fitsHeader The saved list of FITS headers to add to the list of FITS files.
	 * @param fitsImageList A List, containing File object instances, where each item represents a FITS image
	 *        within the directory or it's subdirectories.
	 * @exception FitsHeaderException Thrown if the writeFitsHeader method fails.
	 * @see #ioi
	 */
	public void addFitsHeadersToFitsImages(FitsHeader fitsHeader, List fitsImageList) throws FitsHeaderException
	{
		File fitsFile = null;
		boolean fitsFilenameAnnotate;

		fitsFilenameAnnotate = status.getPropertyBoolean("ioi.file.fits.annotate");
		if(fitsFilenameAnnotate)
		{
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":addFitsHeadersToFitsImages:Adding "+fitsHeader.getKeywordValueCount()+
				" headers to "+fitsImageList.size()+" FITS images.");
			for(int fitsImageIndex=0;fitsImageIndex < fitsImageList.size(); fitsImageIndex++)
			{
				fitsFile = (File)(fitsImageList.get(fitsImageIndex));
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":addFitsHeadersToFitsImages:Adding headers to "+fitsFile.toString());
				fitsHeader.writeFitsHeader(fitsFile.toString());
			}
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":addFitsHeadersToFitsImages:Finished.");
		}
		else
		{
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":addFitsHeadersToFitsImages:ioi.file.fits.annotate is failse:"+
				"Not annotating FITS headers.");

		}
	}

	/**
	 * Method to flip FITS image data within a list of FITS images.
	 * The images are flipped if the property "ioi.file.fits.flip" is true. The properties
	 * "ioi.file.fits.flip.x" and "ioi.file.fits.flip.y" determine the direction of flipping.
	 * @param fitsImageList A List, containing File object instances, where each item represents a FITS image
	 *        within the directory or it's subdirectories.
	 * @exception FitsFlipException Thrown if the image flipping method fails.
	 * @see #ioi
	 */
	public void flipFitsFiles(List fitsImageList) throws FitsFlipException
	{
		FitsFlip fitsFlip = null;
		File fitsFile = null;
		boolean fitsFileFlip,flipX,flipY;

		fitsFileFlip = status.getPropertyBoolean("ioi.file.fits.flip");
		if(fitsFileFlip)
		{
			fitsFlip = ioi.getFitsFlip();
			flipX = status.getPropertyBoolean("ioi.file.fits.flip.x");
			flipY = status.getPropertyBoolean("ioi.file.fits.flip.y");
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":flipFitsFiles:Flipping "+fitsImageList.size()+" FITS images.");
			for(int fitsImageIndex=0;fitsImageIndex < fitsImageList.size(); fitsImageIndex++)
			{
				fitsFile = (File)(fitsImageList.get(fitsImageIndex));
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":flipFitsFiles:Flipping "+fitsFile.toString()+" in x:"+flipX+" in y:"+flipY);
				fitsFlip.flip(fitsFile.toString(),flipX,flipY);
			}
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":flipFitsFiles:Finished.");
		}
		else
		{
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":flipFitsFiles:ioi.file.fits.flip is failse:Not flipping FITS images.");

		}
	}

	/**
	 * Rename the FITS files  specified into a standard LT run with a multrun in the configured LT FITS filename 
	 * directory. The FITS images should all be from the same exposure (ramp). Currently they are renamed
	 * as a sinle multrun, single run with the window number incrementing. The multrun should have been
	 * incremented externally to this method.
	 * @param fitsImageList A List, containing File object instances, where each item represents a FITS image
	 *        within the IDL socket server directory structure. The contents of this list are changed
	 *        to the renamed LT style FITS filenames.
	 * @param exposureCode A character describing which type of exposure we are doing, 
	 *        ARC|BIAS|DARK|EXPOSURE|SKY_FLAT|ACQUIRE
	 * @exception Exception Thrown if the rename operation fails.
	 * @see IOI#getFitsFilename
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_ARC
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_BIAS
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_DARK
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_EXPOSURE
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_SKY_FLAT
	 * @see ngat.fits.FitsFilename#EXPOSURE_CODE_ACQUIRE
	 */
	public void renameFitsFiles(List<File> fitsImageList,char exposureCode) throws Exception
	{
		File fitsFile = null;
		File newFitsFile = null;
		FitsFilename fitsFilename = null;
		String newFilename = null;
		boolean fitsFilenameRename,retval;

		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			":renameFitsFiles:Renaming "+fitsImageList.size()+" FITS images into standard LT files.");
		fitsFilenameRename = status.getPropertyBoolean("ioi.file.fits.rename");
		if(fitsFilenameRename)
		{
			// get the FITS filename instance
			fitsFilename = ioi.getFitsFilename();
			// New Run in Multrun
			fitsFilename.nextRunNumber();
			// set exposure code appropriately
			fitsFilename.setExposureCode(exposureCode);
			for(int fitsImageIndex=0;fitsImageIndex < fitsImageList.size(); fitsImageIndex++)
			{
				fitsFile = (File)(fitsImageList.get(fitsImageIndex));
				fitsFilename.setWindowNumber(fitsImageIndex+1); // window number 1..n
				newFilename = fitsFilename.getFilename();
				newFitsFile = new File(newFilename);
				retval = fitsFile.renameTo(newFitsFile);
				if(retval == false)
				{
					throw new Exception(this.getClass().getName()+
							    ":renameFitsFiles:Renaming "+fitsFile.toString()+
							    " to "+newFitsFile.toString()+" failed.");
				}
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":renameFitsFiles:renamed "+fitsFile.toString()+" to "+
					newFitsFile.toString()+".");
				// update fitsImageList with renamed file.
				fitsImageList.set(fitsImageIndex,newFitsFile);
			}
		}
		else
		{
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":renameFitsFiles:fitsFilenameRename was false, NOT renaming FITS filenames.");
		}
		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":renameFitsFiles:Finished.");
	}

	/**
	 * Delete the original IDL directory, all remaining FITS images within it, and sub-directories.
	 * @param directoryString A string containing the root directory to delete.
	 * @exception Exception Thrown if an error occurs.
	 */
	public void deleteIDLDirectory(String directoryString) throws Exception
	{
		File directoryFile = null;
		List<File> directoryList = new Vector<File>();
		File fileList[];
		boolean deleteDirectory;

		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":deleteIDLDirectory:Deleting "+directoryString+".");
		ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":deleteIDLDirectory:Starting from directory:"+directoryString+".");
		directoryFile = new File(directoryString);
		if(directoryFile.isDirectory() == false)
		{
			throw new IllegalArgumentException(this.getClass().getName()+":deleteIDLDirectory:"+
							   directoryString+" not a directory.");
		}
		// add the top directory to the list of directories to search
		directoryList.add(directoryFile);
		// iterate over the directories to search
		while(directoryList.size() > 0)
		{
			// get the latest directory from the directory list
			directoryFile = (File)(directoryList.get(directoryList.size()-1));
			ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				":deleteIDLDirectory:Currently listing directory:"+directoryFile+".");
			// get a list of files in that directory.
			fileList = directoryFile.listFiles();
			if(fileList == null)
			{
				throw new Exception(this.getClass().getName()+":deleteIDLDirectory:"+
						    "Directory list was null:"+directoryFile);
			}
			deleteDirectory = true;
			for(int i = 0; i < fileList.length; i++)
			{
				if(fileList[i].isDirectory())
				{
					// add the directory to the list of directories to search
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":deleteIDLDirectory:Adding directory:"+fileList[i]+
						" to search directory list.");
					directoryList.add(fileList[i]);
					// we can't delete the directory we are currently in as there
					// is a subdirectory to delete first
					deleteDirectory = false;
				}
				else
				{
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":deleteIDLDirectory:Deleteing:"+fileList[i]+".");
					fileList[i].delete();	
				}

			}// end for over files in that directory
			// if all the files in this directory have been deleted, remove the directory from the
			// list, and physically delete the directory on disk
			// Otherwise the current directory remains in the list, and will hopefully be reselected for
			// deletion after it's subdirectories have been process and deleted.
			if(deleteDirectory)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":deleteIDLDirectory:Deleteing:"+directoryFile+".");
				directoryFile.delete();
				directoryList.remove(directoryFile);
			}
		}// end while directories in the list
		ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":deleteIDLDirectory:Finished Deleting "+
			directoryString+".");
	}

	/**
	 * Instances of this class represent acquired data that needs to be processed.
	 * The inputs to the data processing are:
	 * <ul>
	 * <li>acquireRampCommandCallTime The time we called the IDL ACQUIRERAMP command. 
	 *     This can be used (along with bFS)
	 *     to find the ramp data associated with this call. 
	 *     This is a date stamped directory containing FITS images.
	 * <li>fitsHeader A copy of the FITS headers stored in the main IO:I program when the AcquireRamp was called.
	 *     The main copy will be changed before the next ACQUIRERAMP in the MULTRUN is started, so a copy is needed
	 *     to stop the FITS headers being modified for the next exposure.
	 * <li>bFS An integer (but sort of a boolean). bFS ==1 means the data was acquired using Fowler sampling mode, bFS == 0 means the
	 *     data was acquired using read up the ramp mode.
	 * <li>exposureCode The exposure code to use when renaming the FITS filename.
	 * </ul>
	 */
	public class DataProcessingItem
	{
		/**
		 * The time we called the IDL ACQUIRERAMP command. 
		 */
		protected long acquireRampCommandCallTime;
		/**
		 * A deep copy (sharing no mutable object references with the original) of the
		 * FitsHeaders collected prior to the AcquireRamp, that should be added to the FITS headers
		 * in the acquired data.
		 */
		protected FitsHeader fitsHeader;
		/**
		 * Whether we are using the array in Fowler Sampling (==1) or Read Up The Ramp (==0) mode.
		 */
		protected int bFS = -1;
		/**
		 * The exposure code to use when renaming the FITS filename.
		 */
		protected char exposureCode;

		/**
		 * Defaulot constructor.
		 */
		public DataProcessingItem()
		{
			super();
		}

		/**
		 * Set the timestamp we save when the IDL ACQUIRERAMP command was called. This is used to determine
		 * where the IDL software saved the generated data.
		 * @param t A Java timestamp (long number of milliseconds since the epoch).
		 * @see #acquireRampCommandCallTime
		 */
		public void setAcquireRampCommandCallTime(long t)
		{
			acquireRampCommandCallTime = t;	
		}

		/**
		 * Get the timestamp we saved when the IDL ACQUIRERAMP command was called. This is used to determine
		 * where the IDL software saved the generated data.
		 * @return A Java timestamp (long number of milliseconds since the epoch).
		 * @see #acquireRampCommandCallTime
		 */
		public long getAcquireRampCommandCallTime()
		{
			return acquireRampCommandCallTime;
		}

		/**
		 * Set the FITS headers object, that contains the headers used to annotate the data collected by the 
		 * IDL ACQUIRERAMP command. A copy containing no shared mutable references is made, as the original
		 * may be modified by the next AcquireRamp in the loop before we try to process the data for this
		 * ACQUIRERAMP.
		 * @param f The FitsHeader instance to copy.
		 * @see #fitsHeader
		 * @see ngat.fits.FitsHeader#copy
		 */
		public void setFitsHeader(FitsHeader f)
		{
			// we need to copy this instance of the fitsHeader, and the List element contained within it,
			// as the same instance has it's contents (list) cleared and recreated for each exposure
			// in a Multrun. Therefore some level of clone is needed. 
			// It looks like each element in the list (FitsHeaderCardImage instance) ir probably
			// recreated when the headers are generated for each MULTRUN, but it is difficult to be sure,
			// so the safest option may be to clone those as well.
			fitsHeader = f.copy();
		}

		/**
		 * Get the copied set of FITS headers that need to be added to the data saved by the IDL 
		 * ACQUIRERAMP command.
		 * @return The saved fits header instance.
		 * @see #fitsHeader
		 */
		public FitsHeader getFitsHeader()
		{
			return fitsHeader;
		}

		/**
		 * Set whether we are using the array in Fowler Sampling (==1) or Read Up The Ramp (==0) mode.
		 * @param b If we are using the array in Fowler Sampling mode, this should be 1.
		 *          If we are using the array in  Read Up The Ramp mode, this should be 0.
		 * @exception Exception Thrown if b was out of range.
		 * @see #bFS
		 */
		public void setBFS(int b) throws Exception
		{
			if((b < 0)||(b > 1))
			{
				throw new Exception(this.getClass().getName()+
						    ":setBFS:Value was out of range (0..1):"+b);
			}
			bFS = b;
		}

		/**
		 * Get whether this ramp was acquired in Fowler Sampling or Read Up The Ramp mode.
		 * @return Return 0 if the ramp was acquired in Read Up The Ramp mode, and 
		 *         1 if the ramp was acquired in Fowler Sampling mode.
		 * @see #bFS
		 */
		public int getBFS()
		{
			return bFS;
		}

		/**
		 * Set the exposure code to be used when renaming images.
		 * @param c The exposure code.
		 * @see #exposureCode
		 */
		public void setExposureCode(char c)
		{
			exposureCode = c;
		}

		/**
		 * Get the exposure code to be used when renaming images.
		 * @return The exposure code.
		 * @see #exposureCode
		 */
		public char getExposureCode()
		{
			return exposureCode;
		}

		/**
		 * Return a string describing the data processing item.
		 * @see #acquireRampCommandCallTime
		 * @see #bFS
		 * @see #fitsHeader
		 * @see #exposureCode
		 */
		public String toString()
		{
			return new String(this.getClass().getName()+":Acquire Ramp Call Time:"+acquireRampCommandCallTime+":bFS:"+bFS+
					  ":FitsHeader:"+fitsHeader+":exposure code:"+exposureCode);
		}
	}

}
