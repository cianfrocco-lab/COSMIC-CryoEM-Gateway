package org.ngbw.web.actions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.controllers.FolderController;
import org.ngbw.sdk.DataDetails;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;

/*
	10/2012 Terri Liebowitz Schwartz

	This code, as originally written, reads the whole file into memory which really limits the size of files
	we can upload. It also assumes the file is text and tries to guess the character encoding and inserts
	CR/NLs, which of course means we can't upload binary files.

	I'm going to assume files are either binary or ascii text (no fancy character set encodings) and avoid
	reading the whole file into memory at once.  If I remember, my changes for this will be marked with "BIG_FILES".
*/

/**
 * Struts action class to handle creation (upload/edit/search)
 * of user data in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class CreateData extends DataManager
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(CreateData.class.getName());
	// session attribute key constants
	public static final String UPLOADED_FILES = "uploadedFiles";

	// file upload result values
	public static final String UPLOAD_STATUS = "uploadStatus";
	public static final String UPLOAD_SUCCESS = "SUCCESS";
	public static final String UPLOAD_ERROR = "ERROR: No uploaded files were found.";
	public static final String UPLOAD_ERROR_PREFIX = "ERROR: ";

	// data upload preference keys
	public static final String UPLOAD_ENTITY_TYPE = "Upload_Entity_Type";
	public static final String UPLOAD_DATA_TYPE = "Upload_Data_Type";
	public static final String UPLOAD_DATA_FORMAT = "Upload_Data_Format";

	/*================================================================
	 * Properties
	 *================================================================*/
	// data item upload/edit form properties
	private UserDataItem dataItem;
	private String entityType;
	private String dataType;
	private String dataFormat;
	private String source = null;
	//private File upload;
	private File[] uploads;
	//private String uploadFileName;
	private String[] uploadFileNames;

	/*================================================================
	 * Action methods
	 *================================================================*/
	@SkipValidation
	public String upload() {
		// clear current data item and data form, since this is a new data item
		clearCurrentData();
		clearUploadedFiles();

		// pre-populate last selected upload SourceDocumentType
		Folder currentFolder = getCurrentFolder();
		FolderController controller = getFolderController();
		setEntityType(controller.getFolderPreference(currentFolder, UPLOAD_ENTITY_TYPE));
		setDataType(controller.getFolderPreference(currentFolder, UPLOAD_DATA_TYPE));
		setDataFormat(controller.getFolderPreference(currentFolder, UPLOAD_DATA_FORMAT));
		return INPUT;
	}

	/*
		This is the entry point for multiple file upload via the JUploadApplet 
		BIG_FILES: Where's the documentation for how JUploadApplet works with the struts2 FileUploadInterceptor?
		I assume the interceptor is being called because someone is putting the file content into a tmp file.
		But why do we have to parse the request params?  I'm surprised the inteceptor doesn't provide the
		usual methods for getting the file objects, names, etc.
	*/
	@SuppressWarnings("unchecked")
	public String uploadFiles() 
	{
		Map parameters = getParameters();
		clearSessionAttribute(UPLOAD_APPLET_ERROR);
		if (parameters != null && parameters.size() > 0) 
		{
			// build file map to save to session
			// maps an integer to a [file-contents][filename pair].
			Map<Integer, Object[]> files = new HashMap<Integer, Object[]>();

			// traverse parameters for uploaded files
			for (Object parameter : parameters.keySet()) 
			{
				logger.debug("parameter key is: " + parameter.toString());
				/*
					I don't know how anyone figured this out, but I guess the struts2 FileUploadInterceptor is uploading the 
					files to a temp directory and giving us 2 types of parameters:
						1. FileN = Java File Object
						2. FileNFilename = filename (just the name, no path, as it was sent from the user's computer).

					We extract the index, N, from whichever type of key it is, then put the file object and the filename
					into a map, indexed by the key.  Caller doesn't really need the index, just needs the matched up
					filenames and File objects.
				*/
				Integer index = null;
				if (parameter.toString().matches("^File[\\d]+.*$")) try 
				{
					// remove all non-digits to get just the numeric part (^ means anything but ...).
					// isn't this going to fail if the filename starts with a digit?  
					index = Integer.valueOf(parameter.toString().replaceAll("[^\\d]+", ""));
				} catch (NumberFormatException error) {}

				// if an index was extracted, proceed
				if (index != null) 
				{
					// retrieve stored information for this file, if present
					Object[] file = files.get(index);
					if (file == null)
						file = new Object[2];

					// retrieve the value of this parameter and process it
					Object value = parameters.get(parameter);

					// if this parameter is file content, add it to the file info array
					if (parameter.toString().equals("File" + index)) 
					{
						try 
						{
							//((File[])value)[0] is the File Object.  I guess the File upload interceptor put the data there?
							File f = ((File[])value)[0];
							file[0] = f;
							logger.debug("index " + index + ", tmp file is: " +  f.getAbsolutePath());
						} catch (Throwable error) 
						{
							reportError(error, "Error retrieving what we thought was an uploaded file");
						}

					// if this parameter is a file name, add it to the file info array
					} else if (parameter.toString().equals("File" + index +"FileName")) 
					{
						try 
						{
							logger.debug("index " + index + ", filename is: " + ((String[])value)[0]);
							file[1] = ((String[])value)[0];
						} 
						catch (Throwable error) 
						{
							reportError(error, "Error retrieving what we thought was " + "the name of an uploaded file.");
						}

					}
					// store file info array back to map
					files.put(index, file);
				} else
				{
					logger.debug("index is null");
				}
			}

			// ensure that file map is complete and consistent
			if (files != null && files.size() > 0) 
			{
				boolean uploadSuccessful = true;
				for (Integer index : files.keySet()) 
				{
					Object[] file = files.get(index);
					if (file == null || file.length != 2 || file[0] == null || file[1] == null) 
					{
						uploadSuccessful = false;
						break;
					}
				}

				// if everything is in order, add files to session
				if (uploadSuccessful)
				{
					addUploadedFiles(files);
					saveDataItems();
				} else
				{
					reportUserError("Error uploading.");
				}
			}
		}
		return UPLOAD_STATUS;
	}

	/*
		This method is invoked by struts for the uploadFiles action (see struts.xml)
		where we say the result type is "stream" and inputName is "uploadStatus"
		which means this method, "getUploadStatus()"  is called to get the stream 
		that will be returned (to the applet).  Applet looks for "ERROR: " in the 
		stream to know whether to report success or failure.
	*/
	public InputStream getUploadStatus() {
		logger.debug("In getUploadStatus()");
		Map<Integer, Object[]> files = getUploadedFiles();
		String result = null;
		if (files != null && files.size() > 0)
		{
			if (getSessionAttribute(UPLOAD_APPLET_ERROR) != null)
			{
				result = "ERROR: " + getSessionAttribute(UPLOAD_APPLET_ERROR);
			} else
			{
				result = UPLOAD_SUCCESS;
			}
		} else 
		{
			result = UPLOAD_ERROR;
		}
		return new ByteArrayInputStream(result.getBytes());
	}

	public String executeUpload() {
		if (validateUpload())
			return execute();
		else return INPUT;
	}

	public String executePaste() {
		if (validatePaste())
			return execute();
		else return INPUT;
	}

	@SkipValidation
	public String edit() {
		UserDataItem currentData = getCurrentData();
		if (currentData != null) {
			setDataItem(currentData);
			return INPUT;
		} else {
			addActionError("You must select a data item to edit its details.");
			return LIST;
		}
	}

	public String execute() {
		// save the data items
		try {
			saveDataPreferences();
			int saved = saveDataItems();
			if (saved < 1) {
				reportUserError("No data items were saved.");
				return INPUT;
			} else {
				String message = saved + " data item";
				if (saved != 1)
					message += "s";
				reportUserMessage(message + " successfully saved.");
				if (getCurrentData() != null)
					return DISPLAY;
				else return LIST;
			}
		} catch (Throwable error) {
			reportError(error, "Error saving data items");
			return ERROR;
		}
	}

	@SkipValidation
	public String reload() {
		try {
			resolveConcepts();
			return INPUT;
		} catch (Throwable error) {
			reportError(error, "Error resolving UserDataItem properties");
			return ERROR;
		}
	}

	@SkipValidation
	public String cancel() {
		// discard input and return
		addActionMessage("File not saved.");
		UserDataItem currentData = getCurrentData();
		if (getCurrentData() == null)
			return LIST;
		else {
			setDataItem(currentData);
			return DISPLAY;
		}
	}

	/*================================================================
	 * Upload form property accessor methods
	 *================================================================*/
	public UserDataItem getDataItem() {
		try {
			if (dataItem == null)
				setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
			return dataItem;
		} catch (Throwable error) {
			reportError(error, "Error retrieving UserDataItem instance");
			return null;
		}
	}

	public void setDataItem(UserDataItem dataItem) {
		this.dataItem = dataItem;
	}

	public String getLabel() {		
		if (dataItem != null)
		{
			return dataItem.getLabel();
		}
		else return null;
	}

	public void setLabel(String label) {
		try {
			if (dataItem == null)
				setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
			dataItem.setLabel(label);
		} catch (Throwable error) {
			reportError(error, "Error retrieving UserDataItem instance");
		}
	}

	@SuppressWarnings("unchecked")
	public Map<Integer, Object[]> getUploadedFiles() {
		return (Map<Integer, Object[]>)getSessionAttribute(UPLOADED_FILES);
	}

	public void setUploadedFiles(Map<Integer, Object[]> files) {
		if (files == null || files.size() < 1)
			clearUploadedFiles();
		else setSessionAttribute(UPLOADED_FILES, files);
	}

	public void clearUploadedFiles() {
		clearSessionAttribute(UPLOADED_FILES);
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = stripCarriageReturns(source);
	}

	public File[] getUpload() {
		return uploads;
	}

	// This function is called automatically by strut after user upload file(s)
	public void setUpload(File[] uploads) {
		this.uploads = uploads;
	}

	public String[] getUploadFileName() {
		return uploadFileNames;
	}

	public void setUploadFileName(String[] uploadFileNames) {
		this.uploadFileNames = uploadFileNames;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public String getDataFormat() {
		return dataFormat;
	}

	public void setDataFormat(String dataFormat) {
		this.dataFormat = dataFormat;
	}

	/*================================================================
	 * User interface property accessor methods
	 *================================================================*/
	public Map<String, String> getEntityTypes() {
		try {
			return mapConceptSet(getEntityTypeSet(), "EntityType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of entity types");
			return null;
		}
	}

	public Map<String, String> getDataTypes() {
		String entityType = getEntityType();
		try {
			if (entityType == null || entityType.equals("") ||
				entityType.equals(UNKNOWN))
				return mapConceptSet(getUnknownDataTypeSet(), "DataType");
			else return mapConceptSet(
				getDataTypeSet(EntityType.valueOf(entityType)), "DataType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of data types");
			return null;
		}
	}

	public Map<String, String> getDataFormats() {
		String entityType = getEntityType();
		String dataType = getDataType();
		try {
			if (entityType == null || entityType.equals("") || entityType.equals(UNKNOWN) ||
				dataType == null || dataType.equals("") || dataType.equals(UNKNOWN))
				return mapConceptSet(getUnknownDataFormatSet(), "DataFormat");
			else return mapConceptSet(
				getDataFormatSet(EntityType.valueOf(entityType), DataType.valueOf(dataType)),
					"DataFormat");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of registered data formats for " +
				"specified entity type and data type");
			return null;
		}
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/

	protected void addUploadedFiles(Map<Integer, Object[]> files) 
	{
		if (files != null && files.size() > 0) 
		{
			Map<Integer, Object[]> uploadedFiles = getUploadedFiles();
			if (uploadedFiles == null)
			{
				uploadedFiles = new HashMap<Integer, Object[]>(files.size());
			}
			for (Integer index : files.keySet()) 
			{
				uploadedFiles.put(index, files.get(index));
				reportUserMessage("File \"" + (String)(files.get(index)[1]) + "\" added to session cache of uploaded files.");
			}
			setUploadedFiles(uploadedFiles);
		}
	}

    /**
     * This function will determine the data type based on the given
     * filename's extension.
     **/
    protected DataType determineDataType ( String filename )
    {
        String ext = FilenameUtils.getExtension ( filename );
        DataType type = DataType.valueOf ( UNKNOWN );

        if ( filename == null )
            return ( type );

        switch ( ext ) {
            case "bmp" :
            case "gif" :
            case "jpg" :
            case "jpeg" :
            case "png" :
                type = DataType.valueOf ( "IMAGE" );
                break;

            case "pdf" :
                type = DataType.valueOf ( "PDF" );
                break;

            case "gz" :
            case "zip" :
                type = DataType.valueOf ( "ZIP" );
                break;

            case "txt" :
                type = DataType.valueOf ( "TEXT" );
                break;

            case "star" :
                type = DataType.valueOf ( "STAR" );
                break;

            case "mrc" :
                type = DataType.valueOf ( "MRC" );
                break;
        }

        return ( type );
    }
    
	protected void saveDataPreferences() {
		if (resolveConcepts() == false)
			return;
		else {
			Folder currentFolder = getCurrentFolder();
			FolderController controller = getFolderController();
			controller.setFolderPreference(currentFolder, UPLOAD_ENTITY_TYPE, getEntityType());
			controller.setFolderPreference(currentFolder, UPLOAD_DATA_TYPE, getDataType());
			controller.setFolderPreference(currentFolder, UPLOAD_DATA_FORMAT, getDataFormat());
			try {
				getWorkbenchSession().saveFolder(currentFolder);
			} catch (Throwable error) {
				reportError(error, "Error saving data upload preferences for the current folder");
			}
		}
	}

	// BIG_FILES changes
	protected int saveDataItems() 
	{
		int saved = 0;
		
		/* MONA: no longer needed as we are not uploading from applet anymore!
		Map<Integer, Object[]> uploads = getUploadedFiles();
		// upload from applet, potentially multiple files
		if (uploads != null && uploads.size() > 0) 
		{
			for (Integer index : uploads.keySet()) 
			{
				Object[] file = uploads.get(index);
				try 
				{
					saveDataItem((String)file[1],  null, (File)file[0]);
					saved++;
				} catch (Throwable error) 
				{
					// Turns out the applet only shows error message up to a newline, so we
					// won't see multiple error messages anyway ...
					String errorMsg  = (String)getSessionAttribute(UPLOAD_APPLET_ERROR);
					setSessionAttribute(UPLOAD_APPLET_ERROR, 
						"Error saving data item \"" + (String)file[1] + "\": " + error.getMessage() + 
						((errorMsg == null) ? "" : errorMsg) );
					reportUserError(error, "Error saving data item \"" + (String)file[1] + "\": " + error.getMessage());
					continue;
				}
			}
			clearCurrentData();
		}
		// upload from main upload page, single file -> now multiple files!
		else
		{
		*/
			// get source data
			String data = null;     // No paste (source) for COSMIC2
			//String data = stripCarriageReturns ( getSource().trim() );
			File uploads[] = getUpload();
			String filenames[] = getUploadFileName();
			String label = null;
			
			// If there is a paste, save it
			if ( data != null && data.length() > 0 )
			{
				label = getLabel();
				
				try 
				{
					dumpHeapSize ( "Before saveDataItem" );
					setCurrentData ( saveDataItem ( label, data, null ) );
					//saveDataItem ( label, data, uploads[i] );
					dumpHeapSize ( "After saveDataItem" );
					saved++;
				}
				catch ( Throwable error ) 
				{
					reportUserError ( error, "Error saving data item \"" + label + "\"");
					return 0;
				}
			}
				
			// If this is a file upload, as opposed to a paste of text, upload will be non null.
			if (uploads != null)
			{
				//long length = upload.length();
				//logger.debug("Uploaded file of size: " + length + " bytes (" + length/(1024 * 1024) + " Meg)"); 
				dumpHeapSize("Before upload");
			//}
			
				for ( int i = 0; i < uploads.length; i++ )
				{
					try
					{
						if (uploads[i] != null && uploads[i].canRead())
						{
							// BIG_FILES: leave the data alone!  Don't even read it yet.
							//data = stripCarriageReturns(getEncodedData(upload));
							//dumpHeapSize("After upload");
						}
						else 
						{
							// pasted data.
							data = stripCarriageReturns(getSource());
						}
					}
					catch(java.lang.OutOfMemoryError e)
					{
						dumpHeapSize("Out of memory error reading file into memory:");
						reportUserError("File too large: there isn't enough memory available for the upload.");
						return 0;
					}
	
					label = filenames[i];
					
					try 
					{
						dumpHeapSize("Before saveDataItem");
						//setCurrentData(saveDataItem(label, data, uploads[i] ));
						saveDataItem ( label, data, uploads[i] );
						dumpHeapSize("After saveDataItem");
						//saved = 1;
						saved++;
					} catch (Throwable error) 
					{
						reportUserError(error, "Error saving data item \"" + label + "\"");
						return 0;
					}
				}
			} // end if (uploads != null)
		//}
		// BIG_FILES
		dumpHeapSize("Before refreshFolderDataTabs:");
		refreshFolderDataTabs();
		dumpHeapSize("After refreshFolderDataTabs:");
		
		return saved;
	}

	/*
		BIG_FILES changes.  Added upload parameter so we can stream from it, instead of expecting
		data to be in memory in "data" parameter.

		If upload != null, we're uploading a file.  If data != null, we're getting pasted text.
	*/
	protected UserDataItem saveDataItem(String label, String data, File upload) throws WorkbenchException 
	{
		// get current folder
		Folder folder = getCurrentFolder();
		if (folder == null)
		{
			throw new WorkbenchException("You must select a folder " + "to save a data item.");
		}
		// get data label
		if (label == null || label.equals(""))
		{
			throw new WorkbenchException("You must provide a label " + "to save a data item.");
		}
		// get data contents
		if (upload == null)
		{
			if ( data == null || data.equals(""))
			{
				throw new WorkbenchException("You must provide some source data " + "to save a data item.");
			}
		}
		
		/* MONA: we are not using the Entity Type, and Data Format fields anymore...
		// get data concepts
		EntityType entityType = null;
		DataFormat dataFormat = null;
		DataType dataType = null;

		// Get entity type, data type and data format that user entered, or set to UNKNOWN
		String type = getEntityType();
		if (type != null && type.length() != 0)
			entityType = EntityType.valueOf(type);
		else
			entityType = EntityType.valueOf(UNKNOWN);

		type = getDataType();
		if (type != null && type.length() != 0)
			dataType = DataType.valueOf(type);
		else
			dataType = DataType.valueOf(UNKNOWN);
		type = getDataFormat();
		if (type != null && type.length() != 0)
			dataFormat = DataFormat.valueOf(type);
		else
			dataFormat = DataFormat.valueOf(UNKNOWN);

        logger.debug ( "*** MONA: entityType = " + entityType );
        logger.debug ( "*** MONA: dataType = " + dataType );
        logger.debug ( "*** MONA: dataFormat = " + dataFormat );
        */

		DataType dataType = determineDataType ( label );
		EntityType entityType = EntityType.valueOf ( UNKNOWN );
		DataFormat dataFormat = DataFormat.valueOf ( UNKNOWN );

		/*
			Terri added this code to test out the ability to guess the dataformat.  I made it conditional
			on the type being UNKNOWN, UNKNOWN, UNKNOWN so that it shouldn't do any harm in ngbw, but
			I don't think that's what the condition should really be in cipres and I'm not sure if what it
			should be in ngbw.  But I figured this way at least we shouldn't be erasing any useful info that
			the user entered.

			BUG, I think: file size in bytes isn't being displayed when we set dataformat with this method.
		*/
		if (upload == null)
		{
			if (dataFormat == DataFormat.valueOf(UNKNOWN))
			{
				// BIG_FILES: TODO: need to create a version of this that reads from the file.  Only call it on ascii text files.
				dataFormat = DataDetails.diagnoseFormat(data);
			}
		}
				
		// retrieve data item instance
		UserDataItem dataItem = null;
		try 
		{
			dataItem = getWorkbenchSession().getUserDataItemInstance(folder);
		} catch (Throwable error) 
		{
			throw new WorkbenchException(error);
		}
		// populate data item
		dataItem.setLabel(label);
		dataItem.setEntityType(entityType);
		dataItem.setDataType(dataType);
		dataItem.setDataFormat(dataFormat);

		// BIG_FILES: call stream based method to save uploaded file.  
		if (upload != null)
		{
			FileInputStream is;
			try 
			{
				Workbench.convertEncoding(upload);
				is = new FileInputStream(upload);
				dataItem.setData(is);
			} catch (Exception e) 
			{
				throw new WorkbenchException(e);
			}
		} else
		{
			dataItem.setData(data);
		}

		// save data item
		try 
		{
			dataItem = getWorkbenchSession().saveUserDataItem(dataItem, folder);
		} catch (Throwable error) 
		{
			throw new WorkbenchException(error);
		}
		return dataItem;
	}

	protected boolean resolveConcepts() 
	{
		String entityType = getEntityType();
		String dataType = getDataType();
		String dataFormat = getDataFormat();
		if (entityType == null || entityType.equals("")) 
		{
			setEntityType(null);
			setDataType(null);
			setDataFormat(null);
			return false;
		} else 
		{
			Set<DataType> dataTypes = getDataTypeSet(EntityType.valueOf(entityType));
			if (dataType == null || dataType.equals("") || dataTypes == null ||
				dataTypes.contains(DataType.valueOf(dataType)) == false) 
				{
				setDataType(null);
				setDataFormat(null);
				return false;
			} else 
			{
				Set<DataFormat> dataFormats =
					getDataFormatSet(EntityType.valueOf(entityType), DataType.valueOf(dataType));
				if (dataFormat == null || dataFormat.equals("") || dataFormats == null ||
					dataFormats.contains(DataFormat.valueOf(dataFormat)) == false) 
					{
					setDataFormat(null);
					return false;
				} else return true;
			}
		}
	}

	protected boolean validateData() 
	{
		UserDataItem dataItem = getDataItem();
		if (dataItem == null)
			return false;
		else 
		{
			String label = getLabel();
			if (label == null || label.equals("")) 
			{
				addFieldError("label", "Label is required.");
				return false;
			}
			String source = getSource();
			File upload[] = getUpload();
			
			/* MONA: updated needed!
			if ((source == null || source.equals("")) &&
				(upload == null || upload.canRead() == false)) 
			{
				addActionError("You must either upload a file or enter your data directly.");
				return false;
			} else 
			{
				return true;
			}
			*/
			return true;
		}
	}

	protected boolean validateUpload() {
		Map<Integer, Object[]> uploads = getUploadedFiles();
		if (uploads == null || uploads.size() < 1) {
			addActionError("You must select one or more files to upload.");
		}
		if (hasFieldErrors())
			return false;
		else return true;
	}

	protected boolean validatePaste() {
		String label = getLabel();
		String filenames[] = getUploadFileName();
		/*
		if ((label == null || label.equals("")) &&
			(filenames == null || filenames.equals(""))) {
		*/
		if ( ( label == null || label.equals ( "" ) ) && filenames == null )
		{
			addFieldError("label", "You must provide a label " +
				"if you are entering your data directly.");
		}
		String source = getSource();
		File uploads[] = getUpload();
		if ((source == null || source.equals("")) &&
			( uploads == null || uploads.length == 0 ) )
		{
			//(uploads == null || upload.canRead() == false)) {
			addActionError("You must either upload a file or " +
				"enter your data directly.");
			return false;
		}
		if (hasFieldErrors())
			return false;
		else return true;
	}


}
