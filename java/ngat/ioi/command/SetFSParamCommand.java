// SetFSParamCommand.java
// $HeadURL$
package ngat.ioi.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;

/**
 * Extension of the StandardReplyCommand class for sending the SetFSParam command to the
 * IO:I IDL Socket Server. This sets the parameters for driving the array in Fowler Sampling Mode.
 * The parameters are:
 * <dl>
 * <dt>nReset</dt> <dd>The number of reset frames to take, before starting the sampling.</dd>
 * <dt>nRead</dt> <dd>The number of reads to do at the start of the fowler sampling, and at the end of the
 *          fowler sampling. Therefire 2xnReads frames will be produced.</dd>
 * <dt>nGroup</dt> <dd>I am not sure this is used in Fowler sampling mode.</dd>
 * <dt>exposureLength</dt> <dd>The exposure length, in seconds.</dd>
 * <dt>nRamps</dt> <dd>This is basically the number of Fowler ramps to cycle though, effectively
 *     the number of complete integrations (multrun count).</dd>
 * </dl>
 * The fowler sampling sequence goes something like:
 * <ul>
 * <li>We loop over the number of ramps (nRamps):
 *     <ul>
 *     <li>nReset reset operations are performed.
 *     <li>nRead read operations are performed as the flux is started to be collected.
 *     <li>We wait exposureLength seconds.
 *     <li>nRead read operations are performed as the flux still being collected.
 *     </ul>
 * </ul>
 * Therefore the overall exposure length per pixel is increased by the amount of time to do the two sets
 * of reads. Although after processing this may be reduced by one set of reads.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetFSParamCommand extends StandardReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 */
	public SetFSParamCommand()
	{
		super();
	}

	/**
	 * Method to set the command to send to the server.
	 * @param nReset The number of reset frames.
	 * @param nRead The number of read frames.
	 * @param nGroup The number of groups.
	 * @param exposureLength A double representing the exposure length in seconds.
	 * @param nRamps The number of ramps.
	 */
	public void setCommand(int nReset,int nRead,int nGroup,double exposureLength,int nRamps) throws Exception
	{
		DecimalFormat df = null;
		String exposureLengthString = null;

		df = new DecimalFormat("###0.0##");
		exposureLengthString = df.format(exposureLength);
		commandString = new String("SETFSPARAM("+nReset+", "+nRead+", "+nGroup+", "+exposureLengthString+
					   ", "+nRamps+")");
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetFSParamCommand command = null;
		int portNumber = 5000;
		int nReset,nRead,nGroup,nRamp;
		double exposureLength;

		if(args.length != 7)
		{
			System.out.println("java ngat.ioi.command.SetFSParamCommand <hostname> <port number> <nReset> <nRead> <nGroup> <exposure length(s)> <nRamp>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			nReset = Integer.parseInt(args[2]);
			nRead = Integer.parseInt(args[3]);
			nGroup = Integer.parseInt(args[4]);
			exposureLength = Double.parseDouble(args[5]);
			nRamp = Integer.parseInt(args[6]);
			command = new SetFSParamCommand();
			command.setAddress(args[0]);
			command.setPortNumber(portNumber);
			command.setCommand(nReset,nRead,nGroup,exposureLength,nRamp);
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
