// TestAddFitsHeadersToFitsImages.java
// $HeadURL$

import java.io.*;
import java.lang.*;
import java.text.SimpleDateFormat;
import java.util.*;

import ngat.fits.*;
import ngat.util.logging.*;

/**
 * This test program is used to test adding FITS headers to a set of FITS images. This seems to take a long
 * time when using the virtual shared disk area on VirtualBox. We want to test annotating the same set of FITS
 * images using this virtual shared disk area, and the underlying disk itself, to see whether this is a configuration
 * or software program.
 */
public class TestAddFitsHeadersToFitsImages
{
	/**
	 * The directory containing the FITS images to annotate.
	 */
	private String directory = null;
	/**
	 * A string containing the filename of the FITS header defaults file to load, the keywords within are
	 * used to annotate the FITS images.
	 */
	private String fitsHeaderFilename = null;
	/**
	 * A set of Fits Headers to load from disk, that will be added to the FITS images in the specified directory.
	 */
	private FitsHeaderDefaults fitsHeaderDefaults = null;
	/**
	 * A set of FITS headers that will be added to the FITS images in the specified directory.
	 */
	private FitsHeader fitsHeader = null;
	/**
	 * Logger to log to.
	 */
	Logger logger = null;

	/**
	 * Constructor. The logger instance is created, as are the fitsHeaderDefaults and fitsHeader instances.
	 * @see #logger
	 * @see #fitsHeaderDefaults
	 * @see #fitsHeader
	 */
	public TestAddFitsHeadersToFitsImages()
	{
		super();
		logger = LogManager.getLogger(this);
		fitsHeaderDefaults = new FitsHeaderDefaults();
		fitsHeader = new FitsHeader();
	}

	/**
	 * This is the initialisation routine. initialiseLogger is called to setup the logger.
	 * @see #initialiseLogger
	 */
	private void init()
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":init:Initialing loggers.");
		initialiseLogger();
	}

	/**
	 * Initialise the logger.
	 * @see #logger
	 * @see #copyLogHandlers
	 */
	protected void initialiseLogger()
	{
		LogHandler handler = null;
		BogstanLogFormatter blf = null;

		logger.setLogLevel(Logging.ALL);
		blf = new BogstanLogFormatter();
		blf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		handler = new ConsoleLogHandler(blf);
		handler.setLogLevel(Logging.ALL);
		logger.addHandler(handler);
		// copy logger log handlers to other relevant classes
		//copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.SendConfigCommand"),null,Logging.ALL);
		//copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.BSSServer"),null,Logging.ALL);
		//copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.BSSServerConnectionThread"),null,
		//		Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.fits.FitsHeader"),null,Logging.ALL);
	}

	/**
	 * Method to copy handlers from one logger to another. The outputLogger's channel ID is also
	 * copied from the input logger.
	 * @param inputLogger The logger to copy handlers from.
	 * @param outputLogger The logger to copy handlers to.
	 * @param lf The log filter to apply to the output logger. If this is null, the filter is not set.
	 * @param logLevel The log level to set the logger to filter against.
	 */
	protected void copyLogHandlers(Logger inputLogger,Logger outputLogger,LogFilter lf,int logLevel)
	{
		LogHandler handlerList[] = null;
		LogHandler handler = null;

		handlerList = inputLogger.getHandlers();
		for(int i = 0; i < handlerList.length; i++)
		{
			handler = handlerList[i];
			outputLogger.addHandler(handler);
		}
		outputLogger.setLogLevel(inputLogger.getLogLevel());
		if(lf != null)
			outputLogger.setFilter(lf);
		outputLogger.setChannelID(inputLogger.getChannelID());
		outputLogger.setLogLevel(logLevel);
	}

	/**
	 * This routine parses arguments passed into the test program.
	 * @see #fitsHeaderFilename
	 * @see #directory
	 * @see #help
	 */
	private void parseArgs(String[] args)
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-f")||args[i].equals("-fitsheaderfilename"))
			{
				if((i+1)< args.length)
				{
					fitsHeaderFilename = new String(args[i+1]);
					i++;
				}
				else
					System.err.println("-fitsheaderfilename requires a filename");
			}
			else if(args[i].equals("-d")||args[i].equals("-directory"))
			{
				if((i+1)< args.length)
				{
					directory = new String(args[i+1]);
					i++;
				}
				else
					System.err.println("-directory requires a filename");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else
				System.err.println(this.getClass().getName()+":Option not supported:"+args[i]);
		}
	}

	/**
	 * Help message routine.
	 */
	private void help()
	{
		System.out.println(this.getClass().getName()+" Help:");
		System.out.println("Options are:");
		System.out.println("\t-d[irectory] <filename> - "+
				   "The directory containing the FITS images to annotate.");
		System.out.println("\t-f[itsheaderfilename] <filename> - "+
				   "The filename containing a list of FITS headers keywords and values "+
				   "that are to be added to the FITS images in the specified directory.");
	}

	/**
	 * Method to actually do the program.
	 * <ul>
	 * <li>The FITS header defaults are loaded from disk.
	 * <li>We get a card image list from the loaded  defaults (getCardImageList).
	 * <li>We add the retrieved list to the FitsHeader, to be added to the FITS images (addKeywordValueList).
	 * <li>We search the specified directory for FITS files to annotate (findFITSFilesInDirectory).
	 * <li>We add the FITS headers to each FITS image in the list (addFitsHeadersToFitsImages).
	 * </ul>
	 * @exception FileNotFoundException Thrown by fitsHeaderDefaults.load.
	 * @exception IOException Thrown by fitsHeaderDefaults.load.
	 * @exception FitsHeaderException Thrown by getCardImageList/addFitsHeadersToFitsImages.
	 * @exception Exception Thrown by findFITSFilesInDirectory.
	 * @see #directory
	 * @see #fitsHeaderDefaults
	 * @see #fitsHeaderFilename
	 * @see #findFITSFilesInDirectory
	 * @see #addFitsHeadersToFitsImages
	 * @see ngat.fits.FitsHeaderDefaults#load
	 * @see ngat.fits.FitsHeaderDefaults#getCardImageList
	 * @see ngat.fits.FitsHeader#addKeywordValueList
	 */ 
	protected void run() throws FileNotFoundException, IOException, FitsHeaderException, Exception
	{
		Vector defaultFitsHeaderList = null;
		List fitsFileList = null;

		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Loading FITS header defaults from:"+
			   fitsHeaderFilename);
		fitsHeaderDefaults.load(fitsHeaderFilename);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":run:Retreiving FITS header defaults List.");
		defaultFitsHeaderList = fitsHeaderDefaults.getCardImageList();
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":run:Adding "+defaultFitsHeaderList.size()+" FITS headers to list.");
		fitsHeader.addKeywordValueList(defaultFitsHeaderList,0);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":run:Searching "+directory+" for FITS files.");
		fitsFileList = 	findFITSFilesInDirectory(directory);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":run:Adding "+defaultFitsHeaderList.size()+" FITS headers to "+
			   fitsFileList.size()+" FITS files.");
		addFitsHeadersToFitsImages(fitsFileList);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Finished.");
	}

	/**
	 * Given a directory, find all the FITS images in it, and it's subdirectories.
	 * @param directoryString A string containing the root directory to start the search at.
	 * @return A List, containing File object instances, where each item represents a FITS image
	 *        within the directory or it's subdirectories.
	 * @exception IllegalArgumentException Thrown if directoryString is not a string 
	 *            representing a valid directory.
	 * @exception Exception Thrown if listing a directory returns null.
	 * @see #logger
	 */
	public List findFITSFilesInDirectory(String directoryString) throws Exception, IllegalArgumentException
	{
		File directoryFile = null;
		List<File> directoryList = new Vector<File>();
		File fileList[];
		List<File> fitsFileList = new Vector<File>();

		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
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
			// get the directory from the firectory list, and then remove it from the list
			directoryFile = (File)(directoryList.get(0));
			directoryList.remove(directoryFile);
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
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
				if(fileList[i].isDirectory())
				{
					// add the directory to the list of directories to search
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":findFITSFilesInDirectory:Adding directory:"+fileList[i]+
						" to search directory list.");
					directoryList.add(fileList[i]);
				}
				else
				{
					// is it a fits file?
					if(fileList[i].toString().endsWith(".fits"))
					{
						logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
							":findFITSFilesInDirectory:Adding FITS image:"+fileList[i]+
							" to results list.");
						fitsFileList.add(fileList[i]);	
					}
					else
					{
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
							":findFITSFilesInDirectory:File:"+fileList[i]+
							" not a FITS image.");
					}
				}
			}// end for over files in that directory
		}// end while directories in the list
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			":findFITSFilesInDirectory:directory:"+directoryString+" contained "+fitsFileList.size()+
			" FITS images.");
		return fitsFileList;
	}

	/**
	 * Method to add the FITS headers contained in ioiFitsHeader to the specified List of FITS images.
	 * @param fitsImageList A List, containing File object instances, where each item represents a FITS image
	 *        within the directory or it's subdirectories.
	 * @exception FitsHeaderException Thrown if the writeFitsHeader method fails.
	 * @see #logger
	 * @see #fitsHeader
	 */
	public void addFitsHeadersToFitsImages(List fitsImageList) throws FitsHeaderException
	{
		File fitsFile = null;
		//boolean fitsFilenameAnnotate;

		//fitsFilenameAnnotate = status.getPropertyBoolean("ioi.file.fits.annotate");
		//if(fitsFilenameAnnotate)
		//{
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":addFitsHeadersToFitsImages:Adding "+fitsHeader.getKeywordValueCount()+
			   " headers to "+fitsImageList.size()+" FITS images.");
		for(int fitsImageIndex=0;fitsImageIndex < fitsImageList.size(); fitsImageIndex++)
		{
			fitsFile = (File)(fitsImageList.get(fitsImageIndex));
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				   ":addFitsHeadersToFitsImages:Adding headers to "+fitsFile.toString());
			fitsHeader.writeFitsHeader(fitsFile.toString());
		}
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":addFitsHeadersToFitsImages:Finished.");
		//}
		//else
		//{
		//logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
		//	   ":addFitsHeadersToFitsImages:ioi.file.fits.annotate is failse:"+
		//	   "Not annotating FITS headers.");
		//}
	}

	/**
	 * Main program entry point. Parse arguments, initialise logging, run test methods.
	 */
	public static void main(String[] args)
	{
		TestAddFitsHeadersToFitsImages taf = null;

		taf = new TestAddFitsHeadersToFitsImages();
		taf.parseArgs(args);
		if((taf.fitsHeaderFilename == null)||(taf.directory == null))
		{
			System.out.println("TestAddFitsHeadersToFitsImages:No FITS header filename or directory specified.");
			taf.help();
			System.exit(1);

		}
		try
		{
			taf.init();
		}
		catch(Exception e)
		{
			System.err.println("TestAddFitsHeadersToFitsImages:init failed:"+e);
			e.printStackTrace(System.err);
			System.exit(1);
		}
		try
		{
			taf.run();
		}
		catch(Exception e)
		{
			System.err.println("TestAddFitsHeadersToFitsImages:run failed:"+e);
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
