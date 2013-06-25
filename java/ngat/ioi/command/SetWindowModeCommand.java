// SetWindowModeCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetWindowMode command to the
 * IO:I IDL Socket Server. This determines whether subsequent reads use the window, or the full array.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetWindowModeCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Mode parameter : Read out the full frame.
	 */
	public final static int MODE_FULL_FRAME = 0;
	/**
	 * Mode parameter : Read out the full frame.
	 */
	public final static int MODE_WINDOW = 1;

	/**
	 * Default constructor.
	 */
	public SetWindowModeCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param mode The mode, an integer, one of: MODE_FULL_FRAME, MODE_WINDOW.
	 * @exception Exception Thrown if the mode is out of range.
	 * @see #commandString
	 * @see #MODE_FULL_FRAME
	 * @see #MODE_WINDOW
	 */
	public void setCommand(int mode) throws Exception
	{
		if((mode != MODE_FULL_FRAME)&&(mode != MODE_WINDOW))
		{
			throw new Exception(this.getClass().getName()+":setCommand:Illegal mode value:"+mode);
		}
		// Note documentation call this command SETWINDOWMODE, but the example call uses SETFRAMEMODE !
		commandString = new String("SETFRAMEMODE("+mode+")");
	}

	/**
	 * Method to parse a string representation of the mode into it's numeric equivalent,
	 * which is used as a parameter to setCommand.
	 * @param modeString The string representation of the mode, one of "FULL_FRAME", "WINDOW".
	 * @return The numeric representation of the mode, one of MODE_FULL_FRAME, MODE_WINDOW.
	 * @exception Exception Thrown if modeString was not a recognised  string representation of the mode.
	 * @see #setCommand
	 * @see #MODE_FULL_FRAME
	 * @see #MODE_WINDOW
	 */
	public static int parseMode(String modeString) throws Exception
	{
		if(modeString.equals("FULL_FRAME"))
			return MODE_FULL_FRAME;
		if(modeString.equals("WINDOW"))
			return MODE_WINDOW;
		throw new Exception("ngat.ioi.command.SetWindowModeCommand:parseMode:Illegal mode string:"+modeString);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetWindowModeCommand command = null;
		int portNumber = 5000;
		int mode;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.SetWindowModeCommand <hostname> <port number> <mode>");
			System.out.println("\t<mode> should be one of: FULL_FRAME|WINDOW.");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			mode = SetWindowModeCommand.parseMode(args[2]);
			command = new SetWindowModeCommand();
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
