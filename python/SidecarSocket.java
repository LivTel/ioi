// SidecarSocket.java
// $Header$
//package ngat.ioi.sidecar;

import org.python.core.PyInstance;
import org.python.util.PythonInterpreter;

import java.lang.*;
import java.text.*;
import java.util.*;

/**
 * This class uses a Jython PythonInterpreter to invoke
 * Rob's python software to communicate with an IDL server socket
 * that is talking to IO:I's Sidecar and Jade hardware.
 * @version $Revision$
 */
public class SidecarSocket
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The python interpreter used to execute the python.
	 */
	protected PythonInterpreter interpreter = null;
	/**
	 * An array of python source code filenames, used to load the python code into the interpreter.
	 */
	protected String pythonFilenameList[] = new String[] {"SIDECARComm.py" /*, "TCPSocket.py"*/};
	/**
	 * A python instance of the python object SIDECARTCPClientSocket.
	 */
	protected PyInstance createSidecarInstance = null;
	/**
	 * The hostname to communicate with the IDL server socket.
	 */
	protected String hostname = null;
	/**
	 * The port number of the IDL server socket.
	 */
	protected int portNumber = 0;

	/**
	 * Create an instance of the class. Initialise and create an instance of the python interpreter.
	 * Call loadPythonCode to load the sidecar python code into the interpreter.
	 * @see #interpreter
	 * @see #loadPythonCode
	 */
	public SidecarSocket()
	{
		super();
		PythonInterpreter.initialize(System.getProperties(),System.getProperties(),new String[0]);
		this.interpreter = new PythonInterpreter();
		loadPythonCode();
	}

	/**
	 * Load the python code from the filenames in pythonFilenameList into the interpreter.
	 * @see #pythonFilenameList
	 */
	protected void loadPythonCode()
	{
		for(int i=0; i < pythonFilenameList.length; i++)
		{
			interpreter.execfile(pythonFilenameList[i]);
		}
	}

	protected void createSidecarInstance() throws Exception
	{
	}

	/**
	 * Set the sidecar IDL server socket address.
	 * @param s An IP address or resolvable hostname, in the form of a string.
	 * @see #hostname
	 */
	public void setHostname(String s)
	{
		hostname = s;
	}

	/**
	 * Set the sidecar IDL server socket port number.
	 * @param i An integer port number.
	 * @see #portNumber
	 */
	public void setPortNumber(int i)
	{
		portNumber = i;
	}

	/**
	 * Main test program.
	 * @param args Command line arguments.
	 * @see #parseArgs
	 */
	public static void main(String args[])
	{
		SidecarSocket ss =null;

		ss = new SidecarSocket();
		try
		{
			parseArgs(ss,args);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	public static void parseArgs(SidecarSocket ss,String args[]) throws Exception
	{
		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-h")||args[i].equals("-hostname"))
			{
				if((i+1)< args.length)
				{
					ss.setHostname(args[i+1]);
					i++;
				}
				else
					throw new Exception("SidecarSocket:-hostname requires an argument.");

			}
			else if(args[i].equals("-p")||args[i].equals("-port_number"))
			{
				if((i+1)< args.length)
				{
					ss.setPortNumber(Integer.parseInt(args[i+1]));
					i++;
				}
				else
					throw new Exception("SidecarSocket:-port_number requires an argument.");

			}
			else
				throw new Exception("SidecarSocket: '"+args[i]+"' not a recognised option");
		}
	}

	public static void help()
	{
		System.out.println("SidecarSocket is a test program for interfacing Java to Rob's SIDECARComm python code.");
		System.out.println("java SidecarSocket [-help][-h[ostname] <ip address>][-p[ort_number] <port>]");
	}
}
