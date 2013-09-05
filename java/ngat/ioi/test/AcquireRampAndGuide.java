// AcquireRampAndGuide.java
// $HeadURL$
package ngat.ioi.test;

import java.lang.*;
import java.io.*;
import java.text.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.net.*;
import ngat.util.logging.*;

/**
 * This class provides a way to drive the Sidecar via the IDL socket interface, to do a ramp
 * and simultaneusly read out a sub-window as a guide window whilst the science ramp takes place.
 * @author Chris Mottram
 * @version $Revision$
 */
public class AcquireRampAndGuide
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Subdirectory of the root directory, used by the IDL Socket Server when fowler sampling mode is fowler.
	 * @see #DIRECTORY_UP_THE_RAMP
	 */
	public final static String DIRECTORY_FOWLER = new String("FSRamp");
	/**
	 * Subdirectory of the root directory, used by the IDL Socket Server when fowler sampling mode is 
	 * "up the ramp".
	 * @see #DIRECTORY_FOWLER
	 */
	public final static String DIRECTORY_UP_THE_RAMP = new String("UpTheRamp");
	/**
	 * There are 1000 milliseconds in one second.
	 */
	public final static int SECONDS_TO_MS = 1000;
	/**
	 * A TelnetConnection instance holding connection info to the IDL socket server.
	 */
	protected TelnetConnection idlTelnetConnection = null;
	/**
	 * Set the fowler sampling mode to either FOWLER or UP_THE_RAMP.
	 * Currently this should be UP_THE_RAMP, Fowler is not supported at the moment.
	 * @see ngat.ioi.command.SetFSModeCommand#MODE_UP_THE_RAMP
	 * @see ngat.ioi.command.SetFSModeCommand#MODE_FOWLER
	 */
	protected int fsMode = SetFSModeCommand.MODE_UP_THE_RAMP;
	/**
	 * Number of resets to do before starting the exposure.
	 */
	protected int nReset = 1;
	/**
	 * Number of reads to do for each science sub-exposure group.
	 * If fsMode == MODE_FOWLER, this number of reads is done at the start and end of each science
	 * sub-exposure group.
	 * If fsMode == MODE_UP_THE_RAMP, this number of reads is done for each science sub-ramp group 
	 * (interspersed with guide sub-ramps).
	 */
	protected int nScienceRead = 3;
	/**
	 * Number of read/drop groups to do for each science sub-ramp (fsMode == MODE_UP_THE_RAMP).
	 */
	protected int nScienceGroup = 1;
	/**
	 * Number of drops to do for each science sub-exposure group.
	 * If fsMode == MODE_FOWLER, this number has no effect.
	 * If fsMode == MODE_UP_THE_RAMP, this number of drops is done for each science sub-ramp group 
	 * (interspersed with guide sub-ramps).
	 */
	protected int nScienceDrop = 3;
	/**
	 * Number of reads to do for each guide sub-exposure.
	 * If fsMode == MODE_FOWLER, this number of reads is done at the start and end of each guide
	 * sub-exposure.
	 * If fsMode == MODE_UP_THE_RAMP, this number of reads is done for each guide sub-ramp (interspersed with
	 * science sub-ramps).
	 */
	protected int nGuideRead = 1;
	/**
	 * Number of read/drop groups to do for each guide sub-ramp (fsMode == MODE_UP_THE_RAMP).
	 */
	protected int nGuideGroup = 1;
	/**
	 * Number of drops to do for each guide sub-exposure.
	 * If fsMode == MODE_FOWLER, this number has no effect.
	 * If fsMode == MODE_UP_THE_RAMP, this number of drops is done for each guide sub-ramp (interspersed with
	 * guide sub-ramps).
	 */
	protected int nGuideDrop = 0;
	/**
	 * Start of guide window in X, in pixels.
	 */
	protected int guideXStart = 0;
	/**
	 * End of guide window in X, in pixels.
	 */
	protected int guideXStop = 0;
	/**
	 * Start of guide window in Y, in pixels.
	 */
	protected int guideYStart = 0;
	/**
	 * End of guide window in Y, in pixels.
	 */
	protected int guideYStop = 0;
	/**
	 * String containing the root directory of data.
	 */
	protected String rootDirectory = new String("/data/H2RG-C001-ASIC-LT1");
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The callback to be invoked when science data has been generated.
	 * @see AcquireRampAndGuideCallbackInterface
	 */
	protected AcquireRampAndGuideCallbackInterface scienceDataCallback = null;
	/**
	 * The callback to be invoked when guide data has been generated.
	 * @see AcquireRampAndGuideCallbackInterface
	 */
	protected AcquireRampAndGuideCallbackInterface guideDataCallback = null;
	/**
	 * The total exposure length in seconds.
	 */
	protected float totalExposureLengthSeconds = 0.0f;

	/**
	 * Constructor.
	 * @see #logger
	 */
	public AcquireRampAndGuide()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Set the TelnetConnection instance to use for sending commands to the IDL socket server.
	 * The TelnetConnection instance should have been initialised with the IDL socket server address 
	 * and port number, and could have been already opened.
	 * @param tc The TelnetConnection instance to use.
	 * @see #idlTelnetConnection
	 */
	public void setTelnetConnection(TelnetConnection tc)
	{
		idlTelnetConnection = tc;
	}

	/**
	 * Set the parameters for science RUR sampling mode.
	 * @param nReset Number of resets to do perform starting the science exposure.
	 * @param nRead Number of reads to do for each RUR science sub-ramp group.
	 * @param nGroup Number of groups of read/drops to do for each RUR science sub-ramp.
	 * @param nDrop Number of drops to do for each RUR science sub-ramp group.
	 * @see #nReset
	 * @see #nScienceRead
	 * @see #nScienceGroup
	 * @see #nScienceDrop
	 */
	public void setRampParamScience(int nReset,int nRead,int nGroup,int nDrop)
	{
		this.nReset = nReset;
		this.nScienceRead = nRead;
		this.nScienceGroup = nGroup;
		this.nScienceDrop = nDrop;
	}

	/**
	 * Set the exposure length.
	 * @param exposureLengthSeconds The total exposure length of all the science sub-ramps.
	 * @see #totalExposureLengthSeconds
	 */
	public void setExposureLength(float exposureLengthSeconds)
	{
		totalExposureLengthSeconds = exposureLengthSeconds;
	}

	/**
	 * Set the parameters for guide RUR sampling mode.
	 * @param nRead Number of reads to do for each RUR guide sub-ramp group.
	 * @param nGroup Number of groups of read/drops to do for each RUR guide sub-ramp.
	 * @param nDrop Number of drops to do for each RUR guide sub-ramp group.
	 * @see #nGuideRead
	 * @see #nGuideGroup
	 * @see #nGuideDrop
	 */
	public void setRampParamGuide(int nRead,int nGroup,int nDrop)
	{
		this.nGuideRead = nRead;
		this.nGuideGroup = nGroup;
		this.nGuideDrop = nDrop;
	}

	/**
	 * Set the guide window.
	 * @param xStart Start of guide window in X, in pixels.
	 * @param xStop End of guide window in X, in pixels.
	 * @param yStart Start of guide window in Y, in pixels.
	 * @param yStop End of guide window in Y, in pixels.
	 * @see #guideXStart
	 * @see #guideXStop
	 * @see #guideYStart
	 * @see #guideYStop
	 */
	public void setGuideWindow(int xStart,int xStop,int yStart,int yStop)
	{
		guideXStart = xStart;
		guideXStop = xStop;
		guideYStart = yStart;
		guideYStop = yStop;
	}

	/**
	 * Method to set the root directory, under which the FITS images from AcquireRamp are stored.
	 * @param  s The root directory to use as a String.
	 * @see #rootDirectory
	 */
	public void setRootDirectory(String s)
	{
		rootDirectory = s;
	}

	/**
	 * Method to set the callback reference to an object implementing AcquireRampAndGuideCallbackInterface,
	 * whose newData method will be invoked when new science data is created during the exposure.
	 * @see AcquireRampAndGuideCallbackInterface
	 * @see #scienceDataCallback
	 */
	public void setScienceDataCallback(AcquireRampAndGuideCallbackInterface o)
	{
		scienceDataCallback = o;
	}

	/**
	 * Method to set the callback reference to an object implementing AcquireRampAndGuideCallbackInterface,
	 * whose newData method will be invoked when new guide data is created during the exposure.
	 * @see AcquireRampAndGuideCallbackInterface
	 * @see #guideDataCallback
	 */
	public void setGuideDataCallback(AcquireRampAndGuideCallbackInterface o)
	{
		guideDataCallback = o;
	}

	/**
	 * Initialise the Sidecar ready for the exposures.
	 * <ul>
	 * <li>Set the Fowler Sampling mode using an instance of SetFSModeCommand.
	 * <li>Setup the window dimensions.
	 * </ul>
	 * @exception Exception Thrown if sending a command to the IDL Socket Server fails.
	 * @see #idlTelnetConnection
	 * @see #fsMode
	 * @see #guideXStart
	 * @see #guideXStop
	 * @see #guideYStart
	 * @see #guideYStop
	 * @see ngat.ioi.command.Command#setTelnetConnection
	 * @see ngat.ioi.command.Command#sendCommand
	 * @see ngat.ioi.command.StandardReplyCommand#getReplyErrorCode
	 * @see ngat.ioi.command.StandardReplyCommand#getReplyErrorString
	 * @see ngat.ioi.command.SetFSModeCommand
	 * @see ngat.ioi.command.SetFSModeCommand#setCommand
	 * @see ngat.ioi.command.SetWinParamsCommand
	 * @see ngat.ioi.command.SetWinParamsCommand#setCommand
	 */
	public void initialise() throws Exception
	{
		SetFSModeCommand setFSModeCommand = null;
		SetWinParamsCommand setWinParamsCommand = null;

		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":initialise:started.");
		// set the fowler sampling mode
		setFSModeCommand = new SetFSModeCommand();
		setFSModeCommand.setTelnetConnection(idlTelnetConnection);
		setFSModeCommand.setCommand(fsMode);
		setFSModeCommand.sendCommand();
		if(setFSModeCommand.getReplyErrorCode() != 0)
		{
			throw new Exception(this.getClass().getName()+":initialise:SetFSMode failed:"+
					    setFSModeCommand.getReplyErrorCode()+":"+
					    setFSModeCommand.getReplyErrorString());
		}
		// setup the guide window dimensions
		setWinParamsCommand = new SetWinParamsCommand();
		setWinParamsCommand.setTelnetConnection(idlTelnetConnection);
		setWinParamsCommand.setCommand(guideXStart,guideXStop,guideYStart,guideYStop);
		setWinParamsCommand.sendCommand();
		if(setWinParamsCommand.getReplyErrorCode() != 0)
		{
			throw new Exception(this.getClass().getName()+":initialise:SetWinParams failed:"+
					    setWinParamsCommand.getReplyErrorCode()+":"+
					    setWinParamsCommand.getReplyErrorString());
		}
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":initialise:finished.");
	}

	/**
	 * Do an exposure.
	 * <ul>
	 * <li>Turn off idle mode clocking. (SetIdleModeOption)
	 * <li>Set window mode to full frame. (SetWindowMode)
	 * <li>Configure the first ramp for resets and the first science subexposure. (SetRampParam)
	 * <li>Take a timestamp - this is the start of the exposure.
	 * <li>Acquire the first science sub-ramp - this will also initially reset the array. (AcquireRamp)
	 * <li>Find the directory containing the data and call the science data callback.
	 * <li>Loop until the current time is greater than the exposure start time plus the exposure length.
	 *     <ul>
	 *     <li>Set window mode to window. (SetWindowMode)
	 *     <li>Configure the guide ramp. (SetRampParam)
	 *     <li>Acquire the guide ramp. (AcquireRamp)
	 *     <li>Find the directory containing the data and call the guide data callback.
	 *     <li>Set window mode to full frame. (SetWindowMode)
	 *     <li>Configure the science ramp with no resets.  (SetRampParam)
	 *     <li>Acquire the science sub-ramp - this time DO NOT RESET the array. (AcquireRamp)
	 *     <li>Find the directory containing the data and call the science data callback.
	 *     </ul>
	 * <li>Turn back on idle mode clocking. This should also happen if an error occurs. (SetIdleModeOption) 
	 * </ul>
	 * @exception Exception Thrown if one of the IDL Socket Server commands fails, or returns an error.
	 * @see #SECONDS_TO_MS
	 * @see #logger
	 * @see #idlTelnetConnection
	 * @see #scienceDataCallback
	 * @see #guideDataCallback
	 * @see #nReset
	 * @see #nScienceRead
	 * @see #nScienceGroup
	 * @see #nScienceDrop
	 * @see #nGuideRead
	 * @see #nGuideGroup
	 * @see #nGuideDrop
	 * @see #findRampData
	 * @see #printDate
	 * @see #totalExposureLengthSeconds
	 * @see ngat.ioi.command.Command#setTelnetConnection
	 * @see ngat.ioi.command.Command#sendCommand
	 * @see ngat.ioi.command.StandardReplyCommand#getReplyErrorCode
	 * @see ngat.ioi.command.StandardReplyCommand#getReplyErrorString
	 * @see ngat.ioi.command.SetIdleModeOptionCommand
	 * @see ngat.ioi.command.SetIdleModeOptionCommand#setCommand
	 * @see ngat.ioi.command.SetIdleModeOptionCommand#MODE_NOTHING
	 * @see ngat.ioi.command.SetIdleModeOptionCommand#MODE_RESET
	 * @see ngat.ioi.command.SetWindowModeCommand
	 * @see ngat.ioi.command.SetWindowModeCommand#setCommand
	 * @see ngat.ioi.command.SetWindowModeCommand#MODE_FULL_FRAME
	 * @see ngat.ioi.command.SetWindowModeCommand#MODE_WINDOW
	 * @see ngat.ioi.command.SetRampParamCommand
	 * @see ngat.ioi.command.SetRampParamCommand#setCommand
	 * @see ngat.ioi.command.AcquireRampCommand
	 */
	public void expose() throws Exception
	{
		AcquireRampCommand acquireRampCommand = null;
		SetIdleModeOptionCommand setIdleModeOptionCommand = null;
		SetWindowModeCommand setWindowModeCommand = null;
		SetRampParamCommand setRampParamCommand = null;
		String directory = null;
		long exposureStartTime,acquireRampCommandCallTime,currentTime,exposureEndTime;
		boolean done;

		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":expose:started.");
		try
		{
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Turn off idle mode clocking..");
			// Turn off idle mode clocking.
			setIdleModeOptionCommand = new SetIdleModeOptionCommand();
			setIdleModeOptionCommand.setTelnetConnection(idlTelnetConnection);
			setIdleModeOptionCommand.setCommand(SetIdleModeOptionCommand.MODE_NOTHING);
			setIdleModeOptionCommand.sendCommand();
			if(setIdleModeOptionCommand.getReplyErrorCode() != 0)
			{
				throw new Exception(this.getClass().getName()+
						    ":expose:SetIdleModeOption(nothing) failed:"+
						    setIdleModeOptionCommand.getReplyErrorCode()+":"+
						    setIdleModeOptionCommand.getReplyErrorString());
			}
			// Set window mode to full frame.
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Set window mode to full frame.");
			setWindowModeCommand = new SetWindowModeCommand();
			setWindowModeCommand.setTelnetConnection(idlTelnetConnection);
			setWindowModeCommand.setCommand(SetWindowModeCommand.MODE_FULL_FRAME);
			setWindowModeCommand.sendCommand();
			if(setWindowModeCommand.getReplyErrorCode() != 0)
			{
				throw new Exception(this.getClass().getName()+
						    ":expose:SetWindowMode(FULL_FRAME) failed:"+
						    setWindowModeCommand.getReplyErrorCode()+":"+
						    setWindowModeCommand.getReplyErrorString());
			}
			// Configure the first ramp for resets and the first science subexposure.
			// nRamps = 1
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Configure the first ramp for resets and the first science subexposure:"+
				   "SetRampParam(nReset="+nReset+",nScienceRead="+nScienceRead+
				   ",nScienceGroup="+nScienceGroup+",nScienceDrop="+nScienceDrop+",nRamps="+1+")");
			setRampParamCommand = new SetRampParamCommand();
			setRampParamCommand.setTelnetConnection(idlTelnetConnection);
			setRampParamCommand.setCommand(nReset,nScienceRead,nScienceGroup,nScienceDrop,1);
			setRampParamCommand.sendCommand();
			if(setRampParamCommand.getReplyErrorCode() != 0)
			{
				throw new Exception(this.getClass().getName()+
						    ":expose:SetRampParam(nReset="+nReset+
						    ",nScienceRead="+nScienceRead+",nScienceGroup="+nScienceGroup+
						    ",nScienceDrop="+nScienceDrop+",nRamps="+1+") failed:"+
						    setRampParamCommand.getReplyErrorCode()+":"+
						    setRampParamCommand.getReplyErrorString());
			}
			// Take a timestamp - this is the start of the exposure.
			exposureStartTime = System.currentTimeMillis();
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Exposure Start Time:"+printDate(exposureStartTime));
			// get a timestamp before taking an exposure
			// we will use this to find the generated directory
			acquireRampCommandCallTime = System.currentTimeMillis();
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Acquire Ramp Call Time:"+printDate(acquireRampCommandCallTime));
			// Acquire the first science sub-ramp - this will also initially reset the array.
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Acquire the first science sub-ramp - "+
				   "this will also initially reset the array.");
			acquireRampCommand = new AcquireRampCommand();
			acquireRampCommand.setTelnetConnection(idlTelnetConnection);
			acquireRampCommand.sendCommand();
			if(acquireRampCommand.getReplyErrorCode() != 0)
			{
				throw new Exception(this.getClass().getName()+
						    ":expose:AcquireRamp() failed:"+
						    acquireRampCommand.getReplyErrorCode()+":"+
						    acquireRampCommand.getReplyErrorString());
			}
			// Find the directory containing the data and call the science data callback.
			directory = findRampData(acquireRampCommandCallTime);
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Science Data:"+directory);
			if(scienceDataCallback != null)
			{
				scienceDataCallback.newData(AcquireRampAndGuideCallbackInterface.DATA_TYPE_SCIENCE,
							    directory);
			}
			// Loop until the current time is greater than the exposure start time 
			// plus the exposure length.
			currentTime = System.currentTimeMillis();
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Current Time:"+printDate(currentTime));
			exposureEndTime = (exposureStartTime+((long)(totalExposureLengthSeconds*SECONDS_TO_MS)));
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Exposure End Time:"+printDate(exposureEndTime));
			done = (currentTime >= exposureEndTime);
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":expose:done:"+done);
			while(done == false)
			{
				// Set window mode to window.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Set window mode to window.");
				setWindowModeCommand = new SetWindowModeCommand();
				setWindowModeCommand.setTelnetConnection(idlTelnetConnection);
				setWindowModeCommand.setCommand(SetWindowModeCommand.MODE_WINDOW);
				setWindowModeCommand.sendCommand();
				if(setWindowModeCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:SetWindowMode(FULL_WINDOW) failed:"+
							    setWindowModeCommand.getReplyErrorCode()+":"+
							    setWindowModeCommand.getReplyErrorString());
				}
				// Configure the guide ramp.
				// nReset = 0, nRamps = 1
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Configure the guide subexposure:"+
					   "SetRampParam(nReset="+0+",nGuideRead="+nGuideRead+
					   ",nGuideGroup="+nGuideGroup+",nGuideDrop="+nGuideDrop+",nRamps="+1+")");
				setRampParamCommand = new SetRampParamCommand();
				setRampParamCommand.setTelnetConnection(idlTelnetConnection);
				setRampParamCommand.setCommand(0,nGuideRead,nGuideGroup,nGuideDrop,1);
				setRampParamCommand.sendCommand();
				if(setRampParamCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:SetRampParam(nReset="+0+
							    ",nGuideRead="+nGuideRead+",nGuideGroup="+nGuideGroup+
							    ",nGuideDrop="+nGuideDrop+",nRamps="+1+") failed:"+
							    setRampParamCommand.getReplyErrorCode()+":"+
							    setRampParamCommand.getReplyErrorString());
				}
				// Acquire the guide ramp.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Acquire the guide ramp.");
				acquireRampCommandCallTime = System.currentTimeMillis();
				acquireRampCommand = new AcquireRampCommand();
				acquireRampCommand.setTelnetConnection(idlTelnetConnection);
				acquireRampCommand.sendCommand();
				if(acquireRampCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:AcquireRamp() failed:"+
							    acquireRampCommand.getReplyErrorCode()+":"+
							    acquireRampCommand.getReplyErrorString());
				}
				// Find the directory containing the data and call the guide data callback.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				  ":expose:Find the directory containing the data and call the guide data callback.");
				directory = findRampData(acquireRampCommandCallTime);
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Guide Data:"+directory);
				if(guideDataCallback != null)
				{
					guideDataCallback.newData(
					   AcquireRampAndGuideCallbackInterface.DATA_TYPE_GUIDE,directory);
				}
				// Set window mode to full frame.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Set window mode to full frame.");
				setWindowModeCommand = new SetWindowModeCommand();
				setWindowModeCommand.setTelnetConnection(idlTelnetConnection);
				setWindowModeCommand.setCommand(SetWindowModeCommand.MODE_FULL_FRAME);
				setWindowModeCommand.sendCommand();
				if(setWindowModeCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:SetWindowMode(FULL_FRAME) failed:"+
							    setWindowModeCommand.getReplyErrorCode()+":"+
							    setWindowModeCommand.getReplyErrorString());
				}
				// Configure the science sub-ramp - this time DO NOT RESET the array.
				// nReset = 0, nRamps = 1
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Configure the science subexposure:"+
					   "SetRampParam(nReset="+0+",nScienceRead="+nScienceRead+
					   ",nScienceGroup="+nScienceGroup+",nScienceDrop="+nScienceDrop+
					   ",nRamps="+1+")");
				setRampParamCommand = new SetRampParamCommand();
				setRampParamCommand.setTelnetConnection(idlTelnetConnection);
				setRampParamCommand.setCommand(0,nScienceRead,nScienceGroup,nScienceDrop,1);
				setRampParamCommand.sendCommand();
				if(setRampParamCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:SetRampParam(nReset="+0+
							    ",nScienceRead="+nScienceRead+
							    ",nScienceGroup="+nScienceGroup+
							    ",nScienceDrop="+nScienceDrop+",nRamps="+1+") failed:"+
							    setRampParamCommand.getReplyErrorCode()+":"+
							    setRampParamCommand.getReplyErrorString());
				}
				// Acquire the science sub-ramp - this time DO NOT RESET the array.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Acquire the science sub-ramp - this time DO NOT RESET the array.");
				// get a timestamp before taking an exposure
				// we will use this to find the generated directory
				acquireRampCommandCallTime = System.currentTimeMillis();
				acquireRampCommand = new AcquireRampCommand();
				acquireRampCommand.setTelnetConnection(idlTelnetConnection);
				acquireRampCommand.sendCommand();
				if(acquireRampCommand.getReplyErrorCode() != 0)
				{
					throw new Exception(this.getClass().getName()+
							    ":expose:AcquireRamp() failed:"+
							    acquireRampCommand.getReplyErrorCode()+":"+
							    acquireRampCommand.getReplyErrorString());
				}
				// Find the directory containing the data and call the science data callback.
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				 ":expose:Find the directory containing the data and call the science data callback.");
				directory = findRampData(acquireRampCommandCallTime);
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Science Data:"+directory);
				if(scienceDataCallback != null)
				{
					scienceDataCallback.newData(
					   AcquireRampAndGuideCallbackInterface.DATA_TYPE_SCIENCE,directory);
				}
				// Loop until the current time is greater than the exposure start time 
				// plus the exposure length.
				currentTime = System.currentTimeMillis();
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					   ":expose:Current Time:"+printDate(currentTime));
				done = (currentTime >= exposureEndTime);
				logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":expose:done:"+done);
			}// end while
		}
		finally
		{
			// Turn on idle mode clocking.
			logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":expose:Turn on idle mode clocking.");
			setIdleModeOptionCommand = new SetIdleModeOptionCommand();
			setIdleModeOptionCommand.setTelnetConnection(idlTelnetConnection);
			setIdleModeOptionCommand.setCommand(SetIdleModeOptionCommand.MODE_RESET);
			setIdleModeOptionCommand.sendCommand();
			if(setIdleModeOptionCommand.getReplyErrorCode() != 0)
			{
				throw new Exception(this.getClass().getName()+
						    ":expose:SetIdleModeOption(reset) failed:"+
						    setIdleModeOptionCommand.getReplyErrorCode()+":"+
						    setIdleModeOptionCommand.getReplyErrorString());
			}
		}
		logger.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":expose:finished.");
	}

	/**
	 * Method to find the directory containing the ramp data which was initiatated at the time specified
	 * by acquireRampCommandCallTime.
	 * <ul>
	 * <li>The root directory to search from is set from rootDirectory.
	 * <li>We use fsMode to set the fsModeDirectoryString to one of DIRECTORY_UP_THE_RAMP or DIRECTORY_FOWLER.
	 * <li>We create a file from the resultant string, and test it is a directory. If so, we list it's contents.
	 * <li>We loop over each file in the directory list:
	 *     <ul>
	 *     <li>If the file is a directory, we attempt to parse it as a date of the format ""yyyyMMDDHHmmss".
	 *         The IDL socket server creates a directory of this format, for each AcquireRamp command issued.
	 *     <li>We ensure the parsed date is after acquireRampCommandCallTime.
	 *     <li>If the parsed date is soonest one after acquireRampCommandCallTime we have found, we save the
	 *         file in smallestDiffFile and the diff time in smallestDiffTime.
	 *     </ul>
	 * <li>The smallestDiffFile is converted into a string and returned.
	 * </ul>
	 * @param acquireRampCommandCallTime A timestamp taken just before the AcquireRampCommand was started.
	 * @return A string, containing the directory containing the FITS images associated with the ACQUIRERAMP
	 *         just executed.
	 * @exception Exception Thrown if fsMode is not FOWLER or UP_THE_RAMP, the directory or it's contents
	 *            do not exist, or a ramp data directory is not found.
	 * @see #DIRECTORY_FOWLER
	 * @see #DIRECTORY_UP_THE_RAMP
	 * @see #rootDirectory
	 * @see #logger
	 * @see #fsMode
	 */
	protected String findRampData(long acquireRampCommandCallTime) throws Exception
	{
		Date fileDate = null;
		File directoryFile = null;
		File directoryList[];
		File smallestDiffFile = null;
		SimpleDateFormat dateFormat = null;
		String fsModeDirectoryString = null;
		String directoryString = null;
		long diffTime,smallestDiffTime;
		int bFS;

		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":findRampData:started.");
		// remove milliseconds within the second from acquireRampCommandCallTime 
		// This is because the directory file date is accurate to 1 second, so
		// the directory can appear to have been created before acquireRampCommandCallTime by < 1 second
		acquireRampCommandCallTime -= (acquireRampCommandCallTime%1000);
		// get root directory
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":findRampData:root directory:"+rootDirectory+".");
		// get the current configuration of the array
		if(fsMode == SetFSModeCommand.MODE_UP_THE_RAMP)
			fsModeDirectoryString = DIRECTORY_UP_THE_RAMP;
		else if(fsMode == SetFSModeCommand.MODE_FOWLER)
			fsModeDirectoryString = DIRECTORY_FOWLER;
		else
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:fsMode was an illegal value:"+fsMode);
		}
		directoryString = new String(rootDirectory+File.separator+fsModeDirectoryString+File.separator);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":findRampData:Searching date stamp directories in:"+directoryString+".");
		// The directoryString should contain a list of date stamp directories containing the actual data.
		// Find the first date stamp after the acquireRampCommandCallTime
		directoryFile = new File(directoryString);
		if(directoryFile.isDirectory() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:specified directory is not a directory:"+directoryFile);
		}
		directoryList = directoryFile.listFiles();
		if(directoryList == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:Directory list was null:"+directoryFile);
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			":findRampData:Found "+directoryList.length+" files in directory:"+directoryFile+".");
		// date stamped directories should be of the form: 20130424170309
		dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		smallestDiffTime = Integer.MAX_VALUE;
		smallestDiffFile = null;
		for(int i = 0; i < directoryList.length; i++)
		{
			if(directoryList[i].isDirectory())
			{
				// should be a datestamp directory of the form: 20130424170309
				fileDate = dateFormat.parse(directoryList[i].getName());
				// fileDate is null if the parse fails
				if(fileDate != null)
				{
					diffTime = fileDate.getTime()-acquireRampCommandCallTime;
					//logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					//	   ":findRampData:"+directoryList[i]+" has diff time "+
					//	   (diffTime/1000.0)+" seconds after acquire ramp command call time.");
					if((diffTime >= 0)&&(diffTime < smallestDiffTime))
					{
						smallestDiffTime = diffTime;
						smallestDiffFile = directoryList[i];
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
							   ":findRampData:"+directoryList[i]+
							   " has smallest diff time "+(smallestDiffTime/1000.0)+
							   " seconds after acquire ramp command call time.");
					}
					else
					{
						//logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						//	   ":findRampData:"+directoryList[i]+" has diff time "+
						//	   (diffTime/1000.0)+
						//	   " seconds after acquire ramp command call time.");
					}
				}
				else
				{
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						   ":findRampData:Failed to parse date stamp directory:"+
						   directoryList[i]+".");
				}
			}
			else
			{
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					   ":findRampData:Not a directory:"+directoryList[i]+".");
			}
		}// end for
		if(smallestDiffFile == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":findRampData:Ramp Data not found in directory:"+directoryFile);
		}
		directoryString = smallestDiffFile.toString();
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":findRampData:finished and returning directory:"+directoryString+".");
		return directoryString;
	}

	/**
	 * Given a date in the form of milliseconds since the epoch, print a string representation of the date
	 * and time in the format yyyy-MM-dd'T'HH:mm:ss.SSS.
	 * @param timeMillis The number of milliseconds since the epoch.
	 * @return A string representing the date/time, in the format yyyy-MM-dd'T'HH:mm:ss.SSS.
	 */
	protected String printDate(long timeMillis)
	{
		SimpleDateFormat dateFormat = null;
		Date d = null;

		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		d = new Date();
		d.setTime(timeMillis);
		return dateFormat.format(d);
	}
}
//
// $Log$
//
