// SendConfigCommand.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;

import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.util.*;

/**
 * This class send a IR camera configuration to IO:I. The configuration can be randonly generated 
 * (using a filter wheel database to get sensible names) or specified.
 * @author Chris Mottram
 * @version $Revision: 1.3 $
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
	static final int DEFAULT_SERVER_PORT_NUMBER = 7383;
	/**
	 * The filename of a current filter wheel property file.
	 */
	private String filename = null;
	/**
	 * A property list of filter wheel properties.
	 */
	private NGATProperties filterWheelProperties = null;
	/**
	 * The ip address to send the messages read from file to, this should be the machine the IO:I is on.
	 */
	private InetAddress address = null;
	/**
	 * The port number to send commands from the file to the IO:I.
	 */
	private int ioiPortNumber = DEFAULT_IOI_PORT_NUMBER;
	/**
	 * The port number for the fake ISS server, to recieve commands from the IO:I.
	 */
	private int serverPortNumber = DEFAULT_SERVER_PORT_NUMBER;
	/**
	 * The fake ISS server class that listens for connections from IO:I.
	 */
	private SicfTCPServer server = null;
	/**
	 * The stream to write error messages to - defaults to System.err.
	 */
	private PrintStream errorStream = System.err;
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
	 * This is the initialisation routine. This starts the server thread.
	 */
	private void init()
	{
		server = new SicfTCPServer(this.getClass().getName(),serverPortNumber);
		server.setController(this);
		server.start();
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
		issCommand = (ISS_TO_INST)(createConfig());
		if(issCommand instanceof CONFIG)
		{
			CONFIG configCommand = (CONFIG)issCommand;
			IRCamConfig irCamConfig = (IRCamConfig)(configCommand.getConfig());
			System.err.println("CONFIG:"+
				irCamConfig.getFilterWheel()+":"+
				"calibrate before:"+irCamConfig.getCalibrateBefore()+":"+
				"calibrate after:"+irCamConfig.getCalibrateAfter()+":"+
				irCamConfig.getDetector(0).getXBin()+":"+
				irCamConfig.getDetector(0).getYBin()+".");
		}
		thread = new SicfTCPClientConnectionThread(address,ioiPortNumber,issCommand);
		thread.start();
		while(thread.isAlive())
		{
			try
			{
				thread.join();
			}
			catch(InterruptedException e)
			{
				System.err.println("run:join interrupted:"+e);
			}
		}// end while isAlive
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
			System.err.println("Acknowledge was null");
		else
			System.err.println("Acknowledge with timeToComplete:"+
				thread.getAcknowledge().getTimeToComplete());
		if(thread.getDone() == null)
		{
			System.err.println("Done was null");
			retval = false;
		}
		else
		{
			if(thread.getDone().getSuccessful())
			{
				System.err.println("Done was successful");
				retval = true;
			}
			else
			{
				System.err.println("Done returned error("+thread.getDone().getErrorNum()+
					"): "+thread.getDone().getErrorString());
				retval = false;
			}
		}
		return retval;
	}

	/**
	 * This routine parses arguments passed into SendConfigCommand.
	 * @see #filename
	 * @see #ioiPortNumber
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
			if(args[i].equals("-ca")||args[i].equals("-calibrate_after"))
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
					errorStream.println("-filter requires a filter name");
			}
			else if(args[i].equals("-ff")||args[i].equals("-filterfile"))
			{
				if((i+1)< args.length)
				{
					filename = new String(args[i+1]);
					i++;
				}
				else
					errorStream.println("-filterfilename requires a filename");
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
					errorStream.println("-address requires an address");
			}
			else if(args[i].equals("-ioi")||args[i].equals("-ioiport"))
			{
				if((i+1)< args.length)
				{
					ioiPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-ioiport requires a port number");
			}
			else if(args[i].equals("-s")||args[i].equals("-serverport"))
			{
				if((i+1)< args.length)
				{
					serverPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-serverport requires a port number");
			}
			else if(args[i].equals("-x")||args[i].equals("-xBin"))
			{
				if((i+1)< args.length)
				{
					xBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-xBin requires a valid number.");
			}
			else if(args[i].equals("-y")||args[i].equals("-yBin"))
			{
				if((i+1)< args.length)
				{
					yBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-yBin requires a valid number.");
			}
			else
				System.out.println(this.getClass().getName()+":Option not supported:"+args[i]);
		}
	}

	/**
	 * Help message routine.
	 */
	private void help()
	{
		System.out.println(this.getClass().getName()+" Help:");
		System.out.println("Options are:");
		System.out.println("\t-[ca|[calibrate_after] - Do a calibration after any exposures.");
		System.out.println("\t-[cb|[calibrate_before] - Do a calibration before any exposures.");
		System.out.println("\t-f[ilter] <filter type name> - Specify filter type.");
		System.out.println("\t-[ff|filterfile] <filename> - filter wheel filename.");
		System.out.println("\t-[ip]|[address] <address> - Address to send commands to.");
		System.out.println("\t-s[erverport] <port number> - ISS fake server Port for IO:I to send commands back.");
		System.out.println("\t-[ioi|ioiport] <port number> - Port to send CONFIG command to.");
		System.out.println("\t-x[Bin] <binning factor> - X readout binning factor the CCD.");
		System.out.println("\t-y[Bin] <binning factor> - Y readout binning factor the CCD.");
		System.out.println("The default server port is "+DEFAULT_SERVER_PORT_NUMBER+".");
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
		SendConfigCommand sicf = new SendConfigCommand();

		sicf.parseArgs(args);
		sicf.init();
		if(sicf.address == null)
		{
			System.err.println("No IO:I Address Specified.");
			sicf.help();
			System.exit(1);
		}
		try
		{
			retval = sicf.run();
		}
		catch (Exception e)
		{
			retval = false;
			System.err.println("run failed:"+e);

		}
		if(retval)
			System.exit(0);
		else
			System.exit(2);
	}
}
