// SetFSModeCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetFSMode command to the
 * IO:I IDL Socket Server. This determines whether to run the array in Read up the Ramp or Fowler Sampling mode.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetFSModeCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Mode parameter : Used to set Up the Ramp Group mode.
	 */
	public final static int MODE_UP_THE_RAMP = 0;
	/**
	 * Mode parameter : Used to set Fowler sampling mode.
	 */
	public final static int MODE_FOWLER = 1;

	/**
	 * Default constructor.
	 */
	public SetFSModeCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param mode The mode, an integer, one of: MODE_UP_THE_RAMP, MODE_FOWLER.
	 * @exception Exception Thrown if the mode is out of range.
	 * @see #commandString
	 * @see #MODE_UP_THE_RAMP
	 * @see #MODE_FOWLER
	 */
	public void setCommand(int mode) throws Exception
	{
		if((mode != MODE_UP_THE_RAMP)&&(mode != MODE_FOWLER))
		{
			throw new Exception(this.getClass().getName()+":setCommand:Illegal mode value:"+mode);
		}
		commandString = new String("SETFSMODE("+mode+")");
	}

	/**
	 * Method to parse a string representation of the mode into it's numeric equivalent,
	 * which is used as a parameter to setCommand.
	 * @param modeString The string representation of the mode, one of "UP_THE_RAMP", "FOWLER".
	 * @return The numeric representation of the mode, one of MODE_UP_THE_RAMP, MODE_FOWLER.
	 * @exception Exception Thrown if modeString was not a recognised  string representation of the mode.
	 * @see #setCommand
	 * @see #MODE_UP_THE_RAMP
	 * @see #MODE_FOWLER
	 */
	public static int parseMode(String modeString) throws Exception
	{
		if(modeString.equals("UP_THE_RAMP"))
			return MODE_UP_THE_RAMP;
		if(modeString.equals("FOWLER"))
			return MODE_FOWLER;
		throw new Exception("ngat.ioi.command.SetFSModeCommand:parseMode:Illegal mode string:"+modeString);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetFSModeCommand command = null;
		int portNumber = 5000;
		int mode;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.SetFSModeCommand <hostname> <port number> <mode>");
			System.out.println("\t<mode> should be one of: UP_THE_RAMP|FOWLER.");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			mode = SetFSModeCommand.parseMode(args[2]);
			command = new SetFSModeCommand();
			command.setAddress(args[0]);
			command.setPortNumber(portNumber);
			command.setCommand(mode);
			command.open();
			command.run();
			command.close();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Reply:"+command.getReply());
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
//
// $Log$
//
