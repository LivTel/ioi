// GET_STATUSImplementation.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.net.*;
import ngat.supircam.temperaturecontroller.*;
import ngat.util.logging.*;
import ngat.util.ExecuteCommand;

/**
 * This class provides the implementation for the GET_STATUS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GET_STATUSImplementation extends INTERRUPTImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Constant KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS of type String with value 
	 * "Instrument.Status.Temperature.Comtroller.Comms".
	 */
	public final static String KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS = "Instrument.Status.Temperature.Controller.Comms";
	/**
	 * The temperature input connected to the array temperature sensor.
	 */
	public final static int TEMPERATURE_INDEX_ARRAY = 0;
	/**
	 * The temperature input connected to the sidecar temperature sensor.
	 */
	public final static int TEMPERATURE_INDEX_SIDECAR = 1;

	/**
	 * This hashtable is created in processCommand, and filled with status data,
	 * and is returned in the GET_STATUS_DONE object.
	 * Generic:<String, Object>
	 */
	private Hashtable hashTable = null;
	/**
	 * Standard status string passed back in the hashTable, describing the instrument status health,
	 * using the standard keyword KEYWORD_INSTRUMENT_STATUS. Initialised to VALUE_STATUS_UNKNOWN.
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_UNKNOWN
	 */
	private String instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
	/**
	 * Standard status string passed back in the hashTable, describing the detector temperature status health,
	 * using the standard keyword KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS. 
	 * Initialised to VALUE_STATUS_UNKNOWN.
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_UNKNOWN
	 */
	private String detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
	/**
	 * Non-standard status passed back in the hashTable, describing whether we can talk to
	 * the temperature controller. This remains at "UNKNOWN" if comms is not enabled.
	 * The value is passed back using KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS keyword.
	 * @see #KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS
	 */
	private String temperatureControllerCommsStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
	/**
	 * The hostname to send lower level commands to.
	 */
	protected String idlServerHostname = null;
	/**
	 * The port number to send lower level commands to.
	 */
	protected int idlServerPortNumber;

	/**
	 * Constructor.
	 */
	public GET_STATUSImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.GET_STATUS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.GET_STATUS";
	}

	/**
	 * This method gets the GET_STATUS command's acknowledge time. 
	 * This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the GET_STATUS command. 
	 * The local hashTable is setup (returned in the done object) and a local copy of status setup.
	 * <ul>
	 * <li>getIDLServerConfig is called to get the C layer address/port number.
	 * <li>The "Instrument" status property is set to the "ioi.get_status.instrument_name" property value.
	 * <li>The detectorTemperatureInstrumentStatus is initialised.
	 * </ul>
	 * An object of class GET_STATUS_DONE is returned, with the information retrieved.
	 * @param command The GET_STATUS command.
	 * @return An object of class GET_STATUS_DONE is returned.
	 * @see #ioi
	 * @see #status
	 * @see #hashTable
	 * @see #idlServerHostname
	 * @see #idlServerPortNumber
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #getIDLServerConfig
	 * @see IOI#getDataProcessingThread
	 * @see DataProcessingThread#getThreadState
	 * @see DataProcessingThread#getCurrentAcquireRampCommandCallTime
	 * @see DataProcessingThread#getCurrentMultrunNumber
	 * @see DataProcessingThread#getCurrentRunNumber
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		GET_STATUS getStatusCommand = (GET_STATUS)command;
		GET_STATUS_DONE getStatusDone = new GET_STATUS_DONE(command.getId());
		ISS_TO_INST currentCommand = null;
		DataProcessingThread dataProcessingThread = null;
		int sidecarTemperatureProtectionThreadState;

		try
		{
			// Create new hashtable to be returned
			// v1.5 generic typing of collections:<String, Object>, can't be used due to v1.4 compatibility
			hashTable = new Hashtable();
			// What instrument is this?
			hashTable.put("Instrument",status.getProperty("ioi.get_status.instrument_name"));
			// get lower level comms configuration
			getIDLServerConfig();
			hashTable.put("IDL.Server.Hostname",idlServerHostname);
			hashTable.put("IDL.Server.Port.Number",new Integer(idlServerPortNumber));
			// Initialise Standard status to UNKNOWN
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
				      detectorTemperatureInstrumentStatus);
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,instrumentStatus);
			// current command
			currentCommand = status.getCurrentCommand();
			if(currentCommand == null)
				hashTable.put("currentCommand","");
			else
				hashTable.put("currentCommand",currentCommand.getClass().getName());
			// currentMode
			getStatusDone.setCurrentMode(status.getCurrentMode());

		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				       ":processCommand:Retrieving basic status failed.",e);
			getStatusDone.setDisplayInfo(hashTable);
			getStatusDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+2500);
			getStatusDone.setErrorString("processCommand:Retrieving basic status failed:"+e);
			getStatusDone.setSuccessful(false);
			return getStatusDone;
		}
		// exposure data
		hashTable.put("Exposure Length",new Integer(status.getExposureLength()));
		hashTable.put("Exposure Start Time",new Long(status.getExposureStartTime()));
		hashTable.put("Exposure Count",new Integer(status.getExposureCount()));
		hashTable.put("Exposure Number",new Integer(status.getExposureNumber()));
		// sidecar temperature protection status
		sidecarTemperatureProtectionThreadState = ioi.getSidecarTemperatureProtectionThread().getThreadState();
		hashTable.put("Sidecar Temperature Protection State",
			      new Integer(sidecarTemperatureProtectionThreadState));
		hashTable.put("Sidecar Temperature Protection State String",
			 SidecarTemperatureProtectionThread.stateToString(sidecarTemperatureProtectionThreadState));
		// update detector temp instrument status based on sidecar temperature protection status
		// If the state is known good RUNNING update detector temp instrument status to known good
		// If the state is known bad FAIL_TEMP FAIL_COMMS update detector temp instrument status to known bad
		// If the state is INIT/NOT_RUNNING/STOPPED don't update detector temp instrument status
		if(sidecarTemperatureProtectionThreadState == SidecarTemperatureProtectionThread.THREAD_STATE_RUNNING)
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		else if ((sidecarTemperatureProtectionThreadState == SidecarTemperatureProtectionThread.THREAD_STATE_FAIL_TEMP)||
			 (sidecarTemperatureProtectionThreadState == SidecarTemperatureProtectionThread.THREAD_STATE_FAIL_COMMS))
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
			      detectorTemperatureInstrumentStatus);
		// DataProcessingThread status
		dataProcessingThread = ioi.getDataProcessingThread();
		hashTable.put("DataProcessingThread.State",
		     new String(DataProcessingThread.threadStateToString(dataProcessingThread.getThreadState())));
		hashTable.put("DataProcessingThread.CurrentAcquireRampCommandCallTime",
			      new Long(dataProcessingThread.getCurrentAcquireRampCommandCallTime()));
		hashTable.put("DataProcessingThread.CurrentMultrunNumber",
			      new Integer(dataProcessingThread.getCurrentMultrunNumber()));
		hashTable.put("DataProcessingThread.CurrentRunNumber",
			      new Integer(dataProcessingThread.getCurrentRunNumber()));
	// intermediate level information - basic plus controller calls.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_INTERMEDIATE)
		{
			getIntermediateStatus();
		}// end if intermediate level status
	// Get full status information.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_FULL)
		{
			getFullStatus();
		}
	// set hashtable and return values.
		getStatusDone.setDisplayInfo(hashTable);
		getStatusDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		getStatusDone.setErrorString("");
		getStatusDone.setSuccessful(true);
	// return done object.
		return getStatusDone;
	}

	/**
	 * Get the lower layer hostname and port number from the properties into some internal variables.
	 * @see #status
	 * @see #idlServerHostname
	 * @see #idlServerPortNumber
	 * @see IOIStatus#getProperty
	 * @see IOIStatus#getPropertyInteger
	 */
	protected void getIDLServerConfig() throws Exception
	{
		idlServerHostname = status.getProperty("ioi.idl.server.hostname");
		idlServerPortNumber = status.getPropertyInteger("ioi.idl.server.port_number");
	}

	/**
	 * Get intermediate level status. 
	 * <ul>
	 * <li>The IDL TelnetConnection is retrieved.
	 * <li>An instance of GetConfigCommand is constructed.
	 * <li>The command is sent to the IDL socket server. If an error occurs it is written to the error log.
	 * <li>If the command returns an error it is written to the error log.
	 * <li>The returned keywords and values are retrieved from the command, and added to the hashTable.
	 * </ul>
	 * The following data is put into the hashTable:
	 * <ul>
	 * <li><b>Temperature.&lt;n&gt;</b></b> The current dewar temperature, 
	 *       this is read from the temperature controller.
	 *       <i>setDetectorTemperatureInstrumentStatus</i> is then called to set the hashtable entry 
	 *       KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS and detectorTemperatureInstrumentStatus.
	 * <li><b>Temperature.Ramp.Rate.&lt;loop&gt;</b> This is read from the temperature controller, and is
	 *        the rate the controller will servo the temeprature in Kelvin/min.
	 * <li><b>Temperature.Ramp.Is_On.&lt;loop&gt;</b> This is read from the temperature controller, and
	 *        determines whether the temperature servo rate has been enabled.
	 * <li><b>Heater PCent</b> The current Heater percentage, this is read from the temperature controller.
	 * <li><b>Heater Status</b> The current Heater status, this is an integer read from the temperature controller.
	 * <li><b>Heater Status String</b> A string representingthe  current Heater status, 
	 *        read from the temperature controller.
	 * </ul>
	 * Finally, <i>setInstrumentStatus</i> is called to set the hashTable's overall instrument status,
	 * in the KEYWORD_INSTRUMENT_STATUS.
	 * @see #ioi
	 * @see #tempControl
	 * @see #hashTable
	 * @see #setDetectorTemperatureInstrumentStatus
	 * @see #setInstrumentStatus
	 * @see #KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS
	 * @see #temperatureControllerCommsStatus
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#temperatureGet
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#heaterStatusGet
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#heaterStatusToString
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#rampGet
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#rampStatusGet
	 * @see ngat.ioi.command.GetConfigCommand
	 */
	private void getIntermediateStatus()
	{
		PingCommand pingCommand = null;
		Hashtable cachedGetConfigCommand = null;
		Enumeration keywords = null;
		Date cachedGetConfigCommandTimestamp = null;
		String heaterStatusString = null;
		String tempControlDeviceType = null;
		double ccdTemperature[] = {0.0,0.0};
		int heaterStatus,loop,tempControlLoopCount;
		double heaterOutput,rate;
		char tempInput;
		boolean tempControlEnable,isOn;

		// call GET_CONFIG IDL server command to get array configuration
		try
		{
			pingCommand = new PingCommand();
			pingCommand.sendCommand();
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":getIntermediateStatus:"+
			      "Ping command failed:",e);
		}
		switch(pingCommand.getReplyErrorCode())
		{
			case 0:
				hashTable.put("Ping","Idle");
				break;
			case -1:
				hashTable.put("Ping","Exposure in progress");
				break;
			default:
				hashTable.put("Ping","Unknown value:"+pingCommand.getReplyErrorCode());
				break;
		}
		// copy GetConfig cache into hashtable
		cachedGetConfigCommand = status.getCachedGetConfigCommand();
		cachedGetConfigCommandTimestamp = status.getCachedGetConfigCommandTimestamp();
		if(cachedGetConfigCommand != null)
		{
			hashTable.putAll(cachedGetConfigCommand);
			hashTable.put("GetConfig Cache Timestamp",cachedGetConfigCommandTimestamp);
		}
		//keywords = getConfigCommand.getKeywords();
		//while(keywords.hasMoreElements())
		//{
		//	String keyword = (String)(keywords.nextElement());
		//	String value = getConfigCommand.getValue(keyword);
		//	hashTable.put(keyword,value);
		//}
		// Is temperature control enabled?
		try
		{
			tempControlEnable = status.getPropertyBoolean("ioi.temp_control.config.enable");
			tempControlDeviceType = status.getProperty("ioi.temp_control.config.device.type");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+":getIntermediateStatus:get Temp Control Enable failed:",
				  e);
			tempControlEnable = false;
		}
		// if enabled, get data from temperature controller
		if(tempControlEnable)
		{
			// CCD temperature
			temperatureControllerCommsStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
			try
			{
				for(int i = 0; i < 2; i++)
				{
					tempInput = status.getPropertyChar("ioi.temp_control.temperature_input."+i);
					ccdTemperature[i] = tempControl.temperatureGet(tempInput);
					hashTable.put("Temperature."+i,new Double(ccdTemperature[i]));
				}
				// Only the Lakeshore has a control loop, the Arduino monitors temperature only.
				if(tempControlDeviceType.equals("LAKESHORE_331"))
				{
					// get current ramp rate and whether ramp rate is turned on for each 
					// temperature loop
					tempControlLoopCount = status.getPropertyInteger("ioi.temp_control.config.loop_count");
					if(tempControlLoopCount > IOI.MAX_LOOP_COUNT)
					{
						ioi.error(this.getClass().getName()+":getIntermediateStatus:"+
							  "retrieved temperature control loop count "+
							  tempControlLoopCount+" was too large:"+IOI.MAX_LOOP_COUNT);
						tempControlLoopCount = 0;
					}
					for(int i = 0; i < tempControlLoopCount; i++)
					{
						loop = status.getPropertyInteger("ioi.temp_control.config.loop."+i);
						rate = tempControl.rampGet(loop);
						hashTable.put("Temperature.Ramp.Rate."+loop,new Double(rate));
						isOn = tempControl.rampStatusGet(loop);
						hashTable.put("Temperature.Ramp.Is_On."+loop,new Boolean(isOn));
					}
				}//end if LAKESHORE_331
				// set standard status value based on current temperature, only if it succeeds
				setDetectorTemperatureInstrumentStatus(ccdTemperature);
			}
			catch(Exception e)
			{
				ioi.error(this.getClass().getName()+":getIntermediateStatus:Get Temperature failed.",
					  e);
				for(int i = 0; i < 2; i++)
					ccdTemperature[i] = 0.0;
				temperatureControllerCommsStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
			}// catch
			// Dewar heater percentage - how much we are heating the dewar to control the temperature.
			// Only the Lakeshore has a control loop, the Arduino monitors temperature only.
			if(tempControlDeviceType.equals("LAKESHORE_331"))
			{
				try
				{
					heaterOutput = tempControl.heaterOutputGet();
					heaterStatus = tempControl.heaterStatusGet();
					heaterStatusString = tempControl.heaterStatusToString(heaterStatus);
				}
				catch(TemperatureControllerNativeException e)
				{
					ioi.error(this.getClass().getName()+":getIntermediateStatus:"+
						  "Get Temperature status failed.",e);
					heaterOutput = 0.0;
					heaterStatus = TemperatureController.HEATER_STATUS_OK;
					heaterStatusString = "Unknown";
					temperatureControllerCommsStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
				}// end catch
			}// end if LAKESHORE_331
			else
			{
				heaterOutput = 0.0;
				heaterStatus = TemperatureController.HEATER_STATUS_OK;
				heaterStatusString = "Unknown";
			}
		}// end if tempControlEnable
		else
		{
			for(int i = 0; i < 2; i++)
				ccdTemperature[i] = 0.0;
			heaterOutput = 0.0;
			heaterStatus = TemperatureController.HEATER_STATUS_OK;
			heaterStatusString = "Unknown";
			temperatureControllerCommsStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
		}
		// set data in Hashtable
		// Copy the array temperature to "Temperature", this used by the opsgui amongst others
		hashTable.put("Temperature",new Double(ccdTemperature[TEMPERATURE_INDEX_ARRAY]));
		hashTable.put("Heater PCent",new Double(heaterOutput));
		hashTable.put("Heater Status",new Integer(heaterStatus));
		hashTable.put("Heater Status String",heaterStatusString);
	// Set temperature controller comms status in hashtable
		hashTable.put(KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS,temperatureControllerCommsStatus);
	// Standard status
		setInstrumentStatus();
	}

	/**
	 * Set the standard entry for detector temperature in the hashtable based upon the current temperature.
	 * Reads the folowing config:
	 * <ul>
	 * <li>ioi.get_status.detector.temperature.warm.warn
	 * <li>ioi.get_status.detector.temperature.warm.fail
	 * <li>ioi.get_status.detector.temperature.cold.warn
	 * <li>ioi.get_status.detector.temperature.cold.fail
	 * </ul>
	 * Note the sidecar temperature detection thread status is also propagated into the detector temperature status
	 * in the main command processing method.
	 * @param currentTemperature The current temperature in degrees Kelvin.
	 * @exception NumberFormatException Thrown if the config is not a valid double.
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void setDetectorTemperatureInstrumentStatus(double currentTemperature[]) throws NumberFormatException
	{
		double warmWarnTemperature,warmFailTemperature,coldWarnTemperature,coldFailTemperature;

		// get config for warn and fail temperatures
		warmWarnTemperature = status.getPropertyDouble("ioi.get_status.detector.temperature.warm.warn");
		warmFailTemperature = status.getPropertyDouble("ioi.get_status.detector.temperature.warm.fail");
		coldWarnTemperature = status.getPropertyDouble("ioi.get_status.detector.temperature.cold.warn");
		coldFailTemperature = status.getPropertyDouble("ioi.get_status.detector.temperature.cold.fail");
		// initialise status to OK, if we have detector temperatures to test
		// and the sidecar temperature detection thread has not already set the status to OK/FAIL.
		if((currentTemperature.length > 0)&&(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_UNKNOWN)))
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		if(currentTemperature.length > 0)
		{
			// test the array temperature only
			if((currentTemperature[TEMPERATURE_INDEX_ARRAY] > warmWarnTemperature)&&
			   (detectorTemperatureInstrumentStatus == GET_STATUS_DONE.VALUE_STATUS_OK))
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
			if((currentTemperature[TEMPERATURE_INDEX_ARRAY] < coldWarnTemperature)&&
				(detectorTemperatureInstrumentStatus == GET_STATUS_DONE.VALUE_STATUS_OK))
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
			if(currentTemperature[TEMPERATURE_INDEX_ARRAY] > warmFailTemperature)
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
			if(currentTemperature[TEMPERATURE_INDEX_ARRAY] < coldFailTemperature)
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		}
		// set hashtable entry
		hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
			      detectorTemperatureInstrumentStatus);
	}

	/**
	 * Set the overall instrument status keyword in the hashtable. This is derived from sub-system keyword values,
	 * currently only the detector temperature. instrumentStatus (hashTable entry KEYWORD_INSTRUMENT_STATUS)
	 * should be set to the worst of OK/WARN/FAIL. If sub-systems are UNKNOWN, OK is returned.
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #instrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 * @see #KEYWORD_TEMPERATURE_CONTROLLER_COMMS_STATUS
	 * @see #temperatureControllerCommsStatus
	 */
	protected void setInstrumentStatus()
	{
		// default to OK
		instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		// if a sub-status is in warning, overall status is in warning
		if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		if(temperatureControllerCommsStatus.equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		// if a sub-status is in fail, overall status is in fail. This overrides a previous warn
	        if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
	        if(temperatureControllerCommsStatus.equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		// set standard status in hashtable
		hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,instrumentStatus);
	}

	/**
	 * Method to get misc status, when level FULL has been selected.
	 * The following data is put into the hashTable:
	 * <ul>
	 * <li><b>Log Level</b> The current logging level IO:I is using.
	 * <li><b>Disk Usage</b> The results of running a &quot;df -k&quot;, to get the disk usage.
	 * <li><b>Process List</b> The results of running a &quot;ps -e -o pid,pcpu,vsz,ruser,stime,time,args&quot;, 
	 * 	to get the processes running on this machine.
	 * <li><b>Uptime</b> The results of running a &quot;uptime&quot;, 
	 * 	to get system load and time since last reboot.
	 * <li><b>Total Memory, Free Memory</b> The total and free memory in the Java virtual machine.
	 * <li><b>java.version, java.vendor, java.home, java.vm.version, java.vm.vendor, java.class.path</b> 
	 * 	Java virtual machine version, classpath and type.
	 * <li><b>os.name, os.arch, os.version</b> The operating system type/version.
	 * <li><b>user.name, user.home, user.dir</b> Data about the user the process is running as.
	 * <li><b>thread.list</b> A list of threads the THOR process is running.
	 * </ul>
	 * @see #serverConnectionThread
	 * @see #hashTable
	 * @see ExecuteCommand#run
	 * @see IOIStatus#getLogLevel
	 */
	private void getFullStatus()
	{
		ExecuteCommand executeCommand = null;
		Runtime runtime = null;
		StringBuffer sb = null;
		Thread threadList[] = null;
		int threadCount;

		// log level
		hashTable.put("Log Level",new Integer(status.getLogLevel()));
		// execute 'df -k' on instrument computer
		executeCommand = new ExecuteCommand("df -k");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Disk Usage",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Disk Usage",new String(executeCommand.getException().toString()));
		// execute "ps -e -o pid,pcpu,vsz,ruser,stime,time,args" on instrument computer
		executeCommand = new ExecuteCommand("ps -e -o pid,pcpu,vsz,ruser,stime,time,args");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Process List",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Process List",new String(executeCommand.getException().toString()));
		// execute "uptime" on instrument computer
		executeCommand = new ExecuteCommand("uptime");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Uptime",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Uptime",new String(executeCommand.getException().toString()));
		// get vm memory situation
		runtime = Runtime.getRuntime();
		hashTable.put("Free Memory",new Long(runtime.freeMemory()));
		hashTable.put("Total Memory",new Long(runtime.totalMemory()));
		// get some java vm information
		hashTable.put("java.version",new String(System.getProperty("java.version")));
		hashTable.put("java.vendor",new String(System.getProperty("java.vendor")));
		hashTable.put("java.home",new String(System.getProperty("java.home")));
		hashTable.put("java.vm.version",new String(System.getProperty("java.vm.version")));
		hashTable.put("java.vm.vendor",new String(System.getProperty("java.vm.vendor")));
		hashTable.put("java.class.path",new String(System.getProperty("java.class.path")));
		hashTable.put("os.name",new String(System.getProperty("os.name")));
		hashTable.put("os.arch",new String(System.getProperty("os.arch")));
		hashTable.put("os.version",new String(System.getProperty("os.version")));
		hashTable.put("user.name",new String(System.getProperty("user.name")));
		hashTable.put("user.home",new String(System.getProperty("user.home")));
		hashTable.put("user.dir",new String(System.getProperty("user.dir")));
	}

}
