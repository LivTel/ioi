// TestIDLSocketServer.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import ngat.util.*;

/**
 * This class sets up a server socket to emulate the IO:I IDL Socket Server. This allows us to test the
 * IO:I robotic software without the real IDL Socket Server (and Jade2 and sidecar) running.
 * @author Chris Mottram
 * @version $Revision: 1.3 $
 */
public class TestIDLSocketServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The default port number.
	 */
	public final static int DEFAULT_PORT_NUMBER = 5000;
	/**
	 * Directory postpended to the root directory, depending on whether AcquireRamp is in fowler sampling
	 * or read up the ramp mode.
	 */
	public final static String FS_MODE_DIRECTORY_FOWLER = new String("FSRamp");
	/**
	 * Directory postpended to the root directory, depending on whether AcquireRamp is in fowler sampling
	 * or read up the ramp mode.
	 */
	public final static String FS_MODE_DIRECTORY_UP_THE_RAMP = new String("UpTheRamp");
	/**
	 * The socket port to run the server on.
	 * @see #DEFAULT_PORT_NUMBER
	 */
	protected int portNumber = DEFAULT_PORT_NUMBER;
	/**
	 * The server socket used to accept connections from the clients.
	 */
	protected ServerSocket serverSocket = null;
	/**
	 * Boolean used to terminate the loop accepting connections in startServer.
	 * Should be false whilst running, and set to true when terminating the program.
	 */
	protected boolean terminateServer = false;
	/**
	 * Variable keeping track of the Fowler Sampling mode. Should be 0 for RuR, and 1 for Fowler Sampling.
	 * Set using SETFSMODE command and returned as part of GetConfig, so the MULTRUNImplementation works.
	 */
	protected int bFS = 1;
	/**
	 * The calculated exposure length, the amount of time in milliseconds an AcquireRamp is meant to take.
	 */
	protected int exposureLength = 0;
	/**
	 * A boolean, set to true when an AcquireRamp command is active, and false when it is not.
	 * Used to return the correct errorCode from a Ping command.
	 */
	protected boolean acquiringRamp = false;
	/**
	 * Whether to abort the ramp currently being acquired.
	 */
	protected boolean abort = false;
	/**
	 * Root data directory where AcquireRamp generated pathnames start from.
	 */
	protected String rootDataDirectory = new String("/home/dev/tmp/data/H2RG-C001-ASIC-LT1");

	/**
	 * Method to start the server socket, and to keep accepting conenctions, and
	 * spawning instances of TestIDLSocketServerConnectionThread, until terminateServer is set to true.
	 * @exception IOException Can be thrown when the server socket is created.
	 * @see #terminateServer
	 */
	public void startServer() throws IOException
	{
		Socket clientSocket = null;

		System.out.println(this.getClass().getName()+":startServer:Started.");
		System.out.println(this.getClass().getName()+":startServer:Creating server socket on port:"+
				   portNumber);
		serverSocket = new ServerSocket(portNumber); 
		while (terminateServer == false)
		{
			System.out.println(this.getClass().getName()+":startServer:Waiting for a socket connection.");
			try
			{
				clientSocket = serverSocket.accept();
			}
			catch(IOException e)
			{
				System.err.println(this.getClass().getName()+
						   ":startServer:server socket accept failed:"+e);
				e.printStackTrace();
				clientSocket = null;
			}
			if(clientSocket != null)
			{
				System.out.println(this.getClass().getName()+
						   ":startServer:Spawning a connection thread.");
				TestIDLSocketServerConnectionThread tissct = new TestIDLSocketServerConnectionThread();
				tissct.setServerConnectionSocket(clientSocket);
				tissct.setParent(this);
				tissct.start();
			}
		}
		System.out.println(this.getClass().getName()+":startServer:Finished.");
	}

	/**
	 * Method to stop the server socket. We set terminateServer to true, and then close the server socket.
	 * @exception IOException This can be thrown  when the socket closes.
	 * @see #terminateServer
	 * @see #serverSocket
	 */
	public void stopServer() throws IOException
	{
		terminateServer = true;
		serverSocket.close();
	}

	/**
	 * Set the fowler sampling mode.
	 * @param i Should be 1 for fowler sampling, and 0 for Read Up the Ramp.
	 * @see #bFS
	 */
	public void setFS(int i)
	{
		bFS = i;
	}

	/**
	 * Get the current value of the cached fowler sampling mode.
	 * @return An integer specifying the fowler sampling mode, 1 for fowler sampling, and 0 for Read Up the Ramp.
	 * @see #bFS
	 */
	public int getFS()
	{
		return bFS;
	}

	/**
	 * Set the exposure length.
	 * @param i The exposure length in milliseconds
	 * @see #exposureLength
	 */
	public void setExposureLength(int i)
	{
		exposureLength = i;
	}

	/**
	 * Get the current exposure length.
	 * @return An integer specifying the exposure length, in milliseconds.
	 * @see #exposureLength
	 */
	public int getExposureLength()
	{
		return exposureLength;
	}

	/**
	 * Set the flag that determines whether to abort the currently acquiring ramp or not.
	 * @param b A boolean, true if the ramp is to be aborted, false is not.
	 * @see #abort
	 */
	public void setAbort(boolean b)
	{
		abort = b;
	}

	/**
	 * Get the flag that determines whether to abort the currently acquiring ramp or not.
	 * @return A boolean, true if the ramp is to be aborted, false is not.
	 * @see #abort
	 */
	public boolean getAbort()
	{
		return abort;
	}

	/**
	 * Set the flag that determines when an AcquireRamp command is active.
	 * This is used to return the correct errorCode from a Ping command.
	 * @param b A boolean, true when an AcquireRamp command is active, and false when it is not.
	 * @see #acquiringRamp
	 */
	public void setAcquiringRamp(boolean b)
	{
		acquiringRamp = b;
	}

	/**
	 * Get the flag that determines when an AcquireRamp command is active.
	 * This is used to return the correct errorCode from a Ping command.
	 * @return A boolean, true when an AcquireRamp command is active, and false when it is not.
	 * @see #acquiringRamp
	 */
	public boolean getAcquiringRamp()
	{
		return acquiringRamp;
	}

	/**
	 * Method to set the root data directory AcquireRamp uses as a base of the FITS filenames it generates.
	 * @param s A string representing a directory.
	 * @see #rootDataDirectory
	 */
	public void setRootDataDirectory(String s)
	{
		rootDataDirectory = s;
	}

	/**
	 * Method to get the root data directory AcquireRamp uses as a base of the FITS filenames it generates.
	 * @return A string representing a directory.
	 * @see #rootDataDirectory
	 */
	public String getRootDataDirectory()
	{
		return rootDataDirectory;
	}

	/**
	 * This methods parses command line arguments.
	 * @see #help
	 * @see #portNumber
	 * @see #rootDataDirectory
	 */
	private void parseArgs(String[] args)
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-d")||args[i].equals("-directory"))
			{
				if((i+1)< args.length)
				{
					rootDataDirectory = args[i+1];
					i++;
				}
				else
					System.err.println("-directory requires a string parameter");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-p")||args[i].equals("-port"))
			{
				if((i+1)< args.length)
				{
					portNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-port requires a port number");
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
		System.out.println("\t-p[ort] <number> - The port number to run the server on.");
		System.out.println("\t-d[irectory] <string> - The root data directory AcquireRamp creates new directories in.");
		System.out.println("\t-h[elp] - Print this help message.");
	}

	/**
	 * The main method, called when TestIDLSocketServer is executed. This initialises the object, parses
	 * it's arguments, .
	 * @see #parseArgs
	 * @see #startServer
	 */
	public static void main(String[] args)
	{
		TestIDLSocketServer tiss = null;

		tiss = new TestIDLSocketServer();
		try
		{
			tiss.parseArgs(args);
			tiss.startServer();
		}
		catch (Exception e)
		{
			System.err.println("ngat.ioi.test.TestIDLSocketServer:main:"+e);
			e.printStackTrace();
		}
		System.exit(0);
	}

	/**
	 * This class handles connections made to the TestIDLSocketServer. A new thread running an
	 * instance of this class to spawned for each connection made.
	 */
	public class TestIDLSocketServerConnectionThread extends Thread
	{
		protected TestIDLSocketServer testIDLSocketServer = null;

		protected Socket connectionSocket = null;

		public TestIDLSocketServerConnectionThread()
		{
			super();
		}

		public void setServerConnectionSocket(Socket socket)
		{
			connectionSocket = socket;	
		}

		public void setParent(TestIDLSocketServer tiss)
		{
			testIDLSocketServer = tiss;
		}

		/**
		 * This method:
		 * <ul>
		 * <li>Wraps the connectionSocket's input stream into a buffered reader.
		 * <li>Wraps the connectionSocket's output stream into a buffered writer.
		 * <li>Enters a loop:
		 *     <ul>
		 *     <li>Reads a (command) string from the input.
		 *     <li>Calls parseCommandLine with the input, which returns a reply
		 *     <li>
		 *     </ul>
		 * </ul>
		 * @see #connectionSocket
		 */
		public void run()
		{
			BufferedReader input;
			BufferedWriter output;
			String commandString = null;
			String replyString = null;
			boolean done;

			System.out.println(this.getClass().getName()+":run:Start.");
			try
			{
				System.out.println(this.getClass().getName()+
						   ":run:Creating input and output readers/writers.");
				input = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				output = new BufferedWriter(new OutputStreamWriter(connectionSocket.getOutputStream()));
				done = false;
				while(done == false)
				{
					System.out.println(this.getClass().getName()+":run:Reading input.");
					commandString = input.readLine();
					if(commandString != null)
					{
						System.out.println(this.getClass().getName()+
								   ":run:Parsing command line.");
						replyString = parseCommandLine(commandString);
						System.out.println(this.getClass().getName()+
								   ":run:Writing reply string.");
						output.write(replyString);
						System.out.println(this.getClass().getName()+
								   ":run:Flushing output writer.");
						output.flush();
					}
					else
					{
						System.out.println(this.getClass().getName()+
						     ":run:Command line was null:terminating connection thread.");
						done = true;
					}
				}
			}
			catch(Exception e)
			{
				System.err.println(this.getClass().getName()+":run:"+e);
				e.printStackTrace();
			}
			System.out.println(this.getClass().getName()+":run:Finish.");
		}

		/**
		 * Method to examine each newline terminated command sent over the connection, and generate
		 * a suitable (fake) reply.
		 * @param commandString The command to be parsed.
		 * @return A string containing the reply to send to the client over the socket connection.
		 * @see TestIDLSocketServer#FS_MODE_DIRECTORY_FOWLER
		 * @see TestIDLSocketServer#FS_MODE_DIRECTORY_UP_THE_RAMP
		 */
		public String parseCommandLine(String commandString)
		{
			String replyString = null;
			int tExp = 0;

			System.out.println(this.getClass().getName()+":parseCommandLine:Started with command:"+
					   commandString);
			if(commandString.equals("ACQUIRERAMP"))
			{
				SimpleDateFormat dateFormat = null;
				File directory = null;
				String fsModeDirectoryString = null;
				String leafString = null;

				System.out.println(this.getClass().getName()+
						   ":parseCommandLine:ACQUIRERAMP:Started");
				// create a data directory.
				if(testIDLSocketServer.getFS() == 0)
					fsModeDirectoryString = TestIDLSocketServer.FS_MODE_DIRECTORY_UP_THE_RAMP;
				else if(testIDLSocketServer.getFS() == 1)
					fsModeDirectoryString = TestIDLSocketServer.FS_MODE_DIRECTORY_FOWLER;
				else
				{
					replyString = new String(this.getClass().getName()+
								 ":parseCommandLine:ACQUIRERAMP:Illegal bFS value:"+
								 testIDLSocketServer.getFS());
					return replyString;
				}

				// date stamped directories should be of the form: 20130424170309
				dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
				leafString = dateFormat.format(new Date());
				directory = new File(rootDataDirectory+File.separator+
						     fsModeDirectoryString+File.separator+leafString);
				System.out.println(this.getClass().getName()+
						   ":parseCommandLine:ACQUIRERAMP:Creating directory:"+directory);
				directory.mkdir();
				// setup global variables for abort
				testIDLSocketServer.setAbort(false);
				testIDLSocketServer.setAcquiringRamp(true);
				tExp = 0;
				System.out.println(this.getClass().getName()+
						   ":parseCommandLine:ACQUIRERAMP:Sleeping for "+
						   testIDLSocketServer.getExposureLength()+
						   " milliseconds to simulate ACQUIRERAMP");
				// sleep for the exposure length, or until aborted
				while((tExp < testIDLSocketServer.getExposureLength()) && 
				      (testIDLSocketServer.getAbort() == false))
				{
					try
					{
						Thread.sleep(1000);
						tExp += 1000;
					}
					catch(InterruptedException e)
					{
					}
				}
				testIDLSocketServer.setAcquiringRamp(false);
				if(testIDLSocketServer.getAbort())
					replyString = new String("1:Ramp Aborted.\n");
				else
					replyString = new String("0:Ramp Acquired.\n");
			}
			else if(commandString.equals("GETCONFIG"))
			{
				replyString = new String("getconfigkeyword1=1 getconfigkeyword2=2 bFS="+
							 testIDLSocketServer.getFS()+"\n");
			}
			else if(commandString.equals("INITIALIZE2"))
			{
				System.out.println(this.getClass().getName()+
				      ":parseCommandLine:INITIALIZE2:Sleeping for 5 seconds to simulate INITIALIZE");
				try
				{
					Thread.sleep(5000);
				}
				catch(InterruptedException e)
				{
				}
				replyString = new String("0:Initialized.\n");
			}
			else if(commandString.equals("PING"))
			{
				if(testIDLSocketServer.getAcquiringRamp())
					replyString = new String("-1:Acquiring Ramp.\n");
				else
					replyString = new String("0:Idle.\n");
			}
			else if(commandString.equals("POWERDOWNASIC"))
			{
				replyString = new String("0:Powered down.\n");
			}
			else if (commandString.startsWith("SETFSMODE"))
			{
				int fsMode = 1;

				// parse SETFSMODE command of the form: SETFSMODE(<FSMode>)
				// Set the cached FS mode
				if(commandString.charAt(10) == '0')
				{
					fsMode = 0;
					testIDLSocketServer.setFS(fsMode);
					replyString = new String("0:Read up the Ramp mode set.\n");
				}
				else if(commandString.charAt(10) == '1')
				{
					fsMode = 1;
					testIDLSocketServer.setFS(fsMode);
					replyString = new String("0:Fowler Sampling mode set.\n");
				}
				else
				{
					replyString = new String("1:Could not parse SETFSMODE argument:"+
								 commandString.charAt(10)+"\n");
				}
			}
			else if (commandString.startsWith("SETFSPARAM"))
			{
				List<Number> parameterList = parseNumberParameters(commandString);

				// SETFSPARAM(10, 5, 1, 1.0, 1)
				for(int i = 0; i < parameterList.size(); i++ )
				{
					System.out.println(this.getClass().getName()+":parseCommandLine:Command:"+
							   "SETFSPARAM Parameter:"+i+
							   " has value "+parameterList.get(i));
					
				}
				// set exposure length in milliseconds
				// exposure length in seconds is fourth parameter:- index 3.
				testIDLSocketServer.setExposureLength(parameterList.get(3).intValue()*1000);
				System.out.println(this.getClass().getName()+":parseCommandLine:Command:"+
						   "SETFSPARAM:Exposure length computed to be:"+
						   testIDLSocketServer.getExposureLength()+" ms.");
				replyString = new String("0:Set Fowler Sampling Parmeters received.\n");
			}
			else if (commandString.startsWith("SETRAMPPARAM"))
			{
				List<Integer> parameterList = parseIntegerParameters(commandString);

				// e.g. SETRAMPPARAM(10, 5, 0, 5, 1)
				for(int i = 0; i < parameterList.size(); i++ )
				{
					System.out.println(this.getClass().getName()+":parseCommandLine:Command:"+
							   "SETRAMPPARAM Parameter:"+i+
							   " has value "+parameterList.get(i));
					
				}
				// set exposure length in milliseconds
				// ioi.config.UP_THE_RAMP.group_execution_time		=1430
				// nGroup is third paremeter:- index 2
				testIDLSocketServer.setExposureLength(parameterList.get(2).intValue()*1430);
				System.out.println(this.getClass().getName()+":parseCommandLine:Command:"+
						   "SETFSPARAM:Exposure length computed to be:"+
						   testIDLSocketServer.getExposureLength()+" ms.");
				replyString = new String("0:Set Ramp Parmeters received.\n");
			}
			else if (commandString.equals("STOPACQUISITION"))
			{
				if(testIDLSocketServer.getAcquiringRamp())
				{
					System.out.println(this.getClass().getName()+
							   ":parseCommandLine:STOPACQUISITION:"+
							   "We are acquring ramp:Setting abort flag.\n");
					testIDLSocketServer.setAbort(true);
					replyString = new String("0:Set abort flag.\n");
				}
				else
				{
					System.out.println(this.getClass().getName()+
							   ":parseCommandLine:STOPACQUISITION:"+
							   "We are not acquring a ramp:Returning an error\n");
					replyString = new String("1:We are not acquiring a ramp:"+
								 "STOPACQUISITION failed.\n");
				}
			}
			else
				replyString = new String("1:Unknown Command\n");
			System.out.println(this.getClass().getName()+":parseCommandLine:Command:"+
					   commandString+" generated reply string:"+replyString);
			return replyString;
		}

		/**
		 * Extract the list of comma separated parameters from between the parenthesis
		 * in the command String, and return them as a list.
		 * @param commandString The command string containing the parameters to parse.
		 * @return A List (actually a Vector), where each element is a String containing one parameter.
		 */
		public List<String> parseParameters(String commandString)
		{
			String parameterString = null;
			String parameterArray[] = null;
			List<String> parameterList = new Vector<String>();
			int sindex,eindex;

			sindex = commandString.indexOf('(');
			eindex = commandString.indexOf(')');
			parameterString = commandString.substring(sindex+1,eindex);
			System.out.println(this.getClass().getName()+":parseParameters:Command:"+
					   commandString+" generated parameters string:"+parameterString);
			parameterArray = parameterString.split(",");
			for(int i = 0; i < parameterArray.length; i++)
			{
				System.out.println(this.getClass().getName()+":parseParameters:Parameter "+i+
						   " has value "+parameterArray[i]+
						   "and trimmed value "+parameterArray[i].trim());
				parameterList.add(parameterArray[i].trim());
			}
			return parameterList;
		}

		/**
		 * Extract the list of comma separated parameters from between the parenthesis
		 * in the command String, and return them as a list of Integers.
		 * @param commandString The command string containing the parameters to parse.
		 * @return A List (actually a Vector), where each element is a Integer containing one parameter.
		 */
		public List<Integer> parseIntegerParameters(String commandString)
		{
			List<String> stringParameterList = new Vector<String>();
			List<Integer> integerParameterList = new Vector<Integer>();

			stringParameterList = parseParameters(commandString);
			for(int i = 0; i < stringParameterList.size(); i++ )
			{
				integerParameterList.add(new Integer(Integer.parseInt(stringParameterList.get(i))));
			}
			return integerParameterList;
		}

		/**
		 * Extract the list of comma separated parameters from between the parenthesis
		 * in the command String, and return them as a list of Number(s) (either Integer or Double depending
		 * on whether the String contains a '.').
		 * @param commandString The command string containing the parameters to parse.
		 * @return A List (actually a Vector), where each element is a Integer or Double
		 *         containing one parameter.
		 */
		public List<Number> parseNumberParameters(String commandString)
		{
			List<String> stringParameterList = new Vector<String>();
			List<Number> numberParameterList = new Vector<Number>();
			String s = null;

			stringParameterList = parseParameters(commandString);
			for(int i = 0; i < stringParameterList.size(); i++ )
			{
				s = stringParameterList.get(i);
				if(s.indexOf('.') > -1)
					numberParameterList.add(new Double(Double.parseDouble(s)));
				else
					numberParameterList.add(new Integer(Integer.parseInt(s)));
			}
			return numberParameterList;
		}
	}
}
