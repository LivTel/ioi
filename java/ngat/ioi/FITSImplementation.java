// FITSImplementation.java
// $HeadURL$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.ioi.command.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_BSS.*;
import ngat.message.RCS_BSS.*;
import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation of commands that write FITS files. It extends those that
 * use the hardware libraries as this is needed to generate FITS files.
 * @see HardwareImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class FITSImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Internal constant used when the order number offset defined in the property
	 * 'ioi.get_fits.order_number_offset' is not found or is not a valid number.
	 * @see #getFitsHeadersFromISS
	 */
	private final static int DEFAULT_ORDER_NUMBER_OFFSET = 255;
	/**
	 * A reference to the IOIStatus class instance that holds status information for IO:I.
	 */
	protected IOIStatus status = null;
	/**
	 * A local reference to the FitsHeader object held in IO:I. This is used for writing FITS headers to disk
	 * and setting the values of card images within the headers.
	 */
	protected FitsHeader ioiFitsHeader = null;
	/**
	 * A local reference to the FitsHeaderDefaults object held in IO:I. 
	 * This is used to supply default values, 
	 * units and comments for FITS header card images.
	 */
	protected FitsHeaderDefaults ioiFitsHeaderDefaults = null;

	/**
	 * This method calls the super-classes method, and tries to fill in the reference to the
	 * FITS filename object, the FITS header object and the FITS default value object.
	 * @param command The command to be implemented.
	 * @see #status
	 * @see #ioi
	 * @see IOI#getStatus
	 * @see #ioiFitsHeader
	 * @see IOI#getFitsHeader
	 * @see #ioiFitsHeaderDefaults
	 * @see IOI#getFitsHeaderDefaults
	 */
	public void init(COMMAND command)
	{
		super.init(command);
		if(ioi != null)
		{
			status = ioi.getStatus();
			ioiFitsHeader = ioi.getFitsHeader();
			ioiFitsHeaderDefaults = ioi.getFitsHeaderDefaults();
		}
	}

	/**
	 * This method is used to calculate how long an implementation of a command is going to take, so that the
	 * client has an idea of how long to wait before it can assume the server has died.
	 * @param command The command to be implemented.
	 * @return The time taken to implement this command, or the time taken before the next acknowledgement
	 * is to be sent.
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		return super.calculateAcknowledgeTime(command);
	}

	/**
	 * This routine performs the generic command implementation.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		return super.processCommand(command);
	}

	/**
	 * This routine tries to move the mirror fold to a certain location, by issuing a MOVE_FOLD command
	 * to the ISS. The position to move the fold to is specified by the IOI property file.
	 * If an error occurs the done objects field's are set accordingly.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see IOIStatus#getPropertyInteger
	 * @see IOI#sendISSCommand
	 */
	public boolean moveFold(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		MOVE_FOLD moveFold = null;
		int mirrorFoldPosition = 0;

		moveFold = new MOVE_FOLD(command.getId());
		try
		{
			mirrorFoldPosition = status.getPropertyInteger("ioi.mirror_fold_position");
		}
		catch(NumberFormatException e)
		{
			mirrorFoldPosition = 0;
			ioi.error(this.getClass().getName()+":moveFold:"+command.getClass().getName(),e);
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+300);
			done.setErrorString("moveFold:"+e);
			done.setSuccessful(false);
			return false;
		}
		moveFold.setMirror_position(mirrorFoldPosition);
		instToISSDone = ioi.sendISSCommand(moveFold,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			ioi.error(this.getClass().getName()+":moveFold:"+
				command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+301);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);		
			return false;
		}
		return true;
	}

	/**
	 * This routine clears the current set of FITS headers. The FITS headers are held in the main IO:I
	 * object. This is retrieved and the relevant method called.
	 * @see #ioiFitsHeader
	 * @see ngat.fits.FitsHeader#clearKeywordValueList
	 */
	public void clearFitsHeaders()
	{
		ioiFitsHeader.clearKeywordValueList();
	}

	/**
	 * This routine sets up the Fits Header objects with some keyword value pairs.
	 * It calls the more complicated method below, assuming exposureCount is 1.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @param obsTypeString The type of image taken by the camera. This string should be
	 * 	one of the OBSTYPE_VALUE_* defaults in ngat.fits.FitsHeaderDefaults.
	 * @param exposureTime The exposure time,in milliseconds, to put in the EXPTIME keyword. It
	 * 	is converted into decimal seconds (a double).
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #setFitsHeaders(COMMAND,COMMAND_DONE,String,int,int)
	 */
	public boolean setFitsHeaders(COMMAND command,COMMAND_DONE done,String obsTypeString,int exposureTime)
	{
		return setFitsHeaders(command,done,obsTypeString,exposureTime,1);
	}

	/**
	 * This routine sets up the Fits Header objects with some keyword value pairs.
	 * <p>The following mandatory keywords are assumed to exist in the IDL Socket server generated data: 
	 * SIMPLE,BITPIX,NAXIS,NAXIS1,NAXIS2. </p>
	 * <p> A complete list of keywords is constructed from the IO:I FITS defaults file. Some of the values of
	 * these keywords are overwritten by real data obtained from the camera controller, 
	 * or internal IO:I status.
	 * These are:
	 * OBSTYPE, RUNNUM, EXPNUM, EXPTOTAL, DATE, DATE-OBS, UTSTART, MJD, EXPTIME, 
	 * FILTER1, FILTERI1, FILTER2, FILTERI2, CONFIGID, CONFNAME, 
	 * PRESCAN, POSTSCAN, GAIN, READNOIS, EPERDN, CCDXIMSI, CCDYIMSI, CCDSCALE, CCDRDOUT,
	 * CCDSTEMP, CCDATEMP, CCDWMODE, CALBEFOR, CALAFTER, INSTDFOC, FILTDFOC, MYDFOCUS.
	 * Windowing keywords CCDWXOFF, CCDWYOFF, CCDWXSIZ, CCDWYSIZ are not implemented at the moment.
	 * Note the DATE, DATE-OBS, UTSTART and MJD keywords are given the value of the current
	 * system time, this value is updated to the exposure start time when the image has been exposed. </p>
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @param obsTypeString The type of image taken by the camera. This string should be
	 * 	one of the OBSTYPE_VALUE_* defaults in ngat.fits.FitsHeaderDefaults.
	 * @param exposureTime The exposure time,in milliseconds, to put in the EXPTIME keyword. It
	 * 	is converted into decimal seconds (a double).
	 * @param exposureCount The number of exposures to put in the EXPTOTAL keyword.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #status
	 * @see #ioiFitsHeader
	 * @see #ioiFitsHeaderDefaults
	 * @see IOIStatus#getPropertyBoolean
	 * @see IOIStatus#getPropertyDouble
	 * @see IOIStatus#getBSSFocusOffset
	 * @see ngat.fits.FitsHeaderDefaults#getCardImageList
	 * @see ngat.supircam.temperaturecontroller.TemperatureController#temperatureGet
	 */
	public boolean setFitsHeaders(COMMAND command,COMMAND_DONE done,String obsTypeString,
				      int exposureTime,int exposureCount)
	{
		double actualTemperature = 0.0;
		FitsHeaderCardImage cardImage = null;
		Date date = null;
		String filterWheelString = null;
		String filterWheelIdString = null;
		Vector defaultFitsHeaderList = null;
		int iValue,filterWheelPosition,xBin,yBin,windowFlags,preScan, postScan;
		double doubleValue = 0.0;
		double instDFoc,filtDFoc,myDFoc,bssFoc;
		boolean filterWheelEnable,tempControlEnable;
		char tempInput;

		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":setFitsHeaders:Started.");
		// filter wheel and dfocus data
		try
		{
			// instrument defocus
			instDFoc = status.getPropertyDouble("ioi.focus.offset");
			// currently set the filter dfocus to zero.
			filtDFoc = 0.0;
			// defocus settings
			// get cached BSS offset. This is the offset returned from the last BSS GET_FOCUS_OFFSET
			// command issued by ioi. The current BSS offset may be different, but this cached one
			// is probably better as we want to use it to calculate myDFoc, which is the total
			// DFOCUS this instrument (configuration) would have liked, rather than the current
			// telescope DFOCUS which might be different (if this instrument does not have FOCUS_CONTROL).
			bssFoc = status.getBSSFocusOffset();
			myDFoc = instDFoc + filtDFoc + bssFoc;
		}
		catch(Exception e)
		{
			String s = new String("Command "+command.getClass().getName()+
				":Setting Fits Headers failed:");
			ioi.error(s,e);
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+309);
			done.setErrorString(s+e);
			done.setSuccessful(false);
			return false;
		}
		try
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":setFitsHeaders:Loading defaults.");
		// load all the FITS header defaults and put them into the ioiFitsHeader object
			defaultFitsHeaderList = ioiFitsHeaderDefaults.getCardImageList();
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:Adding "+defaultFitsHeaderList.size()+" defaults to list.");
			ioiFitsHeader.addKeywordValueList(defaultFitsHeaderList,0);
		// NAXIS1
			//cardImage = ioiFitsHeader.get("NAXIS1");
			//cardImage.setValue(new Integer(ccd.getBinnedNCols()));
		// NAXIS2
			//cardImage = ioiFitsHeader.get("NAXIS2");
			//cardImage.setValue(new Integer(ccd.getBinnedNRows()));
		// OBSTYPE
			cardImage = ioiFitsHeader.get("OBSTYPE");
			cardImage.setValue(obsTypeString);
		// The current MULTRUN number and runNumber are used for these keywords at the moment.
		// They are updated in saveFitsHeaders, when the retrieved values are more likely 
		// to be correct.
			// diddly not sure how to calculate these now
		// RUNNUM
			//cardImage = ioiFitsHeader.get("RUNNUM");
			//cardImage.setValue(new Integer(oFilename.getMultRunNumber()));
		// EXPNUM
			//cardImage = ioiFitsHeader.get("EXPNUM");
			//cardImage.setValue(new Integer(oFilename.getRunNumber()));
		// EXPTOTAL
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:EXPTOTAL = "+exposureCount+".");
			cardImage = ioiFitsHeader.get("EXPTOTAL");
			cardImage.setValue(new Integer(exposureCount));
		// The DATE,DATE-OBS and UTSTART keywords are saved using the current date/time.
		// This is updated when the data is saved if CFITSIO is used.
			// diddly some of these date values will be generated internally by the IDL socket server
			date = new Date();
		// DATE
			//cardImage = ioiFitsHeader.get("DATE");
			//cardImage.setValue(date);
		// DATE-OBS
			//cardImage = ioiFitsHeader.get("DATE-OBS");
			//cardImage.setValue(date);
		// UTSTART
			//cardImage = ioiFitsHeader.get("UTSTART");
			//cardImage.setValue(date);
		// MJD
			//cardImage = ioiFitsHeader.get("MJD");
			//cardImage.setValue(date);
		// EXPTIME
			//ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			//	":setFitsHeaders:EXPTIME = "+(((double)exposureTime)/1000.0)+".");
			//cardImage = ioiFitsHeader.get("EXPTIME");
			//cardImage.setValue(new Double(((double)exposureTime)/1000.0));
		// FILTER1
			// diddly these don't exist at the moment
			//cardImage = ioiFitsHeader.get("FILTER1");
			//cardImage.setValue(filterWheelString);
		// FILTERI1
			//cardImage = ioiFitsHeader.get("FILTERI1");
			//cardImage.setValue(filterWheelIdString);
		// CONFIGID
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:CONFIGID = "+status.getConfigId()+".");
			cardImage = ioiFitsHeader.get("CONFIGID");
			cardImage.setValue(new Integer(status.getConfigId()));
		// CONFNAME
			cardImage = ioiFitsHeader.get("CONFNAME");
			cardImage.setValue(status.getConfigName());
		// CCDSTEMP
			doubleValue = status.getPropertyDouble("ioi.temp_control.config.target_temperature.0");
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:CCDSTEMP = "+doubleValue+".");
			cardImage = ioiFitsHeader.get("CCDSTEMP");
			cardImage.setValue(new Integer((int)doubleValue));
			// check whether temperature control is enabled
			tempControlEnable = status.getPropertyBoolean("ioi.temp_control.config.enable");
			if(tempControlEnable)
			{
				tempInput = status.getPropertyChar("ioi.temp_control.temperature_input."+0);
				actualTemperature = tempControl.temperatureGet(tempInput);
		// CCDATEMP
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":setFitsHeaders:CCDATEMP = "+actualTemperature+".");
				cardImage = ioiFitsHeader.get("CCDATEMP");
				cardImage.setValue(new Integer((int)(actualTemperature)));
				// sidecar temperature
				tempInput = status.getPropertyChar("ioi.temp_control.temperature_input."+1);
				actualTemperature = tempControl.temperatureGet(tempInput);
		// SIDETEMP
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":setFitsHeaders:SIDETEMP = "+actualTemperature+".");
				cardImage = ioiFitsHeader.get("SIDETEMP");
				cardImage.setValue(new Integer((int)(actualTemperature)));
			}
		// windowing keywords
		// CCDWMODE
			//windowFlags = ccd.getSetupWindowFlags();
			//cardImage = ioiFitsHeader.get("CCDWMODE");
			//cardImage.setValue(new Boolean((boolean)(windowFlags>0)));
		// CALBEFOR
			//ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+":setFitsHeaders:CALBEFOR.");
			//cardImage = ioiFitsHeader.get("CALBEFOR");
			// diddly cardImage.setValue(new Boolean(status.getCachedConfigCalibrateBefore()));
		// CALAFTER
			//cardImage = ioiFitsHeader.get("CALAFTER");
			// diddly cardImage.setValue(new Boolean(status.getCachedConfigCalibrateAfter()));
		// INSTDFOC
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:INSTDFOC = "+instDFoc+".");
			cardImage = ioiFitsHeader.get("INSTDFOC");
			cardImage.setValue(new Double(instDFoc));
		// FILTDFOC
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:FILTDFOC = "+filtDFoc+".");
			cardImage = ioiFitsHeader.get("FILTDFOC");
			cardImage.setValue(new Double(filtDFoc));
		// MYDFOCUS
			ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
				":setFitsHeaders:MYDFOCUS = "+myDFoc+".");
			cardImage = ioiFitsHeader.get("MYDFOCUS");
			cardImage.setValue(new Double(myDFoc));
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":setFitsHeaders:Finished.");
		}// end try
		// ngat.fits.FitsHeaderException thrown by ioiFitsHeaderDefaults.getValue
		// ngat.util.FileUtilitiesNativeException thrown by IOIStatus.getConfigId
		// IllegalArgumentException thrown by IOIStatus.getFilterWheelName
		// NumberFormatException thrown by IOIStatus.getFilterWheelName/IOIStatus.getConfigId
		// Exception thrown by IOIStatus.getConfigId
		catch(Exception e)
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":setFitsHeaders:An error occured whilst setting headers.");
			String s = new String("Command "+command.getClass().getName()+
				":Setting Fits Headers failed:");
			ioi.error(s,e);
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+304);
			done.setErrorString(s+e);
			done.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * This routine tries to get a set of FITS headers for an exposure, by issuing a GET_FITS command
	 * to the ISS. 
	 * If an error occurs the done objects field's can be set to record the error.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #ioiFitsHeader
	 * @see #DEFAULT_ORDER_NUMBER_OFFSET
	 * @see IOI#sendISSCommand
	 * @see IOI#getStatus
	 * @see IOIStatus#getPropertyInteger
	 */
	public boolean getFitsHeadersFromISS(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		ngat.message.ISS_INST.GET_FITS getFits = null;
		ngat.message.ISS_INST.GET_FITS_DONE getFitsDone = null;
		FitsHeaderCardImage cardImage = null;
		Object value = null;
		Vector list = null;
		int orderNumberOffset;

		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":getFitsHeadersFromISS:Started.");
		getFits = new ngat.message.ISS_INST.GET_FITS(command.getId());
		instToISSDone = ioi.sendISSCommand(getFits,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			ioi.error(this.getClass().getName()+":getFitsHeadersFromISS:"+
				     command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+302);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
	// Get the returned FITS header information into the FitsHeader object.
		getFitsDone = (ngat.message.ISS_INST.GET_FITS_DONE)instToISSDone;
	// extract specific FITS headers 
		list = getFitsDone.getFitsHeader();
		// get an ordernumber offset
		try
		{
			orderNumberOffset = status.getPropertyInteger("ioi.get_fits.iss.order_number_offset");
		}
		catch(NumberFormatException e)
		{
			orderNumberOffset = DEFAULT_ORDER_NUMBER_OFFSET;
			ioi.error(this.getClass().getName()+
				  ":getFitsHeadersFromISS:Getting order number offset failed.",e);
		}
		// Add the list, which is a Vector containing FitsHeaderCardImage objects, 
		// to ioiFitsHeader
		ioiFitsHeader.addKeywordValueList(list,orderNumberOffset);
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":getFitsHeadersFromISS:finished.");
		return true;
	}

	/**
	 * This routine tries to get a set of FITS headers for an exposure, by issuing a GET_FITS command
	 * to the BSS. 
	 * If an error occurs the done objects field's can be set to record the error.
	 * @param command The command being implemented that made this call to the BSS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #ioiFitsHeader
	 * @see #DEFAULT_ORDER_NUMBER_OFFSET
	 * @see IOI#sendBSSCommand
	 * @see IOI#getStatus
	 * @see IOIStatus#getPropertyInteger
	 */
	public boolean getFitsHeadersFromBSS(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_BSS_DONE instToBSSDone = null;
		ngat.message.INST_BSS.GET_FITS getFits = null;
		ngat.message.INST_BSS.GET_FITS_DONE getFitsDone = null;
		FitsHeaderCardImage cardImage = null;
		Object value = null;
		Vector list = null;
		String instrumentName = null;
		int orderNumberOffset;

		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":getFitsHeadersFromBSS:Started.");
		instrumentName = status.getProperty("ioi.bss.instrument_name");
		getFits = new ngat.message.INST_BSS.GET_FITS(command.getId());
		getFits.setInstrumentName(instrumentName);
		instToBSSDone = ioi.sendBSSCommand(getFits,serverConnectionThread);
		if(instToBSSDone.getSuccessful() == false)
		{
			ioi.error(this.getClass().getName()+":getFitsHeadersFromBSS:"+
				  command.getClass().getName()+":"+instToBSSDone.getErrorString());
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+303);
			done.setErrorString(instToBSSDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
		if((instToBSSDone instanceof ngat.message.INST_BSS.GET_FITS_DONE) == false)
		{
			ioi.error(this.getClass().getName()+":getFitsHeadersFromBSS:"+
				  command.getClass().getName()+":DONE was not instance of GET_FITS_DONE:"+
				  instToBSSDone.getClass().getName());
			done.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+305);
			done.setErrorString("getFitsHeadersFromBSS:"+command.getClass().getName()+
					    ":DONE was not instance of GET_FITS_DONE:"+
					    instToBSSDone.getClass().getName());
			done.setSuccessful(false);
			return false;
		}
	// Get the returned FITS header information into the FitsHeader object.
		getFitsDone = (ngat.message.INST_BSS.GET_FITS_DONE)instToBSSDone;
	// extract specific FITS headers and add them to the C layers list
		list = getFitsDone.getFitsHeader();
		// get the order number offset
		try
		{
			orderNumberOffset = status.getPropertyInteger("ioi.get_fits.bss.order_number_offset");
		}
		catch(NumberFormatException e)
		{
			orderNumberOffset = DEFAULT_ORDER_NUMBER_OFFSET;
			ioi.error(this.getClass().getName()+
				  ":getFitsHeadersFromBSS:Getting order number offset failed.",e);
		}
		// do something with list, which is a Vector containing FitsHeaderCardImage objects.
		ioiFitsHeader.addKeywordValueList(list,orderNumberOffset);
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":getFitsHeadersFromBSS:finished.");
		return true;
	}

	/**
	 * Routine to set the telescope focus offset. The offset sent is based on:
	 * <ul>
	 * <li>The instrument's offset with respect to the telescope's natural offset (in the configuration
	 *     property 'ioi.focus.offset'.
	 * <li>An offset returned from the Beam Steering System, based on the position of it's mechanisms.
	 * <ul>
	 * The BSS focus offset is queryed using a GET_FOCUS_OFFSET command to the BSS. The returned offset
	 * is cached in IOIStatus, to be used when writing FITS headers.
	 * This method sends a OFFSET_FOCUS_CONTROL command to
	 * the ISS. OFFSET_FOCUS_CONTROL means thats the offset focus will only be enacted if the BSS thinks this
	 * instrument is in control of the FOCUS at the time this command is sent.
	 * @param configId The Id is used as the OFFSET_FOCUS_CONTROL and GET_FOCUS_OFFSET command's id.
	 * @exception Exception Thrown if the return value of the OFFSET_FOCUS_CONTROL ISS command is false.
	 *            Thrown if the return value of the GET_FOCUS_OFFSET BSS command is false.
	 * @see #ioi
	 * @see #status
	 * @see IOI#sendBSSCommand
	 * @see IOIStatus#setBSSFocusOffset
	 * @see ngat.message.INST_BSS.GET_FOCUS_OFFSET
	 * @see ngat.message.INST_BSS.GET_FOCUS_OFFSET_DONE
	 * @see ngat.message.INST_BSS.GET_FOCUS_OFFSET_DONE#getFocusOffset
	 */
	protected void setFocusOffset(String configId) throws Exception
	{
		GET_FOCUS_OFFSET getFocusOffset = null;
		INST_TO_BSS_DONE instToBSSDone = null;
		GET_FOCUS_OFFSET_DONE getFocusOffsetDone = null;
		OFFSET_FOCUS_CONTROL offsetFocusControlCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		String instrumentName = null;
		float focusOffset = 0.0f;
		boolean bssUse;

		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Started.");
	// get instrument name to use for GET_FOCUS_OFFSET/OFFSET_FOCUS_CONTROL
		instrumentName = status.getProperty("ioi.bss.instrument_name");
		focusOffset = 0.0f;
	// retrieve master telescope focus offset
		focusOffset += status.getPropertyFloat("ioi.focus.offset");
		ioi.log(Logging.VERBOSITY_VERY_TERSE,"setFocusOffset:telescope offset:"+focusOffset+".");
	// get Beam Steering System Offset
		bssUse = status.getPropertyBoolean("ioi.net.bss.use");
		if(bssUse)
		{
			getFocusOffset = new GET_FOCUS_OFFSET(configId);
			getFocusOffset.setInstrumentName(instrumentName);
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":setFocusOffset:Getting BSS focus offset for "+instrumentName+".");
			instToBSSDone = ioi.sendBSSCommand(getFocusOffset,serverConnectionThread);
			if(instToBSSDone.getSuccessful() == false)
			{
				throw new Exception(this.getClass().getName()+
						    ":setFocusOffset:BSS GET_FOCUS_OFFSET failed:"+
						    instrumentName+":"+instToBSSDone.getErrorString());
			}
			if((instToBSSDone instanceof GET_FOCUS_OFFSET_DONE) == false)
			{
				throw new Exception(this.getClass().getName()+":setFocusOffset:BSS GET_FOCUS_OFFSET("+
						    instrumentName+
						    ") did not return instance of GET_FOCUS_OFFSET_DONE:"+
						    instToBSSDone.getClass().getName());
			}
			getFocusOffsetDone = (GET_FOCUS_OFFSET_DONE)instToBSSDone;
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":setFocusOffset:BSS focus offset for "+instrumentName+" was "+
				getFocusOffsetDone.getFocusOffset()+".");
			focusOffset += getFocusOffsetDone.getFocusOffset();
			// Cache the BSS focus offset for writing into the FITS headers
			status.setBSSFocusOffset(getFocusOffsetDone.getFocusOffset());
		}
		else
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":setFocusOffset:BSS not in use, faking BSS GET_FOCUS_OFFSET to 0.0.");
			status.setBSSFocusOffset(0.0f);
		}
	// send the overall focusOffset to the ISS using  OFFSET_FOCUS_CONTROL
		offsetFocusControlCommand = new OFFSET_FOCUS_CONTROL(configId);
		offsetFocusControlCommand.setInstrumentName(instrumentName);
		offsetFocusControlCommand.setFocusOffset(focusOffset);
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Total offset for "+
			instrumentName+" is "+focusOffset+".");
		instToISSDone = ioi.sendISSCommand(offsetFocusControlCommand,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			throw new Exception(this.getClass().getName()+":focusOffset failed:"+focusOffset+":"+
					    instToISSDone.getErrorString());
		}
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Finished.");
	}
}
