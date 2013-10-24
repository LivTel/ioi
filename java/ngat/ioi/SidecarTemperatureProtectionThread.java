// SidecarTemperatureProtectionThread.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.supircam.temperaturecontroller.*;
import ngat.util.logging.*;

/**
 * An instance of this class is instansiated and run (as a separate thread) from within the IOI software.
 * The thread is designed to periodically query the Lakeshore temeprature controller, and iff the sidecar
 * temperature is too high, power down the ASIC. Some state is maintained that can be reported to other bits
 * of software, so it is known when the temperature is in a state that allows us to power the ASIC.
 * The state model is as follows:
 * <pre>
 * INIT->NOT_RUNNING
 *     +>RUNNING->FAIL_COMMS
 *              +>FAIL_TEMP
 *              +>STOPPED
 * </pre>
 * Note once the FAIL_COMMS, FAIL_TEMP, NOT_RUNNING, STOPPED state are reached these are the final states 
 * (the thread has stopped running). To reset / restart the thread reboot the robotic software or create a new
 * thread instance.
 * @version $Revision: 28 $
 */
public class SidecarTemperatureProtectionThread extends Thread
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means awaiting or doing initialisation.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_INIT        = 0;
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means the thread has stopped running for some non-failure reason, i.e. the thread
	 * has not been configured to run, or the temperature controller has not been enabled in software.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_NOT_RUNNING = 1;
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means the thread is running.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_RUNNING     = 2;
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means the thread has stopped running (and the ASIC powered down), because the actual temperature
	 * of the ASIC is too warm.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_FAIL_TEMP   = 3;
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means the thread has stopped running (and the ASIC powered down), because the retrieving
	 * the current ASIC temperature from the temperature controller has failed, and the thread has been
	 * configured to stop in this situation.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_FAIL_COMMS  = 4;
	/**
	 * Variable used to define a possible thread state for this thread.
	 * This means the thread has stopped running as the stop method has been run. This state should normally
	 * occur whilst shutting down the robotic software as a precusor to a redatum/restart.
	 * @see #threadState
	 */
	public final static int THREAD_STATE_STOPPED      = 5;
	/**
	 * The IOI object.
	 */
	protected IOI ioi = null;
	/**
	 * IOI status reference.
	 * @see IOIStatus
	 */
	protected IOIStatus status = null;
	/**
	 * The interface class to the temperature controller.
	 */
	protected TemperatureController tempControl = null;
	/**
	 * Which input is the sidecar temperature.
	 */
	protected char tempInput;
	/**
	 * Is the temperature controller enabled for use.
	 */
	protected boolean tempControlEnable = false;
	/**
	 * Is the sidecar protection thread enabled.
	 */
	protected boolean sidecarProtectionEnable = false;
	/**
	 * How long the thread sleeps between checks.
	 */
	protected long sleepTime = 60000;
	/**
	 * The failure temeprature in Kelvin. Above this temperature the Sidecar
	 * should be switched off to protect the electronics.
	 */
	protected double failureTemperature = 173.0;
	/**
	 * Whether to change to the fail state and power down tghe ASIC, when there is a communications problem
	 * with the temperature controller.
	 */
	protected boolean failOnCommsFault = false;
	/**
	 * Variable keeping track of the current state of this thread.
	 * @see #THREAD_STATE_INIT
	 * @see #THREAD_STATE_NOT_RUNNING
	 * @see #THREAD_STATE_RUNNING
	 * @see #THREAD_STATE_FAIL_TEMP
	 * @see #THREAD_STATE_FAIL_COMMS
	 */
	protected int threadState = THREAD_STATE_INIT;
	/**
	 * Boolean variable. Normally false, set to true by the stop method, which is then
	 * picked up in the run method and used to terminate the monitoring loop.
	 * @see #run
	 * @see #stop
	 */
	protected boolean stoppingThread = false;

	/**
	 * Constructor.
	 * @see #threadState
	 * @see #THREAD_STATE_INIT
	 */
	public SidecarTemperatureProtectionThread()
	{
		super();
		threadState = THREAD_STATE_INIT;
	}

	/**
	 * Set the IOI object reference. The IOI status object, and temeprature control object, are also retrieved.
	 * @param o The IOI object reference.
	 * @see #ioi
	 * @see #status
	 * @see #tempControl
	 * @see ngat.ioi.IOI#getStatus
	 * @see ngat.ioi.IOI#getTempControl
	 */
	public void setIOI(IOI o)
	{
		ioi = o;
		status = ioi.getStatus();
		tempControl = ioi.getTempControl();
	}

	/**
	 * Initialise various variables before starting the thread. Should be called after setIOI and before
	 * starting the thread. The following config is read:
	 * <ul>
	 * <li>"ioi.sidecar.temperature.protection.enable"
	 * <li>"ioi.temp_control.config.enable"
	 * <li>"ioi.temp_control.temperature_input."+1
	 * <li>"ioi.sidecar.temperature.protection.sleep_time"
	 * <li>"ioi.sidecar.temperature.protection.warm.fail"
	 * <li>"ioi.sidecar.temperature.protection.fail_on_comms_fault"
	 * </ul>
	 * @exception Exception Thrown if retrieving property values fails.
	 * @see #sidecarProtectionEnable
	 * @see #tempControlEnable
	 * @see #tempInput
	 * @see #sleepTime
	 * @see #failureTemperature
	 * @see #failOnCommsFault
	 */
	public void init() throws Exception
	{
		sidecarProtectionEnable = status.getPropertyBoolean("ioi.sidecar.temperature.protection.enable");
		tempControlEnable = status.getPropertyBoolean("ioi.temp_control.config.enable");
		// sidecar temperature
		tempInput = status.getPropertyChar("ioi.temp_control.temperature_input."+1);
		sleepTime = status.getPropertyLong("ioi.sidecar.temperature.protection.sleep_time");
		failureTemperature = status.getPropertyDouble("ioi.sidecar.temperature.protection.warm.fail");
		failOnCommsFault = status.getPropertyBoolean("ioi.sidecar.temperature.protection.fail_on_comms_fault");
	}

	/**
	 * Run method.
	 * <ul>
	 * <li>If sidecarProtectionEnable is false,we enter THREAD_STATE_NOT_RUNNING and terminate.
	 * <li>If tempControlEnable is false,we enter THREAD_STATE_NOT_RUNNING and terminate.
	 * <li>We enter THREAD_STATE_RUNNING.
	 * <li>We enter a loop:
	 *     <ul>
	 *     <li>We get the temperature from the configured input using temperatureGet. If this fails we set a 
	 *         commsFault.
	 *     <li>If we had a commsFault and failOnCommsFault is true, we try calling powerDownASIC, 
	 *         and if this succeeds we set THREAD_STATE_FAIL_COMMS and terminate the loop.
	 *     <li>If the actual temperature is greater than the failureTemperature, we try calling powerDownASIC, 
	 *         and if this succeeds we set THREAD_STATE_FAIL_TEMP and terminate the loop.
	 *     <li>if stoppingThread is set (by the stop method) we set THREAD_STATE_STOPPED and terminate the loop.
	 *     <li>If we are not stopping the loop we sleep for sleepTime.
	 *     </ul>
	 * </ul>
	 * @see #THREAD_STATE_NOT_RUNNING
	 * @see #THREAD_STATE_RUNNING
	 * @see #THREAD_STATE_FAIL_TEMP
	 * @see #THREAD_STATE_FAIL_COMMS
	 * @see #THREAD_STATE_STOPPED
	 * @see #powerDownASIC
	 * @see #sidecarProtectionEnable
	 * @see #tempControlEnable
	 * @see #ioi
	 * @see #tempControl
	 * @see #stoppingThread
	 * @see #tempInput
	 * @see #failOnCommsFault
	 * @see #threadState
	 * @see #failureTemperature
	 * @see #sleepTime
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#temperatureGet
	 */
	public void run()
	{
		double actualTemperature = 0.0;
		boolean finishThreadLoop,commsFault;

		if(sidecarProtectionEnable == false)
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":run:Sidecar Protection NOT enabled: terminating thread.");
			threadState = THREAD_STATE_NOT_RUNNING;
			return;
		}
		if(tempControlEnable == false)
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":run:Temperature Control NOT enabled: terminating thread.");
			threadState = THREAD_STATE_NOT_RUNNING;
			return;
		}
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Starting loop.");
		threadState = THREAD_STATE_RUNNING;
		finishThreadLoop = false;
		while(finishThreadLoop == false)
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":run:Get the current temperature.");
			try
			{
				actualTemperature = tempControl.temperatureGet(tempInput);
				commsFault = false;
			}
			catch(Exception e)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":run:Retrieving temperature failed.");
				commsFault = true;
			}
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":run:Communication status with the temperature controller:commsFault="+commsFault);
			if(commsFault ==  false)
			{
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":run:The current temperature is:"+actualTemperature);
			}
			if(commsFault && failOnCommsFault)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":run:Temperature Controller Communication Fault:Powering down ASIC.");
				//finishThreadLoop = true;
				finishThreadLoop = powerDownASIC();
				if(finishThreadLoop)
					threadState = THREAD_STATE_FAIL_COMMS;
			}
			if((commsFault == false) && (actualTemperature > failureTemperature))
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":run:Current temperature too warm (actual:"+actualTemperature+
					" warmer than failure:"+failureTemperature+"):Powering down ASIC.");
				//finishThreadLoop = true;
				finishThreadLoop = powerDownASIC();
				if(finishThreadLoop)
					threadState = THREAD_STATE_FAIL_TEMP;
			}
			if(stoppingThread)
			{
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":run:We have been asked to stop, terminating thread.");
				finishThreadLoop = true;
				threadState = THREAD_STATE_STOPPED;
			}
			if(finishThreadLoop == false)
			{
				ioi.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					":run:Sleeping for: "+sleepTime+" milliseconds.");
				try
				{
					Thread.sleep(sleepTime);
				}
				catch(Exception e)
				{
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":run:Sleep interrupted.");
				}
			}
		}// end while finishThreadLoop == false
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":run:Finished with thread state:"+
			threadState+".");
	}

	/**
	 * Stop the sidecar temperature protection thread.
	 * <ul>
	 * <li>We set stoppingThread to true, which is read in the run method and used to terminate the loop.
	 * <li>We enter a loop within the stop method, sleeping until the threadState says the 
	 *     loop is no longer running.
	 * </ul>
	 * @see #stoppingThread
	 * @see #finishThreadLoop
	 * @see #THREAD_STATE_RUNNING
	 * @see #stoppingThread
	 */
	public void stopThread()
	{
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":stopThread:Started.");
		stoppingThread = true;
		// wait for the thread to finish sleeping and notice that we are stopping
		while(threadState == THREAD_STATE_RUNNING)
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":stopThread:Waiting for thread to terminate.");
			try
			{
				Thread.sleep(10000);
			}
			catch(Exception e)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":stopThread:Sleep interrupted.");
			}
		}
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":stopThread:Finished.");
	}

	/**
	 * Power down the ASIC by issuing a PowerDownASIC command.
	 * @return Returns true if the PowerDownASIC command returns successfully, and false if it fails.
	 * @see ngat.ioi.command.PowerDownASICCommand
	 * @see ngat.ioi.command.PowerDownASICCommand#sendCommand
	 * @see ngat.ioi.command.PowerDownASICCommand#getReplyErrorCode
	 * @see ngat.ioi.command.PowerDownASICCommand#getReplyErrorString
	 */
	protected boolean powerDownASIC()
	{
		PowerDownASICCommand powerDownASICCommand = null;
		boolean done = false;

		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":powerDownASIC:Started.");
		try
		{
			// power down the ASIC
			powerDownASICCommand = new PowerDownASICCommand();
			powerDownASICCommand.sendCommand();
			if(powerDownASICCommand.getReplyErrorCode() == 0)
			{
				done = true;
			}
			else
			{
				ioi.error(this.getClass().getName()+
					  ":powerDownASIC:PowerDownASIC failed:"+
					  powerDownASICCommand.getReplyErrorCode()+":"+
					  powerDownASICCommand.getReplyErrorString());
				done = false;
			}
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":powerDownASIC:PowerDownASIC threw an exception:",e);
			done = false;

		}
		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":powerDownASIC:Finished with done = "+done+
			".");
		return done;
	}

	/**
	 * Return the current state of this thread.
	 * @return An integer representing the state.
	 * @see #THREAD_STATE_INIT
	 * @see #THREAD_STATE_NOT_RUNNING
	 * @see #THREAD_STATE_RUNNING
	 * @see #THREAD_STATE_FAIL_TEMP
	 * @see #THREAD_STATE_FAIL_COMMS
	 * @see #THREAD_STATE_STOPPED
	 * @see #threadState
	 */
	public int getThreadState()
	{
		return threadState;
	}

	/**
	 * Class method to return a string describing the specified state.
	 * @param state A state to translate into a string.
	 * @return An string representation of the state. This is one of: "INIT","NOT_RUNNING","RUNNING",
	 *         "FAIL_TEMP","FAIL_COMMS"
	 * @see #THREAD_STATE_INIT
	 * @see #THREAD_STATE_NOT_RUNNING
	 * @see #THREAD_STATE_RUNNING
	 * @see #THREAD_STATE_FAIL_TEMP
	 * @see #THREAD_STATE_FAIL_COMMS
	 * @see #THREAD_STATE_STOPPED
	 */
	public static String stateToString(int state)
	{
		switch(state)
		{
			case THREAD_STATE_INIT:
				return "INIT";
			case THREAD_STATE_NOT_RUNNING:
				return "NOT_RUNNING";
			case THREAD_STATE_RUNNING:
				return "RUNNING";
			case THREAD_STATE_FAIL_TEMP:
				return "FAIL_TEMP";
			case THREAD_STATE_FAIL_COMMS:
				return "FAIL_COMMS";
			case THREAD_STATE_STOPPED:
				return "STOPPED";

		}
		return "UNKNOWN";
	}
}
