// SetDetectorCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetDetector command to the
 * IO:I IDL Socket Server. This determines the multiplexor type (H1RG, H2RG, H4RG) and the number of outputs
 * to use (1,2,4,16,32)
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetDetectorCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Integer representation of multiplexor type H1RG, used as a commkand parameter.
	 * @see #setCommand
	 */
	public final static int MUX_TYPE_H1RG = 1;
	/**
	 * Integer representation of multiplexor type H2RG, used as a commkand parameter.
	 * @see #setCommand
	 */
	public final static int MUX_TYPE_H2RG = 2;
	/**
	 * Integer representation of multiplexor type H4RG, used as a commkand parameter.
	 * @see #setCommand
	 */
	public final static int MUX_TYPE_H4RG = 4;

	/**
	 * Default constructor.
	 */
	public SetDetectorCommand()
	{
		super();
	}

	/**
	 * Method to set the command parameters to send to the server.
	 * The legal values for number of outputs depends on the muxType setting.
	 * @param muxType Which multiplexor type to use, one of: MUX_TYPE_H1RG|MUX_TYPE_H2RG|MUX_TYPE_H4RG.
	 * @param nOutputs Number of outputs to use, one of: 1|2|4|16|32.
	 * @exception Exception Thrown if the mode is out of range.
	 * @see #commandString
	 * @see #MUX_TYPE_H1RG
	 * @see #MUX_TYPE_H2RG
	 * @see #MUX_TYPE_H4RG
	 */
	public void setCommand(int muxType,int nOutputs) throws Exception
	{
		commandString = new String("SETDETECTOR("+muxType+","+nOutputs+")");
	}

	/**
	 * Method to parse a string representation of the mutiplexor into it's numeric equivalent,
	 * which is used as a parameter to setCommand.
	 * @param muxString The string representation of the multiplexor, one of "H1RG", "H2RG", "H4RG".
	 * @return The numeric representation of the mode, one of MUX_TYPE_H1RG, MUX_TYPE_H2RG, MUX_TYPE_H4RG.
	 * @see #MUX_TYPE_H1RG
	 * @see #MUX_TYPE_H2RG
	 * @see #MUX_TYPE_H4RG
	 */
	public static int parseMuxType(String muxString) throws Exception
	{
		if(muxString.equals("H1RG"))
			return MUX_TYPE_H1RG;
		else if(muxString.equals("H2RG"))
			return MUX_TYPE_H2RG;
		else if(muxString.equals("H4RG"))
			return MUX_TYPE_H4RG;
		throw new Exception("ngat.ioi.command.SetDetectorCommand:parseMode:Illegal mux string:"+
				    muxString);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetDetectorCommand command = null;
		CommandReplyBroker replyBroker = null;
		int portNumber = 5000;
		int mux,nOutputs;

		if(args.length != 4)
		{
			System.out.println("java ngat.ioi.command.SetDetectorCommand <hostname> <port number> <mux> <noutputs>");
			System.out.println("\t<mux> should be one of: H1RG|H2RG|H4RG.");
			System.out.println("\t<noutputs> should be one of: 1|2|4|16|32.");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			mux = SetDetectorCommand.parseMuxType(args[2]);
			nOutputs = Integer.parseInt(args[3]);
			command = new SetDetectorCommand();
			// setup telnet connection
			command.telnetConnection.setAddress(args[0]);
			command.telnetConnection.setPortNumber(portNumber);
			command.setCommand(mux,nOutputs);
			command.telnetConnection.open();
			// ensure reply broker is using same connection
			replyBroker = CommandReplyBroker.getInstance();
			replyBroker.setTelnetConnection(command.telnetConnection);
			command.run();
			command.telnetConnection.close();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Reply:"+command.getReplyString());
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Error Code:"+command.getReplyErrorCode());
			System.out.println("Reply Error String:"+command.getReplyErrorString());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
