// SetWinParamsCommand.java
// $Header$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetWinParams command to the
 * IO:I IDL Socket Server. This sets a readout subwindow on the array.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetWinParamsCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 */
	public SetWinParamsCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server. The window goes from xStart to xStop inclusive,
	 * and yStart to yStop inclusive, the total bnumber of pixels are (xStop-xStart+1)*(yStop-yStart+1).
	 * @param xStart Start of window in x pixels.
	 * @param xStop End of window in x pixels (inclusive).
	 * @param yStart Start of window in y pixels.
	 * @param yStop End of window in y pixels (inclusive).
	 */
	public void setCommand(int xStart,int xStop,int yStart,int yStop) throws Exception
	{
		commandString = new String("SETWINPARAMS("+xStart+", "+xStop+", "+yStart+", "+yStop+")");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetWinParamsCommand command = null;
		int portNumber = 5000;
		int xStart,xStop,yStart,yStop;

		if(args.length != 6)
		{
			System.out.println("java ngat.ioi.command.SetWinParamsCommand <hostname> <port number> <xStart> <xStop> <yStart> <yStop>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			xStart = Integer.parseInt(args[2]);
			xStop = Integer.parseInt(args[3]);
			yStart = Integer.parseInt(args[4]);
			yStop = Integer.parseInt(args[5]);
			command = new SetWinParamsCommand();
			command.setAddress(args[0]);
			command.setPortNumber(portNumber);
			command.setCommand(xStart,xStop,yStart,yStop);
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
