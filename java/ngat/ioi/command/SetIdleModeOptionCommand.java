// SetIdleModeOptionCommand.java
// $Header$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetIdleModeOption command to the
 * IO:I IDL Socket Server. This determines what happens to the array when we are not exposing,
 * this can be one of : do nothing, take reset frames or take reset-read frames.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetIdleModeOptionCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Mode parameter : Do nothing when not exposing (idling). This will let charge build up on the array..
	 */
	public final static int MODE_NOTHING = 0;
	/**
	 * Mode parameter : Take reset frames when not exposing (idling). 
	 * This will prevent charge building up on the array.
	 */
	public final static int MODE_RESET = 1;
	/**
	 * Mode parameter : Take reset-read frames when not exposing (idling). 
	 * This will prevent charge building up on the array.
	 */
	public final static int MODE_RESET_READ = 2;

	/**
	 * Default constructor.
	 */
	public SetIdleModeOptionCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param mode The mode, an integer, one of: MODE_NOTHING, MODE_RESET, MODE_RESET_READ.
	 * @exception Exception Thrown if the mode is out of range.
	 * @see #commandString
	 * @see #MODE_NOTHING
	 * @see #MODE_RESET
	 * @see #MODE_RESET_READ
	 */
	public void setCommand(int mode) throws Exception
	{
		if((mode != MODE_NOTHING)&&(mode != MODE_RESET)&&(mode != MODE_RESET_READ))
		{
			throw new Exception(this.getClass().getName()+":setCommand:Illegal mode value:"+mode);
		}
		commandString = new String("SETIDLEMODEOPTION("+mode+")");
	}

	/**
	 * Method to parse a string representation of the mode into it's numeric equivalent,
	 * which is used as a parameter to setCommand.
	 * @param modeString The string representation of the mode, one of "NOTHING", "RESET", "RESET_READ".
	 * @return The numeric representation of the mode, one of MODE_NOTHING, MODE_RESET, MODE_RESET_READ.
	 * @exception Exception Thrown if modeString was not a recognised  string representation of the mode.
	 * @see #setCommand
	 * @see #MODE_NOTHING
	 * @see #MODE_RESET
	 * @see #MODE_RESET_READ
	 */
	public static int parseMode(String modeString) throws Exception
	{
		if(modeString.equals("NOTHING"))
			return MODE_NOTHING;
		if(modeString.equals("RESET"))
			return MODE_RESET;
		if(modeString.equals("RESET_READ"))
			return MODE_RESET_READ;
		throw new Exception("ngat.ioi.command.SetIdleModeOptionCommand:parseMode:Illegal mode string:"+
				    modeString);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetIdleModeOptionCommand command = null;
		int portNumber = 5000;
		int mode;

		if(args.length != 3)
		{
			System.out.println("java ngat.ioi.command.SetIdleModeOptionCommand <hostname> <port number> <mode>");
			System.out.println("\t<mode> should be one of: NOTHING|RESET|RESET_READ.");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			mode = SetIdleModeOptionCommand.parseMode(args[2]);
			command = new SetIdleModeOptionCommand();
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
