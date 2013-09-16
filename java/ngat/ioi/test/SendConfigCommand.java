// SendConfigCommand.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * This class send a IR camera configuration to IO:I. The configuration can be randonly generated 
 * (using a filter wheel database to get sensible names) or specified.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SendConfigCommand
{
	/**
	 * The default port number to send ISS commands to.
	 */
	static final int DEFAULT_IOI_PORT_NUMBER = 8472;
	/**
	 * The default port number for the fake ISS server, to get commands from the IO:I from.
	 */
	static final int DEFAULT_ISS_SERVER_PORT_NUMBER = 7383;
	/**
	 * The default port number for the fake BSS server, to get commands from the IO:I from.
	 */
	static final int DEFAULT_BSS_SERVER_PORT_NUMBER = 6683;
	/**
	 * The filename of a current filter wheel property file.
	 */
	private String filename = null;
	/**
	 * A property list of filter wheel properties.
	 */
	private NGATProperties filterWheelProperties = null;
	/**
	 * The ip address of the IOI:I controlo computer, to send the CONFIG command to.
	 */
	private InetAddress address = null;
	/**
	 * The port number to send the CONFIG commands to on the IO:I control computer.
	 */
	private int ioiPortNumber = DEFAULT_IOI_PORT_NUMBER;
	/**
	 * The port number for the fake ISS server, to recieve commands from IO:I.
	 */
	private int issServerPortNumber = DEFAULT_ISS_SERVER_PORT_NUMBER;
	/**
	 * The port number for the fake BSS server, to recieve commands from IO:I.
	 */
	private int bssServerPortNumber = DEFAULT_BSS_SERVER_PORT_NUMBER;
	/**
	 * The fake ISS server class that listens for connections from IO:I.
	 */
	private SicfTCPServer issServer = null;
	/**
	 * Reference to the fake BSS server thread that listens for connections from IO:.
	 */
	private BSSServer bssServer = null;
	/**
	 * Filter wheel string. Defaults to null.
	 */
	private String filterString = null;
	/**
	 * X Binning of configuration. Defaults to 1.
	 */
	private int xBin = 1;
	/**
	 * Y Binning of configuration. Defaults to 1.
	 */
	private int yBin = 1;
	/**
	 * Whether exposures taken using this configuration, should do a calibration
	 * before the exposure.
	 */
	private boolean calibrateBefore = false;
	/**
	 * Whether exposures taken using this configuration, should do a calibration
	 * after the exposure.
	 */
	private boolean calibrateAfter = false;
	/**
	 * Logger to log to.
	 */
	Logger logger = null;

	/**
	 * Constructor.
	 * @see #logger
	 */
	public SendConfigCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * This is the initialisation routine.  This calls initialiseLogger to setup the loggers.
	 * This calls startISSServer and startBSSServer to start
	 * fake ISS and BSS servers, to handle commands coming back from the robotic instrument control software.
	 * @see #initialiseLogger
	 * @see #startISSServer
	 * @see #startBSSServer
	 */
	private void init()
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":init:Initialing loggers.");
		initialiseLogger();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":init:Start ISS Server.");
		startISSServer();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":init:Start BSS Server.");
		startBSSServer();
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
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.BSSServer"),null,Logging.ALL);
		copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.BSSServerConnectionThread"),null,
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
	 * This starts the issServer thread.
	 * @see #issServer	 
	 * @see #issServerPortNumber
	 * @see ngat.ioi.test.SicfTCPServer
	 */
	private void startISSServer()
	{
		issServer = new SicfTCPServer(this.getClass().getName(),issServerPortNumber);
		issServer.setController(this);
		issServer.start();		
	}

	/**
	 * This starts the bssServer thread.
	 * @see #bssServer
	 * @see #bssServerPortNumber
	 * @see ngat.ioi.test.BSSServer
	 */
	private void startBSSServer()
	{
		bssServer = new BSSServer(this.getClass().getName(),bssServerPortNumber);
		bssServer.start();		
	}

	/**
	 * Routine to load the current filter wheel properties from filename
	 * into filterWheelProperties.
	 * @exception FileNotFoundException Thrown if the load failed.
	 * @exception IOException Thrown if the load failed.
	 * @see #filename
	 * @see #filterWheelProperties
	 */
	private void loadCurrentFilterProperties() throws FileNotFoundException, IOException
	{
		filterWheelProperties = new NGATProperties();
		filterWheelProperties.load(filename);
	}

	/**
	 * Routine to select and return a random filter from the filter wheel,
	 * using the loaded filter property database.
	 * @return A string, a filter type, that is present in the wheel according to
	 * 	the loaded properties.
	 * @exception NGATPropertyException Thrown if a property retrieve fails.
	 * @see #filterWheelProperties
	 */
	private String selectRandomFilter() throws NGATPropertyException
	{
		int positionCount,position,wheel;
		Random random = null;
		String filterType;

		wheel = 0; // IO:I has 1 wheel.
		positionCount = filterWheelProperties.getInt("filterwheel."+wheel+".count");
		random = new Random();
		position = random.nextInt(positionCount);
		filterType = (String)(filterWheelProperties.get("filterwheel."+wheel+"."+position+".type"));
		return filterType;
	}

	/**
	 * This routine creates a CONFIG command. This object
	 * has a IRCamConfig phase2 object with it, this is created and it's fields initialised.
	 * @return An instance of CONFIG.
	 * @see #filterString
	 * @see #xBin
	 * @see #yBin
	 * @see #calibrateBefore
	 * @see #calibrateAfter
	 */
	private CONFIG createConfig()
	{
		String string = null;
		CONFIG configCommand = null;
		IRCamConfig irCamConfig = null;
		IRCamDetector detector = null;

		configCommand = new CONFIG("Object Id");
		irCamConfig = new IRCamConfig("Object Id");
	// detector for config
		detector = new IRCamDetector();
		detector.setXBin(xBin);
		detector.setYBin(yBin);
		irCamConfig.setDetector(0,detector);
	// filterWheel
		irCamConfig.setFilterWheel(filterString);
	// InstrumentConfig fields.
		irCamConfig.setCalibrateBefore(calibrateBefore);
		irCamConfig.setCalibrateAfter(calibrateAfter);
	// CONFIG command fields
		configCommand.setConfig(irCamConfig);
		return configCommand;
	}

	/**
	 * This is the run routine. It creates a CONFIG object and sends it to the using a 
	 * SicfTCPClientConnectionThread, and awaiting the thread termination to signify message
	 * completion. 
	 * @return The routine returns true if the command succeeded, false if it failed.
	 * @exception Exception Thrown if an exception occurs.
	 * @see #loadCurrentFilterProperties
	 * @see #selectRandomFilter
	 * @see #createConfig
	 * @see SicfTCPClientConnectionThread
	 * @see #getThreadResult
	 */
	private boolean run() throws Exception
	{
		ISS_TO_INST issCommand = null;
		SicfTCPClientConnectionThread thread = null;
		boolean retval;

		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Starting.");
		if(filename != null)
		{
			loadCurrentFilterProperties();
			if(filterString == null)
				filterString = selectRandomFilter();
		}
		else
		{
			if(filterString == null)
				System.err.println("Program should fail:No lower filter specified.");
		}
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Creating CONFIG command.");
		issCommand = (ISS_TO_INST)(createConfig());
		if(issCommand instanceof CONFIG)
		{
			CONFIG configCommand = (CONFIG)issCommand;
			IRCamConfig irCamConfig = (IRCamConfig)(configCommand.getConfig());
			logger.log(Logging.VERBOSITY_VERBOSE,"CONFIG:"+
				   irCamConfig.getFilterWheel()+":"+
				   "calibrate before:"+irCamConfig.getCalibrateBefore()+":"+
				   "calibrate after:"+irCamConfig.getCalibrateAfter()+":"+
				   irCamConfig.getDetector(0).getXBin()+":"+
				   irCamConfig.getDetector(0).getYBin()+".");
			//System.err.println("CONFIG:"+
			//	irCamConfig.getFilterWheel()+":"+
			//	"calibrate before:"+irCamConfig.getCalibrateBefore()+":"+
			//	"calibrate after:"+irCamConfig.getCalibrateAfter()+":"+
			//	irCamConfig.getDetector(0).getXBin()+":"+
			//	irCamConfig.getDetector(0).getYBin()+".");
		}
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":run:Starting client connection thread to send CONFIG to the robotic control software.");
		thread = new SicfTCPClientConnectionThread(address,ioiPortNumber,issCommand);
		thread.start();
		while(thread.isAlive())
		{
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":run:Waiting for client connection thread to finish.");
			try
			{
				thread.join();
			}
			catch(InterruptedException e)
			{
				System.err.println("run:join interrupted:"+e);
			}
		}// end while isAlive
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":run:Getting result of command.");
		retval = getThreadResult(thread);
		return retval;
	}

	/**
	 * Find out the completion status of the thread and print out the final status of some variables.
	 * @param thread The Thread to print some information for.
	 * @return The routine returns true if the thread completed successfully,
	 * 	false if some error occured.
	 */
	private boolean getThreadResult(SicfTCPClientConnectionThread thread)
	{
		boolean retval;

		if(thread.getAcknowledge() == null)
		{
			//System.err.println("Acknowledge was null");
			logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":getThreadResult:Acknowledge was null.");
		}
		else
		{
			//System.err.println("Acknowledge with timeToComplete:"+
			//	thread.getAcknowledge().getTimeToComplete());
			logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":getThreadResult:Acknowledge with timeToComplete:"+
				   thread.getAcknowledge().getTimeToComplete()+".");
		}
		if(thread.getDone() == null)
		{
			//System.err.println("Done was null");
			logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":getThreadResult:Done was null.");
			retval = false;
		}
		else
		{
			if(thread.getDone().getSuccessful())
			{
				//System.err.println("Done was successful");
				logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					   ":getThreadResult:Done was successful.");
				retval = true;
			}
			else
			{
				//System.err.println("Done returned error("+thread.getDone().getErrorNum()+
				//	"): "+thread.getDone().getErrorString());
				logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					   ":getThreadResult:Done returned error("+thread.getDone().getErrorNum()+
					"): "+thread.getDone().getErrorString()+".");
				retval = false;
			}
		}
		return retval;
	}

	/**
	 * This routine parses arguments passed into SendConfigCommand.
	 * @see #filename
	 * @see #ioiPortNumber
	 * @see #issServerPortNumber
	 * @see #bssServerPortNumber
	 * @see #address
	 * @see #filterString
	 * @see #xBin
	 * @see #yBin
	 * @see #calibrateBefore
	 * @see #calibrateAfter
	 * @see #help
	 */
	private void parseArgs(String[] args)
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-bss")||args[i].equals("-bssserverport"))
			{
				if((i+1)< args.length)
				{
					bssServerPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-bssserverport requires a port number");
			}
			else if(args[i].equals("-ca")||args[i].equals("-calibrate_after"))
			{
				calibrateAfter = true;
			}
			else if(args[i].equals("-cb")||args[i].equals("-calibrate_before"))
			{
				calibrateBefore = true;
			}
			else if(args[i].equals("-f")||args[i].equals("-filter"))
			{
				if((i+1)< args.length)
				{
					filterString = args[i+1];
					i++;
				}
				else
					System.err.println("-filter requires a filter name");
			}
			else if(args[i].equals("-ff")||args[i].equals("-filterfile"))
			{
				if((i+1)< args.length)
				{
					filename = new String(args[i+1]);
					i++;
				}
				else
					System.err.println("-filterfilename requires a filename");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-ip")||args[i].equals("-address"))
			{
				if((i+1)< args.length)
				{
					try
					{
						address = InetAddress.getByName(args[i+1]);
					}
					catch(UnknownHostException e)
					{
						System.err.println(this.getClass().getName()+":illegal address:"+
							args[i+1]+":"+e);
					}
					i++;
				}
				else
					System.err.println("-address requires an address");
			}
			else if(args[i].equals("-ioi")||args[i].equals("-ioiport"))
			{
				if((i+1)< args.length)
				{
					ioiPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-ioiport requires a port number");
			}
			else if(args[i].equals("-iss")||args[i].equals("-issserverport"))
			{
				if((i+1)< args.length)
				{
					issServerPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-issserverport requires a port number");
			}
			else if(args[i].equals("-x")||args[i].equals("-xBin"))
			{
				if((i+1)< args.length)
				{
					xBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-xBin requires a valid number.");
			}
			else if(args[i].equals("-y")||args[i].equals("-yBin"))
			{
				if((i+1)< args.length)
				{
					yBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-yBin requires a valid number.");
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
		System.out.println("\t-bss[serverport] <port number> - BSS fake server Port for IO:I to send commands back.");
		System.out.println("\t-[ca|[calibrate_after] - Do a calibration after any exposures.");
		System.out.println("\t-[cb|[calibrate_before] - Do a calibration before any exposures.");
		System.out.println("\t-f[ilter] <filter type name> - Specify filter type.");
		System.out.println("\t-[ff|filterfile] <filename> - filter wheel filename.");
		System.out.println("\t-[ip]|[address] <address> - Address to send commands to.");
		System.out.println("\t-iss[serverport] <port number> - ISS fake server Port for IO:I to send commands back.");
		System.out.println("\t-[ioi|ioiport] <port number> - Port to send CONFIG command to.");
		System.out.println("\t-x[Bin] <binning factor> - X readout binning factor the CCD.");
		System.out.println("\t-y[Bin] <binning factor> - Y readout binning factor the CCD.");
		System.out.println("The default ISS server port is "+DEFAULT_ISS_SERVER_PORT_NUMBER+".");
		System.out.println("The default BSS server port is "+DEFAULT_BSS_SERVER_PORT_NUMBER+".");
		System.out.println("The default IO:I port is "+DEFAULT_IOI_PORT_NUMBER+".");
		System.out.println("The filters can be specified, otherwise if the filename is specified\n"+
			"the filters are selected randomly from that, otherwise 'null' is sent as a filter\n"+
			"and an error should occur.");
	}

	/**
	 * The main routine, called when SendConfigCommand is executed. This initialises the object, parses
	 * it's arguments, opens the filename, runs the run routine, and then closes the file.
	 * @see #parseArgs
	 * @see #init
	 * @see #run
	 */
	public static void main(String[] args)
	{
		boolean retval;
		SendConfigCommand scc = new SendConfigCommand();

		scc.parseArgs(args);
		scc.init();
		if(scc.address == null)
		{
			System.err.println("No IO:I Address Specified.");
			scc.help();
			System.exit(1);
		}
		try
		{
			retval = scc.run();
		}
		catch (Exception e)
		{
			retval = false;
			System.err.println("run failed:"+e);

		}
		// shut down started fake servers
		if(scc.issServer != null)
			scc.issServer.close();
		if(scc.bssServer != null)
			scc.bssServer.close();
		if(retval)
			System.exit(0);
		else
			System.exit(2);
	}
}
