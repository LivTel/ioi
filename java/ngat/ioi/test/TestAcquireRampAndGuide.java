// TestAcquireRampAndGuide.java
// $Header$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.net.*;
import ngat.util.logging.*;

/**
 * Test program to drive an instance of AcquireRampAndGuide. This test program can take a science ramp of the whole
 * array, whilst simultaneously reading out a sub-window as a guide ramp.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class TestAcquireRampAndGuide implements AcquireRampAndGuideCallbackInterface
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * A TelnetConnection instance holding connection info to the IDL socket server.
	 */
	TelnetConnection idlTelnetConnection = null;
	/**
	 * The initialisation level to initialise the Sidecar. This should normally be 2.
	 */
	int initializeLevel = 2;
	/**
	 * The AcquireRampAndGuide instance we are going to use.
	 */
	AcquireRampAndGuide acquireRampAndGuide = null;
	/**
	 * Logger to log to.
	 */
	Logger logger = null;

	/**
	 * Constructor.
	 * @see #logger
	 */
	public TestAcquireRampAndGuide()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Initialise the sidecar, by opening the telnet connection and sending an Initialize command 
	 * to the IDL Socket Server.
	 * @exception Exception Thrown if the socket open fails, or if the Initialize command fails.
	 * @see #idlTelnetConnection
	 * @see #initializeLevel
	 * @see #initialiseLogger
	 * @see ngat.net.TelnetConnection
	 * @see ngat.net.TelnetConnection#open
	 * @see ngat.ioi.command.InitializeCommand
	 * @see ngat.ioi.command.InitializeCommand#setTelnetConnection
	 * @see ngat.ioi.command.InitializeCommand#setCommand
	 * @see ngat.ioi.command.InitializeCommand#sendCommand
	 * @see ngat.ioi.command.InitializeCommand#getReplyErrorCode
	 * @see ngat.ioi.command.InitializeCommand#getReplyErrorString
	 */
	protected void initialise() throws Exception
	{
		InitializeCommand initializeCommand = null;

		// initialise logger
		initialiseLogger();
		// open connection to IDL Socket Server
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":initialise:Opening telnet connection.");
		idlTelnetConnection.open();
		// send "Initialize" with level initializeLevel to IDL Socket Server
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":initialise:Sending initialise command.");
		initializeCommand = new InitializeCommand();
		initializeCommand.setTelnetConnection(idlTelnetConnection);
		initializeCommand.setCommand(initializeLevel);
		initializeCommand.sendCommand();
		if(initializeCommand.getReplyErrorCode() != 0)
		{
			throw new Exception(this.getClass().getName()+":initialise:Initialize"+initializeLevel+
					    " failed:"+initializeCommand.getReplyErrorCode()+":"+
					    initializeCommand.getReplyErrorString());
		}
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			   ":initialise:initialising AcquireRampAndGuide instance.");
		// Tell the acquireRampAndGuide instance the (opened) telnet connection to use
		acquireRampAndGuide.setTelnetConnection(idlTelnetConnection);
		// Initialise acquireRampAndGuide. This sets the fowler sampling mode, and configures the sidecar
		acquireRampAndGuide.initialise();
		// setup callbacks
		acquireRampAndGuide.setScienceDataCallback(this);
		acquireRampAndGuide.setGuideDataCallback(this);
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":initialise:Finished.");
	}

	/**
	 * Initialise the logger.
	 * @see #logger
	 * @see #copyLogHandlers
	 */
	protected void initialiseLogger()
	{
		LogHandler handler = null;
		SimpleLogFormatter slf = null;
		BogstanLogFormatter blf = null;

		logger.setLogLevel(Logging.ALL);
		//slf = new SimpleLogFormatter();
		//slf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		blf = new BogstanLogFormatter();
		blf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		handler = new ConsoleLogHandler(blf);
		handler.setLogLevel(Logging.ALL);
		logger.addHandler(handler);
		// copy logger log handlers to other relevant classes
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.AcquireRampAndGuide"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.AcquireRampCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.GetConfigCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.InitializeCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.PingCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.PowerUpASICCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.PowerDownASICCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetFSModeCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetFSParamCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetIdleModeOptionCommand"),null,
				Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetRampParamCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetWindowModeCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.SetWinParamsCommand"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.command.StopAcquisitionCommand"),null,
				Logging.ALL);
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
	 * Data callback from the exposure.
	 * @param dataType Which kind of data is being returned, one of DATA_TYPE_SCIENCE or DATA_TYPE_GUIDE .
	 * @param directory The directory containing the data.
	 */
	public void newData(int dataType,String directory)
	{
		if(dataType == DATA_TYPE_SCIENCE)
			logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":newData:Science:"+directory);
		else if(dataType == DATA_TYPE_GUIDE)
			logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":newData:Guide:"+directory);
	}

	/**
	 * Power down the ASIC. Close the IDL Socket Server.
	 * @see #idlTelnetConnection
	 * @see ngat.ioi.command.PowerDownASICCommand
	 */
	protected void close() throws Exception
	{
		PowerDownASICCommand powerDownASICCommand = null;

		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":close:Starteded.");
		// power down the ASIC
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":close:Powering down ASIC.");
		powerDownASICCommand = new PowerDownASICCommand();
		powerDownASICCommand.setTelnetConnection(idlTelnetConnection);
		powerDownASICCommand.sendCommand();
		if(powerDownASICCommand.getReplyErrorCode() != 0)
		{
			throw new Exception(this.getClass().getName()+":close:PowerDownASIC failed:"+
					    powerDownASICCommand.getReplyErrorCode()+":"+
					    powerDownASICCommand.getReplyErrorString());
		}
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":close:Closing telnet connection.");
		// close the connection to the socket server
		idlTelnetConnection.close();
		logger.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":close:Finished.");
	}

	/**
	 * Parse the command line arguments.
	 * @param args The command line arguments.
	 * @exception UnknownHostException Thrown if idlTelnetConnection.setAddress fails.
	 * @see #idlTelnetConnection
	 * @see #initializeLevel
	 * @see #acquireRampAndGuide
	 * @see ngat.net.TelnetConnection#setAddress
	 * @see ngat.net.TelnetConnection#setPortNumber
	 * @see ngat.ioi.test.AcquireRampAndGuide#setExposureLength
	 * @see ngat.ioi.test.AcquireRampAndGuide#setRootDirectory
	 * @see ngat.ioi.test.AcquireRampAndGuide#setGuideWindow
	 * @see ngat.ioi.test.AcquireRampAndGuide#setRampParamGuide
	 * @see ngat.ioi.test.AcquireRampAndGuide#setRampParamScience
	 */
	protected void parseArguments(String args[]) throws UnknownHostException
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-e")||args[i].equals("-exposure_length"))
			{
				if((i+1)< args.length)
				{
					acquireRampAndGuide.setExposureLength(Float.parseFloat(args[i+1]));
					i++;
				}
				else
				{
					System.err.println("-exposure_length requires a duration in seconds.");
					System.exit(1);
				}
			}
			else if(args[i].equals("-ip")||args[i].equals("-address"))
			{
				if((i+1)< args.length)
				{
					idlTelnetConnection.setAddress(args[i+1]);
					i++;
				}
				else
				{
					System.err.println("-address requires an address");
					System.exit(1);
				}
			}
			/*
			else if(args[i].equals("-fs")||args[i].equals("-fs_mode"))
			{
				if((i+1)< args.length)
				{
					acquireRampAndGuide.setFSMode(SetFSModeCommand.parseMode(args[i+1]));
					i++;
				}
				else
				{
					System.err.println("-fs_mode requires a mode:FOWLER or UP_THE_RAMP.");
					System.exit(1);
				}
			}
			*/
			else if(args[i].equals("-g")||args[i].equals("-guide_window"))
			{
				if((i+4)< args.length)
				{
					acquireRampAndGuide.setGuideWindow(Integer.parseInt(args[i+1]),
									   Integer.parseInt(args[i+2]),
									   Integer.parseInt(args[i+3]),
									   Integer.parseInt(args[i+4]));
					i+=4;
				}
				else
				{
					System.err.println("-guide_window <xStart> <xStop> <yStart> <yStop>");
					System.exit(1);
				}
			}
			else if(args[i].equals("-gp")||args[i].equals("-guide_param"))
			{
				if((i+3)< args.length)
				{
					acquireRampAndGuide.setRampParamGuide(Integer.parseInt(args[i+1]),
									      Integer.parseInt(args[i+2]),
									      Integer.parseInt(args[i+3]));
					i+=3;
				}
				else
				{
					System.err.println("-guide_param <nRead> <nGroup> <nDrop>");
					System.exit(1);
				}
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-il")||args[i].equals("-initialise_level"))
			{
				if((i+1)< args.length)
				{
					initializeLevel = Integer.parseInt(args[i+1]);
					i++;
				}
				else
				{
					System.err.println("-initialise_level requires a port number");
					System.exit(1);
				}
			}
			else if(args[i].equals("-p")||args[i].equals("-port"))
			{
				if((i+1)< args.length)
				{
					idlTelnetConnection.setPortNumber(Integer.parseInt(args[i+1]));
					i++;
				}
				else
				{
					System.err.println("-port requires a port number");
					System.exit(1);
				}
			}
			else if(args[i].equals("-r")||args[i].equals("-root_directory"))
			{
				if((i+1)< args.length)
				{
					acquireRampAndGuide.setRootDirectory(args[i+1]);
					i++;
				}
				else
				{
					System.err.println("-root_directory requires a directory");
					System.exit(1);
				}
			}
			else if(args[i].equals("-s")||args[i].equals("-science_param"))
			{
				if((i+4)< args.length)
				{
					acquireRampAndGuide.setRampParamScience(Integer.parseInt(args[i+1]),
										Integer.parseInt(args[i+2]),
										Integer.parseInt(args[i+3]),
										Integer.parseInt(args[i+4]));
					i+=4;
				}
				else
				{
					System.err.println("-science_param <nReset> <nRead> <nGroup> <nDrop>");
					System.exit(1);
				}
			}
			else
			{
				System.err.println(this.getClass().getName()+":Option not supported:"+args[i]);
				System.exit(1);
			}
		}
	}

	/**
	 * Help function.
	 */
	protected void help()
	{
		System.out.println(this.getClass().getName()+" Help:");
		System.out.println("Options are:");
		System.out.println("\t-e[xposure_length] <secs> - The science exposure length in seconds.");
		System.out.println("\t-p[ort] <port number> - IDL Server Socket port.");
		System.out.println("\t-[ip]|[address] <address> - IDL Server Socket Address.");
		System.out.println("\t-g[uide_window] <xStart> <xStop> <yStart> <yStop>.");
		System.out.println("\t-[guide_param]|[gp] <nRead> <nGroup> <nDrop>.");
		/*System.out.println("\t-[fs]_mode <FOWLER|UP_THE_RAMP> - Set the Fowler sampling mode.");*/
		System.out.println("\t-[il]|[initialise_level] <level> - Initialize command level - should normally be 2.");
		System.out.println("\t-[help] - Print this help.");
		System.out.println("\t-r[oot_directory] <dir> - Set the root directory the data is put into.");
		System.out.println("\t-s[cience_param] <nReset> <nRead> <nGroup> <nDrop>.");
	}

	/**
	 * Main program
	 * <ul>
	 * <li>We initialise the acquireRampAndGuide instance.
	 * <li>
	 * <li>
	 * <li>
	 * <li>
	 * <li>
	 * <li>
	 * </ul>
	 * @param args The command line arguments.
	 * @see #acquireRampAndGuide
	 */
	public static void main(String args[])
	{
		TestAcquireRampAndGuide testAcquireRampAndGuide = null;

		// create test instance
		testAcquireRampAndGuide = new TestAcquireRampAndGuide();
		// create actual AcquireRampAndGuide instance
		testAcquireRampAndGuide.acquireRampAndGuide = new AcquireRampAndGuide();
		// create idlTelnetConnection instance
		testAcquireRampAndGuide.idlTelnetConnection = new TelnetConnection();
		try
		{
			// parse arguments
			testAcquireRampAndGuide.parseArguments(args);
			// open connection to IDL Socket Server and initialise the Sidecar
			testAcquireRampAndGuide.initialise();
			// do exposure
			testAcquireRampAndGuide.logger.log(Logging.VERBOSITY_TERSE,
							   "TestAcquireRampAndGuide:main:Starting exposure.");
			testAcquireRampAndGuide.acquireRampAndGuide.expose();
			// close the connection to the IDL Socket server
			testAcquireRampAndGuide.close();
		}
		catch(Exception e)
		{
			System.err.println("TestAcquireRampAndGuide failed:"+e);
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}
}
//
// $Log$
//
