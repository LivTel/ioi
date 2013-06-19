// IOITCPServer.java
// $Header$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.net.*;

import ngat.net.*;

/**
 * This class extends the TCPServer class for the IO:I application.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class IOITCPServer extends TCPServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Field holding the instance of IO:I currently executing, so we can pass this to spawned threads.
	 */
	private IOI ioi = null;

	/**
	 * The constructor.
	 */
	public IOITCPServer(String name,int portNumber)
	{
		super(name,portNumber);
	}

	/**
	 * Routine to set this objects pointer to the ioi object.
	 * @param o The ioi object.
	 */
	public void setIOI(IOI o)
	{
		this.ioi = o;
	}

	/**
	 * This routine spawns threads to handle connection to the server. This routine
	 * spawns IOITCPServerConnectionThread threads.
	 * The routine also sets the new threads priority to higher than normal. This makes the thread
	 * reading it's command a priority so we can quickly determine whether the thread should
	 * continue to execute at a higher priority.
	 * @see IOITCPServerConnectionThread
	 */
	public void startConnectionThread(Socket connectionSocket)
	{
		IOITCPServerConnectionThread thread = null;

		thread = new IOITCPServerConnectionThread(connectionSocket);
		thread.setIOI(ioi);
		thread.setPriority(ioi.getStatus().getThreadPriorityInterrupt());
		thread.start();
	}
}
//
// $Log$
//
