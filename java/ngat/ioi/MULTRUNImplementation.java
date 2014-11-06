// MULTRUNImplementation.java
// $HeadURL$
// $Revision$
package ngat.ioi;

import java.lang.*;
import java.io.*;
import java.util.*;

import ngat.ioi.command.*;
import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.net.*;
import ngat.supircam.temperaturecontroller.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class MULTRUNImplementation extends EXPOSEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * This integer is set to 1 when we are using Fowler sampling to acquire data, and 0 when we are using
	 * 'read up the ramp' to acquire data. We determine this by using a GetConfig command to see
	 * what the 'bFS' variable to set to on the IDL Socket Server:- this is in turn set during
	 * CONFIGImplementation.
	 * @see HardwareImplementation#getFSMode
	 */
	protected int bFS = 0;
	/**
	 * The overhead time for reading out in milliseconds.
	 */
	protected int rampOverheadTime = 0;

	/**
	 * Constructor.
	 */
	public MULTRUNImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTRUN&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTRUN";
	}

	/**
	 * This method returns the MULTRUN command's acknowledge time. Each frame in the MULTRUN takes 
	 * the exposure time plus the status's max readout time plus the default acknowledge time to complete. 
	 * The default acknowledge time
	 * allows time to setup the camera, get information about the telescope and save the frame to disk.
	 * This method returns the time for the first frame in the MULTRUN only, as a MULTRUN_ACK message
	 * is returned to the client for each frame taken.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see #serverConnectionThread
	 * @see #status
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see IOITCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see MULTRUN#getExposureTime
	 * @see MULTRUN#getNumberExposures
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(multRunCommand.getExposureTime()+
			serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTRUN command. 
	 * <ul>
	 * <li>We initialise exposure status variables (<b>setExposureCount</b>/<b>setExposureNumber</b>/
	 *     <b>setExposureLength</b>).
	 * <li>It moves the fold mirror to the correct location (<b>moveFold</b>).
	 * <li>We call <b>getBFS</b>, which extracts the bFS variable from the reply to a GetConfig command,
	 *     which tells us whether we have previously configured the array to acquire ramps using Fowler Sampling
	 *     or Read up the Ramp.
	 * <li>If bFS is one (fowler sampling), we call <b>setFowlerSamplingParameters</b> to send a command to the
	 *     IDL Socket Server to configure the fowler sampling mode.
	 * <li>If bFS is zero (read up the ramp), we call <b>setReadUpTheRampParameters</b> to send a command to the
	 *     IDL Socket Server to configure read up the ramp mode.
	 * <li>For each exposure we do the following:
	 *	<ul>
	 *      <li>We issue a RA/DEC offset to the ISS, for sky dithering (<b>offsetTelescope</b>).
	 * 	<li>It generates some FITS headers from the setup, ISS and BSS, using 
	 *          <b>clearFitsHeaders</b>,<b>setFitsHeaders</b>,<b>getFitsHeadersFromISS</b>,
	 *          <b>getFitsHeadersFromBSS</b>
	 *      <li>We take an exposure start time timestamp, save it in the status object
	 *          (<b>setExposureStartTime</b>), and set the status's current mode (<b>setCurrentMode</b>) 
	 *           to exposure.
	 * 	<li>We call <b>acquireRamp</b> to do the exposure.
	 * 	<li>We call <b>findRampData</b> to find where the IDL Socket Server has created a new directory with 
	 *          the acquired data.
	 * 	<li>We call <b>findFITSFilesInDirectory</b> to locate all the generated FITS files from the ramp.
	 * 	<li>We call <b>addFitsHeadersToFitsImages</b> to add the previously retrieved ISS/BSS/IO:I headers
	 *          to the IDL Socket Server generated FITS images.
	 * 	<li>We call <b>renameFitsFiles</b> which, depending on a config option, 
	 *          renames the IDL Socket Server generated FITS images to LT standard filenames.
	 * 	<li>We call <b>sendMultrunACK</b> to the client to ensure the client connection does not time out.
	 * 	<li>We keep track of the generated filenames in the list. We increment the exposure number in
	 *          the status object (<b>setExposureNumber</b>).
	 * 	</ul>
	 * <li>We offset the telscope back to 0,0 using resetTelescopeOffset.
	 * <li>If we are going to call the DpRt, we iterate over the filename list calling <b>reduceExpose</b>
	 *     for each generated FITS image.
	 * <li>We set up the return values to return to the client.
	 * </ul>
	 * The resultant filenames or the relevant error code is put into the an object of class MULTRUN_DONE and
	 * returned. During execution of these operations the abort flag is tested to see if we need to
	 * stop the implementation of this command.
	 * @see #offsetTelescope
	 * @see #resetTelescopeOffset
	 * @see #getBFS
	 * @see #setFowlerSamplingParameters
	 * @see #setReadUpTheRampParameters
	 * @see #acquireRamp
	 * @see #sendMultrunACK
	 * @see #bFS
	 * @see CommandImplementation#testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see FITSImplementation#getFitsHeadersFromBSS
	 * @see FITSImplementation#findRampData
	 * @see FITSImplementation#findFITSFilesInDirectory
	 * @see FITSImplementation#addFitsHeadersToFitsImages
	 * @see FITSImplementation#renameFitsFiles
	 * @see EXPOSEImplementation#sendACK
	 * @see EXPOSEImplementation#reduceExpose
	 * @see IOIStatus#setExposureLength
	 * @see IOIStatus#setExposureCount
	 * @see IOIStatus#setExposureNumber
	 * @see IOIStatus#setExposureStartTime
	 * @see IOIStatus#setCurrentMode
	 * @see IOIStatus#getCurrentMode
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_EXPOSING
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_READING_OUT
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		MULTRUN_DP_ACK multRunDpAck = null;
		MULTRUN_DONE multRunDone = new MULTRUN_DONE(command.getId());
		FitsFilename fitsFilename = null;
		Vector<File> reduceFilenameList = null;
		List<File> fitsFileList = null;
		File fitsFile = null;
		String obsType = null;
		String directory = null;
		String filename = null;
		double exposureLengthSeconds;
		long acquireRampCommandCallTime;
		int index;
		char exposureCode;
		boolean retval = false;
		boolean fitsFilenameRename;

		ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			":processCommand:Starting MULTRUN with exposure length "+multRunCommand.getExposureTime()+
			" ms and number of exposures "+multRunCommand.getNumberExposures()+".");
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		// send an initiali ACK, actually getDefaultAcknowledgeTime long
		if(sendACK(multRunCommand,multRunDone,0) == false)
			return multRunDone;
	// setup exposure status.
		status.setExposureCount(multRunCommand.getNumberExposures());
		status.setExposureNumber(0);
		status.setExposureLength(multRunCommand.getExposureTime());
		// if we are renaming the FITS images, increment the MULTRUN number
		fitsFilenameRename = status.getPropertyBoolean("ioi.file.fits.rename");
		if(fitsFilenameRename)
		{
			fitsFilename = ioi.getFitsFilename();
			fitsFilename.nextMultRunNumber();
		}
	// move the fold mirror to the correct location
		if(moveFold(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		if(multRunCommand.getStandard())
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_STANDARD;
			exposureCode = FitsFilename.EXPOSURE_CODE_STANDARD;
		}
		else
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_EXPOSURE;
			exposureCode = FitsFilename.EXPOSURE_CODE_EXPOSURE;
		}
		// configure the array 
		exposureLengthSeconds = ((double)(multRunCommand.getExposureTime())/1000.0);
		// send an ACK, actually getDefaultAcknowledgeTime long
		if(sendACK(multRunCommand,multRunDone,0) == false)
			return multRunDone;
		// Find out which sampling mode the array is using
		if(!getBFS(multRunCommand,multRunDone))
			return multRunDone;
		if(bFS == 1)// Fowler sampling mdoe
		{
			if(!setFowlerSamplingParameters(multRunCommand,multRunDone,exposureLengthSeconds))
				return multRunDone;
		}
		else if(bFS == 0)// Read Up the Ramp mode
		{
			if(!setReadUpTheRampParameters(multRunCommand,multRunDone,exposureLengthSeconds))
				return multRunDone;
		}
		// do exposures
		index = 0;
		retval = true;
		reduceFilenameList = new Vector<File>();
		while(retval&&(index < multRunCommand.getNumberExposures()))
		{
			// send an ACK, actually at least one exposure length + ramp overhead long
			if(sendACK(multRunCommand,multRunDone,
				   multRunCommand.getExposureTime()+rampOverheadTime) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:sendACK failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Starting exposure "+index+" of length "+exposureLengthSeconds+"s.");
			// RA/Dec Offset for sky dithering.
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Offseting telescope.");
			if(offsetTelescope(multRunCommand,multRunDone,index) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:offsetTelescope failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			// get fits headers
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Retrieving FITS headers.");
			clearFitsHeaders();
			if(setFitsHeaders(multRunCommand,multRunDone,obsType,
					  multRunCommand.getExposureTime(),
					  multRunCommand.getNumberExposures()) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:setFitsHeaders failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			if(getFitsHeadersFromISS(multRunCommand,multRunDone) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:getFitsHeadersFromISS failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:testAbort failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			if(getFitsHeadersFromBSS(multRunCommand,multRunDone) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:getFitsHeadersFromBSS failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:testAbort failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			// send an ACK, at least one exposure length + ramp overhead long
			if(sendACK(multRunCommand,multRunDone,
				   multRunCommand.getExposureTime()+rampOverheadTime) == false)
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:sendACK failed for index "+index+
					" : Reseting telescope offset.");
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			// get a timestamp before taking an exposure
			// we will use this to find the generated directory
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Calling AcquireRamp of length "+exposureLengthSeconds+
				"s for exposure index "+index+".");
			acquireRampCommandCallTime = System.currentTimeMillis();
			status.setExposureStartTime(acquireRampCommandCallTime);
			status.setCurrentMode(GET_STATUS_DONE.MODE_EXPOSING);
			// do exposure.
			if(!acquireRamp(multRunCommand,multRunDone))
			{
				ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					":processCommand:acquireRamp failed for index "+index+
					" : Reseting telescope offset.");
				status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				return multRunDone;
			}
			// We are not really reading out, but managing the acquired data
			status.setCurrentMode(GET_STATUS_DONE.MODE_READING_OUT);
			// find the data just acquired
			try
			{
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Finding ramp data for exposure index "+index+".");
				directory = findRampData(acquireRampCommandCallTime);
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Listing FITS images in Ramp Data directory "+
					directory+".");
				fitsFileList = findFITSFilesInDirectory(directory);
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Adding FITS headers to "+fitsFileList.size()+
					" FITS images.");
				// It can take up to 2 seconds per frame to add FITS headers to each frame
				// send an ACK to stop a timeout
				if(sendACK(multRunCommand,multRunDone,(fitsFileList.size()*2000)) == false)
				{
					ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
						":processCommand:sendACK failed for index "+index+
						" : Reseting telescope offset.");
					//moveFilterToBlank(multRunCommand,multRunDone);
					resetTelescopeOffset(multRunCommand,multRunDone);
					return multRunDone;
				}
				addFitsHeadersToFitsImages(fitsFileList);
				ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					":processCommand:Rename generated FITS images to LT spec (if enabled).");
				renameFitsFiles(fitsFileList,exposureCode);
			}
			catch(Exception e)
			{
				retval = false;
				ioi.error(this.getClass().getName()+
					  ":processCommand:Processing acquired data for exposure index "+index+
					  " failed:"+command+":",e);
				status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
				//moveFilterToBlank(multRunCommand,multRunDone);
				resetTelescopeOffset(multRunCommand,multRunDone);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1201);
				multRunDone.setErrorString(this.getClass().getName()+
							   ":processCommand:findRampData failed:"+
							   e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			status.setCurrentMode(GET_STATUS_DONE.MODE_IDLE);
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Ramp data found in directory:"+directory+" for exposure index "+
				index+".");
			// return the FITS files generated for this exposure
			if(fitsFileList != null)
			{
				for(int fitsFileIndex = 0; fitsFileIndex < fitsFileList.size(); fitsFileIndex++)
				{
					fitsFile = (File)(fitsFileList.get(fitsFileIndex));
					filename = fitsFile.getAbsolutePath();
					// send acknowledge to say frames are completed.
					if(!sendMultrunACK(multRunCommand,multRunDone,filename))
					{
						ioi.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
							":processCommand:sendMultrunACK failed for index "+index+
							" : Reseting telescope offset.");
						//moveFilterToBlank(multRunCommand,multRunDone);
						resetTelescopeOffset(multRunCommand,multRunDone);
						return multRunDone;
					}
				}// end for on fits filenames
			}// end if
			status.setExposureNumber(index+1);
			// add filename to list for data pipeline processing.
			reduceFilenameList.addAll(fitsFileList);
			// test whether an abort has occured.
			if(testAbort(multRunCommand,multRunDone) == true)
			{
				retval = false;
			}
			index++;
		}// end while
		//moveFilterToBlank(multRunCommand,multRunDone);
	// reset telescope offsets
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":processCommand:Reseting telescope offset.");
		if(resetTelescopeOffset(multRunCommand,multRunDone) == false)
			return multRunDone;
		// if a failure occurs, return now
		if(!retval)
		{
			return multRunDone;
		}
		index = 0;
		retval = true;
	// call pipeline to process data and get results
		if(multRunCommand.getPipelineProcess())
		{
			ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				":processCommand:Data pipelining.");
			while(retval&&(index < reduceFilenameList.size()))
			{
				filename = (String)(reduceFilenameList.get(index).toString());
			// do reduction.
				retval = reduceExpose(multRunCommand,multRunDone,filename);
			// send acknowledge to say frame has been reduced.
				multRunDpAck = new MULTRUN_DP_ACK(command.getId());
				multRunDpAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
			// copy Data Pipeline results from DONE to ACK
				multRunDpAck.setFilename(multRunDone.getFilename());
				multRunDpAck.setCounts(multRunDone.getCounts());
				multRunDpAck.setSeeing(multRunDone.getSeeing());
				multRunDpAck.setXpix(multRunDone.getXpix());
				multRunDpAck.setYpix(multRunDone.getYpix());
				multRunDpAck.setPhotometricity(multRunDone.getPhotometricity());
				multRunDpAck.setSkyBrightness(multRunDone.getSkyBrightness());
				multRunDpAck.setSaturation(multRunDone.getSaturation());
				try
				{
					serverConnectionThread.sendAcknowledge(multRunDpAck);
				}
				catch(IOException e)
				{
					retval = false;
					ioi.error(this.getClass().getName()+
						":processCommand:sendAcknowledge(DP):"+command+":"+e.toString());
					multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1203);
					multRunDone.setErrorString(e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					retval = false;
				}
				index++;
			}// end while on MULTRUN exposures
		}// end if Data Pipeline is to be called
		else
		{
		// no pipeline processing occured, set return value to something bland.
		// set filename to last filename exposed.
			multRunDone.setFilename(filename);
			multRunDone.setCounts(0.0f);
			multRunDone.setSeeing(0.0f);
			multRunDone.setXpix(0.0f);
			multRunDone.setYpix(0.0f);
			multRunDone.setPhotometricity(0.0f);
			multRunDone.setSkyBrightness(0.0f);
			multRunDone.setSaturation(false);
		}
	// if a failure occurs, return now
		if(!retval)
			return multRunDone;
	// setup return values.
	// setCounts,setFilename,setSeeing,setXpix,setYpix 
	// setPhotometricity, setSkyBrightness, setSaturation set by reduceExpose for last image reduced.
		multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_NO_ERROR);
		multRunDone.setErrorString("");
		multRunDone.setSuccessful(true);
	// return done object.
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":processCommand:MULTRUN command completed.");
		return multRunDone;
	}

	/**
	 * Get whether we are using fowler sampling or read up the ramp to acquire data. We do this by calling
	 * HardwareImplementation's getFSMode, which in turn issues a GetConfig to the IDL Socket Server
	 * and looks at the value of the 'bFS' keyword returned.
	 * @param multRunCommand The MULTRUN command we are implementing.
	 * @param multRunDone The MULTRUN_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see #bFS
	 * @see ngat.ioi.IOI#error
	 * @see HardwareImplementation#getFSMode
	 */
	protected boolean getBFS(MULTRUN multRunCommand,MULTRUN_DONE multRunDone)
	{
		try
		{
			bFS = getFSMode();
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":getBFS:getFSMode failed:"+multRunCommand,e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1206);
			multRunDone.setErrorString(this.getClass().getName()+
						   ":getBFS:getFSMode failed:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Configure the IDL Socket Server for a ramp in Fowler Sampling mode.
	 * <ul>
	 * <li>The number of resets to use is taken from the "ioi.config.FOWLER.nreset" property value.
	 * <li>The number of reads to use is taken from the "ioi.config.FOWLER.nread" property value.
	 * <li>An instance of SetFSParamCommand is constructed, configured and used to send a 
	 *     SetFSParam command to the IDL Socket Server.
	 * </ul>
	 * If an error or exception occurs it is caught, a suitable error message put into MULTRUN_DONE, and false
	 * is returned.
	 * @param multRunCommand The MULTRUN command we are implementing.
	 * @param multRunDone The MULTRUN_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @param exposureLengthSeconds The exposure length in seconds. This is passed as a parameter to
	 *        SetFSParamCommand.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see #rampOverheadTime
	 * @see ngat.ioi.IOI#error
	 * @see ngat.ioi.command.SetFSParamCommand
	 */
	protected boolean setFowlerSamplingParameters(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,
						      double exposureLengthSeconds)
	{
		SetFSParamCommand setFSParamCommand = null;
		int nReset,nRead,resetExecutionTime,readExecutionTime;

		try
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setFowlerSamplingParameters:Configuring Fowler sampling mode.");
			nReset = status.getPropertyInteger("ioi.config.FOWLER.nreset");
			nRead = status.getPropertyInteger("ioi.config.FOWLER.nread");
			setFSParamCommand = new SetFSParamCommand();
			setFSParamCommand.setCommand(nReset,nRead,1,exposureLengthSeconds,1);
			setFSParamCommand.sendCommand();
			if(setFSParamCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+
					  ":setFowlerSamplingParameters:SetFSParam failed:"+
					  setFSParamCommand.getReplyErrorCode()+":"+
					  setFSParamCommand.getReplyErrorString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1204);
				multRunDone.setErrorString("setFowlerSamplingParameters:SetFSParam failed:"+
							   setFSParamCommand.getReplyErrorCode()+":"+
							   setFSParamCommand.getReplyErrorString());
				multRunDone.setSuccessful(false);
				return false;
			}
			// calculate ramp overhead
			resetExecutionTime = status.getPropertyInteger("ioi.config.FOWLER.reset_execution_time");
			readExecutionTime = status.getPropertyInteger("ioi.config.FOWLER.read_execution_time");
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setFowlerSamplingParameters:resetExecutionTime = "+resetExecutionTime+
				",readExecutionTime = "+readExecutionTime+".");
			// there is one set of nReset resets, and TWO sets of nRead reads per AcquireRamp
			rampOverheadTime = (resetExecutionTime*nReset)+(2*nRead*readExecutionTime);
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setFowlerSamplingParameters:rampOverheadTime = "+rampOverheadTime+".");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":setFowlerSamplingParameters:SetFSParam failed:"+multRunCommand,e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1207);
			multRunDone.setErrorString(":setFowlerSamplingParameters:SetFSParam failed:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Configure the IDL Socket Server for a ramp in Read up the Ramp mode.
	 * <ul>
	 * <li>The number of resets to use is taken from the "ioi.config.UP_THE_RAMP.nreset" property value.
	 * <li>The number of reads to use is taken from the "ioi.config.UP_THE_RAMP.nread" property value.
	 * <li>The number of drops to use is taken from the "ioi.config.UP_THE_RAMP.ndrop" property value.
	 * <li>The length of time to do one group (one set of read and drops) in milliseconds is read from the 
	 *     "ioi.config.UP_THE_RAMP.group_execution_time" property value.
	 * <li>The number of groups to configure is computer from the group execution time and
	 *     the exposure length.
	 * <li>An instance of SetRampParamCommand is constructed, configured and used to send a 
	 *     SetRampParam command to the IDL Socket Server.
	 * </ul>
	 * If an error or exception occurs it is caught, a suitable error message put into MULTRUN_DONE, and false
	 * is returned.
	 * @param multRunCommand The MULTRUN command we are implementing.
	 * @param multRunDone The MULTRUN_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @param exposureLengthSeconds The exposure length in seconds. This is used to compute the number of
	 *        groups.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see #rampOverheadTime
	 * @see ngat.ioi.IOI#error
	 * @see ngat.ioi.command.SetFSParamCommand
	 */
	protected boolean setReadUpTheRampParameters(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,
						     double exposureLengthSeconds)
	{
		SetRampParamCommand setRampParamCommand = null;
		int nReset,nRead,nDrop,nGroup,groupExecutionTime,resetExecutionTime;

		try
		{
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setReadUpTheRampParameters:Configuring read-up-the-ramp mode.");
			nReset = status.getPropertyInteger("ioi.config.UP_THE_RAMP.nreset");
			nRead = status.getPropertyInteger("ioi.config.UP_THE_RAMP.nread");
			nDrop = status.getPropertyInteger("ioi.config.UP_THE_RAMP.ndrop");
			groupExecutionTime = status.getPropertyInteger("ioi.config.UP_THE_RAMP.group_execution_time");
			setRampParamCommand = new SetRampParamCommand();
			nGroup = ((int)(exposureLengthSeconds*1000/groupExecutionTime));
			if(nGroup < 1)
			{
				ioi.error(this.getClass().getName()+":setReadUpTheRampParameters:"+
					  "Computed nGroup too small:exposureLengthSeconds"+
					  exposureLengthSeconds+"*1000/groupExecutionTime"+
					  groupExecutionTime+" less than 1.");
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1216);
				multRunDone.setErrorString("processCommand:Computed nGroup too small:"+
							   "exposureLengthSeconds"+
							   exposureLengthSeconds+
							   "*1000/groupExecutionTime"+
							   groupExecutionTime+" less than 1.");
				multRunDone.setSuccessful(false);
				return false;
			}
			setRampParamCommand.setCommand(nReset,nRead,nGroup,nDrop,1);
			setRampParamCommand.sendCommand();
			if(setRampParamCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+
					  ":setReadUpTheRampParameters:SetRampParam failed:"+
					  setRampParamCommand.getReplyErrorCode()+":"+
					  setRampParamCommand.getReplyErrorString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1205);
				multRunDone.setErrorString("setReadUpTheRampParameters:SetRampParam failed:"+
							   setRampParamCommand.getReplyErrorCode()+":"+
							   setRampParamCommand.getReplyErrorString());
				multRunDone.setSuccessful(false);
				return false;
			}
			// as nGroup is computed from groupexecution time, the overhead is just the reset overhead
			resetExecutionTime = status.getPropertyInteger("ioi.config.UP_THE_RAMP.reset_execution_time");
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setReadUpTheRampParameters:resetExecutionTime = "+resetExecutionTime+".");
			rampOverheadTime = nReset*resetExecutionTime;
			ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
				":setReadUpTheRampParameters:rampOverheadTime = "+rampOverheadTime+".");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":setReadUpTheRampParameters:SetRampParam failed:"+multRunCommand,e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1208);
			multRunDone.setErrorString(this.getClass().getName()+
						   ":setReadUpTheRampParameters:SetRampParam failed:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to offset the telescope by a small amount in RA/Dec, to dither the sky.
	 * @param multRunCommand The command requiring this configuration to be done.
	 * @param multRunDone The done message, filled in if with a suitable error if the method failed.
	 * @param index The index in the list of exposures to do in the Multrun, from 0 to the number
	 *        of exposures specified by the MULTRUN. This number is used to calculate which
	 *        RA/Dec offset to use.
	 * @return The method returns true on success and false on failure.	 
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 */
	protected boolean offsetTelescope(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,int index) 
	{
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		int raDecOffsetCount,raDecOffsetIndex,offsetSleepTime;
		float raOffset,decOffset;
		boolean doRADecOffset,waitForOffsetToComplete;

	 // get configuration
		ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
			":offsetTelescope:Starting, retrieveing config.");
		try
		{
			doRADecOffset = status.getPropertyBoolean("ioi.multrun.offset.enable");
			waitForOffsetToComplete = status.getPropertyBoolean("ioi.multrun.offset.wait_for_complete");
			offsetSleepTime =  status.getPropertyInteger("ioi.multrun.offset.wait_sleep_time");
			raDecOffsetCount = status.getPropertyInteger("ioi.multrun.offset.count");
			raDecOffsetIndex = index % raDecOffsetCount;
			raOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".ra");
			decOffset = status.getPropertyFloat("ioi.multrun.offset."+raDecOffsetIndex+".dec");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":offsetTelescope:"+multRunCommand+":"+e.toString());
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1210);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		if(doRADecOffset)
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:We are going to physically move the telescope ("+
				raOffset+","+decOffset+").");
			// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC(multRunCommand.getId());
			offsetRaDecCommand.setRaOffset(raOffset);
			offsetRaDecCommand.setDecOffset(decOffset);
			instToISSDone = ioi.sendISSCommand(offsetRaDecCommand,serverConnectionThread,true,
							   waitForOffsetToComplete);
			// if we are waiting for the offset to complete, and it returns an error, return an error.
			if(waitForOffsetToComplete && (instToISSDone.getSuccessful() == false))
			{
				String errorString = null;
				
				errorString = new String("Offset Ra Dec failed:ra = "+raOffset+
							 ", dec = "+decOffset+":"+instToISSDone.getErrorString());
				ioi.error(errorString);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1211);
				multRunDone.setErrorString(this.getClass().getName()+":offsetTelescope:"+errorString);
				multRunDone.setSuccessful(false);
				return false;
			}
			// if we have not waited for the offset to complete, and we are supposed to be waiting a
			// configured time for the offset to have been done, wait a bit.
			if(!waitForOffsetToComplete)
			{
				ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
					":offsetTelescope:We have sent the telescope offset, "+
					"but have NOT waited for the DONE.");
				if(offsetSleepTime > 0)
				{
					ioi.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
						":offsetTelescope:We have sent the telescope offset, "+
						"but have NOT waited for the DONE, so are waiting here for "+
						offsetSleepTime+" ms.");
					try
					{
						Thread.sleep(offsetSleepTime);
					}
					catch(InterruptedException e)
					{
						ioi.error(this.getClass().getName()+
							  ":offsetTelescope:Offset sleep time was interrupted.",e);
					}
				}
			}
		}// end if
		else
		{
			ioi.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+
				":offsetTelescope:Offsets NOT enabled:"+
				"We are NOT going to physically move the telescope ("+raOffset+","+decOffset+").");
		}
		return true;
	}

	/**
	 * Method to reset the telescope offset to 0,0.
	 * @param multRunCommand The command requiring this configuration to be done.
	 * @param multRunDone The done message, filled in if with a suitable error if the method failed.
	 * @return The method returns true on success and false on failure.	 
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 */
	protected boolean resetTelescopeOffset(MULTRUN multRunCommand,MULTRUN_DONE multRunDone) 
	{
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		boolean doRADecOffset;

		try
		{
			doRADecOffset = status.getPropertyBoolean("ioi.multrun.offset.enable");
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":offsetTelescope:"+multRunCommand+":"+e.toString());
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1212);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		if(doRADecOffset)
		{
			// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC(multRunCommand.getId());
			offsetRaDecCommand.setRaOffset(0.0f);
			offsetRaDecCommand.setDecOffset(0.0f);
			instToISSDone = ioi.sendISSCommand(offsetRaDecCommand,serverConnectionThread);
			if(instToISSDone.getSuccessful() == false)
			{
				String errorString = null;
				
				errorString = new String("Resetting Offset Ra Dec failed:"+
							 instToISSDone.getErrorString());
				ioi.error(errorString);
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1213);
				multRunDone.setErrorString(this.getClass().getName()+
							   ":resetTelescopeOffset:"+errorString);
				multRunDone.setSuccessful(false);
				return false;
			}
		}// end if
		return true;
	}

	/**
	 * Send the command to the IDL Socket Server (AcquireRamp) to acquire the ramp.
	 * If an error or exception occurs it is caught, a suitable error message put into MULTRUN_DONE, and false
	 * is returned.
	 * @param multRunCommand The MULTRUN command we are implementing.
	 * @param multRunDone The MULTRUN_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see ngat.ioi.IOI#log
	 * @see ngat.ioi.IOI#error
	 * @see ngat.ioi.command.AcquireRampCommand
	 */
	protected boolean acquireRamp(MULTRUN multRunCommand,MULTRUN_DONE multRunDone)
	{
		AcquireRampCommand acquireRampCommand = null;

		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":acquireRamp:Acquiring ramp.");
		try
		{
			acquireRampCommand = new AcquireRampCommand();
			acquireRampCommand.sendCommand();
			if(acquireRampCommand.getReplyErrorCode() != 0)
			{
				ioi.error(this.getClass().getName()+
					  ":acquireRamp:AcquireRamp failed:"+
					  acquireRampCommand.getReplyErrorCode()+":"+
					  acquireRampCommand.getReplyErrorString());
				multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1209);
				multRunDone.setErrorString("acquireRamp:AcquireRamp failed:"+
							   acquireRampCommand.getReplyErrorCode()+":"+
							   acquireRampCommand.getReplyErrorString());
				multRunDone.setSuccessful(false);
				return false;
			}
		}
		catch(Exception e)
		{
			ioi.error(this.getClass().getName()+
				  ":acquireRamp:AcquireRampCommand failed:",e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1200);
			multRunDone.setErrorString("acquireRamp:AcquireRampCommand failed:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":acquireRamp:Finished acquiring ramp.");
		return true;
	}

	/**
	 * Method to send an ACK containing the filename (could actually be a directory) just taken back to the
	 * client, and to ensure the client connection is kept open.
	 * @param multRunCommand The MULTRUN command we are implementing.
	 * @param multRunDone The MULTRUN_DONE command object that will be returned to the client. We set
	 *       a sensible error message in this object if this method fails.
	 * @return We return true if the method succeeds, and false if an error occurs.
	 * @see #ioi
	 * @see #serverConnectionThread
	 * @see #rampOverheadTime
	 * @see ngat.ioi.IOI#log
	 * @see ngat.ioi.IOI#error
	 * @see ngat.message.ISS_INST.MULTRUN_ACK
	 */
	protected boolean sendMultrunACK(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,String filename)
	{
		MULTRUN_ACK multRunAck = null;

		// send acknowledge to say frames are completed.
		ioi.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			":sendMultrunACK:Sending ACK with exposure time "+multRunCommand.getExposureTime()+
			" plus ramp overhead "+rampOverheadTime+
			" plus default ACK time "+serverConnectionThread.getDefaultAcknowledgeTime()+".");
		multRunAck = new MULTRUN_ACK(multRunCommand.getId());
		multRunAck.setTimeToComplete(multRunCommand.getExposureTime()+rampOverheadTime+
					     serverConnectionThread.getDefaultAcknowledgeTime());
		multRunAck.setFilename(filename);
		try
		{
			serverConnectionThread.sendAcknowledge(multRunAck);
		}
		catch(IOException e)
		{
			ioi.error(this.getClass().getName()+":sendMultrunACK:sendAcknowledge:",e);
			multRunDone.setErrorNum(IOIConstants.IOI_ERROR_CODE_BASE+1202);
			multRunDone.setErrorString("sendMultrunACK:sendAcknowledge:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		return true;
	}
}
