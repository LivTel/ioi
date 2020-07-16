// SendMultrunCommand.java 
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
 * This class sends a MULTRUN to IO:I. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class SendMultrunCommand
{
	/**
	 * The default port number to send ISS commands to.
	 */
	static final int DEFAULT_IOI_PORT_NUMBER = 8472;;
	/**
	 * The default port number for the fake ISS server, to receive ISS commands from IO:I.
	 */
	static final int DEFAULT_ISS_SERVER_PORT_NUMBER = 7383;
	/**
	 * The default port number for the fake BSS server, to receive BSS commands from IO:I.
	 */
	static final int DEFAULT_BSS_SERVER_PORT_NUMBER = 6683;
	/**
	 * The ip address of the IOI:I controlo computer, to send the CONFIG command to.
	 */
	private InetAddress address = null;
	/**
	 * The port number to send the MULTRUN commands to on the IO:I control computer.
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
	 * Exposure length. Defaults to zero, which should cause MULTRUN to return an error.
	 */
	private int exposureLength = 0;
	/**
	 * Number of exposures for the MULTRUN to take. 
	 * Defaults to zero, which should cause MULTRUN to return an error.
	 */
	private int exposureCount = 0;
	/**
	 * Whether this MULTRUN has standard flags set (is of a standard source). Defaults to false.
	 */
	private boolean standard = false;
	/**
	 * Whether to send the generated filenames to the DpRt. Defaults to false.
	 */
	private boolean pipelineProcess = false;
	/**
	 * Logger to log to.
	 */
	Logger logger = null;

	/**
	 * Constructor.
	 * @see #logger
	 */
	public SendMultrunCommand()
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
		//copyLogHandlers(logger,LogManager.getLogger("ngat.ioi.test.SendMultrunCommand"),null,Logging.ALL);
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
	 * This routine creates a MULTRUN command. 
	 * @return An instance of MULTRUN.
	 * @see #exposureLength
	 * @see #exposureCount
	 * @see #standard
	 * @see #pipelineProcess
	 */
	private MULTRUN createMultrun()
	{
		String string = null;
		MULTRUN multrunCommand = null;

		multrunCommand = new MULTRUN("SendMultrunCommand");
		multrunCommand.setExposureTime(exposureLength);
		multrunCommand.setNumberExposures(exposureCount);
		multrunCommand.setStandard(standard);
		multrunCommand.setPipelineProcess(pipelineProcess);
		return multrunCommand;
	}

	/**
	 * This is the run routine. It creates a MULTRUN object and sends it to the using a 
	 * SicfTCPClientConnectionThread, and awaiting the thread termination to signify message
	 * completion. 
	 * @return The routine returns true if the command succeeded, false if it failed.
	 * @exception Exception Thrown if an exception occurs.
	 * @see #createMultrun
	 * @see SicfTCPClientConnectionThread
	 * @see #getThreadResult
	 * @see #address
	 * @see #ioiPortNumber
	 */
	private boolean run() throws Exception
	{
		ISS_TO_INST issCommand = null;
		SicfTCPClientConnectionThread thread = null;
		boolean retval;

		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Starting.");
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":run:Creating MULTRUN command.");
		issCommand = (ISS_TO_INST)(createMultrun());
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":run:Starting client connection thread to send MULTRUN to the robotic control software.");
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
			System.err.println("Acknowledge was null");
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
			//System.out.println("Done was null");
			logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":getThreadResult:Done was null.");
			retval = false;
		}
		else
		{
			if(thread.getDone().getSuccessful())
			{
				//System.out.println("Done was successful");
				logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					   ":getThreadResult:Done was successful.");
				if(thread.getDone() instanceof EXPOSE_DONE)
				{
					//System.out.println("\tFilename:"+
					//	((EXPOSE_DONE)(thread.getDone())).getFilename());
					logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
						   ":getThreadResult:Filename:"+
						   ((EXPOSE_DONE)(thread.getDone())).getFilename());
				}
				retval = true;
			}
			else
			{
				//System.out.println("Done returned error("+thread.getDone().getErrorNum()+
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
	 * This routine parses arguments passed into SendMultrunCommand.
	 * @see #exposureLength
	 * @see #exposureCount
	 * @see #standard
	 * @see #pipelineProcess
	 * @see #ioiPortNumber
	 * @see #issServerPortNumber
	 * @see #bssServerPortNumber
	 * @see #address
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
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
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
			else if(args[i].equals("-l")||args[i].equals("-exposureLength"))
			{
				if((i+1)< args.length)
				{
					exposureLength = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-exposureLength requires an argument.");
			}
			else if(args[i].equals("-n")||args[i].equals("-exposureCount"))
			{
				if((i+1)< args.length)
				{
					exposureCount = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-exposureCount requires an argument.");
			}
			else if(args[i].equals("-pp")||args[i].equals("-pipelineProcess"))
			{
					pipelineProcess = true;
			}
			else if(args[i].equals("-t")||args[i].equals("-standard"))
			{
				standard = true;
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
		System.out.println("\t-[ioi|ioiport] <port number> - Robotic software server port to send MULTRUN command to.");
		System.out.println("\t-[ip]|[address] <address> - Address to send commands to.");
		System.out.println("\t-iss[serverport] <port number> - ISS fake server Port for IO:I to send commands back.");
		System.out.println("\t-[l]|[exposureLength] <time in millis> - Specify exposure length.");
		System.out.println("\t-[n]|[exposureCount] <number> - Specify number of exposures.");
		System.out.println("\t-pp|-pipelineProcess - Send frames to pipeline process.");
		System.out.println("\t-[t]|[standard] - Set standard parameters.");
		System.out.println("The default ISS server port is "+DEFAULT_ISS_SERVER_PORT_NUMBER+".");
		System.out.println("The default BSS server port is "+DEFAULT_BSS_SERVER_PORT_NUMBER+".");
		System.out.println("The default IO:I port is "+DEFAULT_IOI_PORT_NUMBER+".");
	}

	/**
	 * The main routine, called when SendMultrunCommand is executed. This initialises the object, parses
	 * it's arguments, opens the filename, runs the run routine, and then closes the file.
	 * @see #parseArgs
	 * @see #init
	 * @see #run
	 */
	public static void main(String[] args)
	{
		boolean retval;
		SendMultrunCommand smc = new SendMultrunCommand();

		smc.parseArgs(args);
		smc.init();
		if(smc.address == null)
		{
			System.err.println("No O Address Specified.");
			smc.help();
			System.exit(1);
		}
		try
		{
			retval = smc.run();
		}
		catch (Exception e)
		{
			retval = false;
			System.err.println("run failed:"+e);

		}
		// shut down started fake servers
		if(smc.issServer != null)
			smc.issServer.close();
		if(smc.bssServer != null)
			smc.bssServer.close();
		if(retval)
			System.exit(0);
		else
			System.exit(2);
	}
}
//
// $Log: SendMultrunCommand.java,v $
// Revision 1.1  2011/11/23 10:59:38  cjm
// Initial revision
//
//
