// SetRampParamCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetRampParam command to the
 * IO:I IDL Socket Server. This sets the parameters for driving the array in Read Up the Ramp Groups Mode.
 * The parameters are:
 * <dl>
 * <dt>nReset</dt> <dd>The number of reset frames to take, before starting the sampling.</dd>
 * <dt>nRead</dt> <dd>The number of reads to do in one group. Therefore the total number of reads is
 *                nRead x nGroup.</dd>
 * <dt>nGroup</dt> <dd>The number of Read/Drop groups.</dd>
 * <dt>nDrop</dt> <dd>The number of frames to drop in each group. The last group has no drop frames, so
 *                nDrop x (nGroup-1) frames are dropped.</dd>
 * <dt>nRamps</dt> <dd>This is basically the number of Up the Ramp Groups ramps to cycle though, effectively
 *     the number of complete integrations (multrun count).</dd>
 * </dl>
 * The Up the Ramp Groups sequence goes something  like:
 * <ul>
 * <li>We loop over the number of ramps (nRamps):
 *     <ul>
 *     <li>nReset reset operations are performed.
 *     <li>We loop over the number of read/drop groups to perform (nGroup):
 *         <ul>
 *         <li>nRead read operations are performed.
 *         <li>If this is not the last group, we drop nDrop frames. 
 *         </ul>
 *     </ul>
 * </ul>
 * The total exposure length of a pixel is basically, the time taken to do (nRead x nGroup) reads plus 
 * (nDrop x (nGroup-1)) drop frames.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetRampParamCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 */
	public SetRampParamCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param nReset The number of reset frames.
	 * @param nRead The number of read frames.
	 * @param nGroup The number of groups.
	 * @param nDrop The number of drop frames.
	 * @param nRamps The number of ramps.
	 */
	public void setCommand(int nReset,int nRead,int nGroup,int nDrop,int nRamps) throws Exception
	{
		commandString = new String("SETRAMPPARAM("+nReset+", "+nRead+", "+nGroup+", "+nDrop+", "+nRamps+")");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetRampParamCommand command = null;
		int portNumber = 5000;
		int nReset,nRead,nDrop,nGroup,nRamp;

		if(args.length != 7)
		{
			System.out.println("java ngat.ioi.command.SetRampParamCommand <hostname> <port number> <nReset> <nRead> <nGroup> <nDrop> <nRamp>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			nReset = Integer.parseInt(args[2]);
			nRead = Integer.parseInt(args[3]);
			nGroup = Integer.parseInt(args[4]);
			nDrop = Integer.parseInt(args[5]);
			nRamp = Integer.parseInt(args[6]);
			command = new SetRampParamCommand();
			command.setAddress(args[0]);
			command.setPortNumber(portNumber);
			command.setCommand(nReset,nRead,nGroup,nDrop,nRamp);
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
