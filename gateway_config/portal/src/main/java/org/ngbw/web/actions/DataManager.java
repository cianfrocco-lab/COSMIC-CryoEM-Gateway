package org.ngbw.web.actions;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.struts2.interceptor.validation.SkipValidation;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.core.GenericDataRecordCollection;
import org.ngbw.sdk.core.shared.IndexedDataRecord;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.core.types.RecordFieldType;
import org.ngbw.sdk.core.types.RecordType;
import org.ngbw.sdk.database.DataRecord;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.model.Page;
import org.ngbw.web.model.Tab;
import org.ngbw.web.model.TabbedPanel;
import org.ngbw.web.model.impl.ConceptComparator;
import org.ngbw.web.model.impl.ListPage;
import org.ngbw.web.model.impl.RecordFieldTypeComparator;

import edu.sdsc.globusauth.util.OauthConstants;


/**
 * Struts action class to process all user data-related requests
 * in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class DataManager extends FolderManager
{
	//TODO: Implement this class with a proper DataController
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(DataManager.class.getName());
	// parameter attribute key constants
	public static final String PAGE = "page";
	public static final String SORT = "sort";

	// session attribute key constants
	public static final String CURRENT_DATA = "currentData";
	public static final String FOLDER_DATA = "folderData";
	public static final String SELECTED_DATA_ITEMS = "selectedDataItems";

	// result constants
	public static final String DOWNLOAD = "download";
	public static final String ARCHIVE_DATA = "archiveData";

	// data tab properties
	public static final String DATA_TAB_SORT_TYPE = "Data_Tab_Sort_Type";
	public static final String DATA_TAB_TYPE = "dataTabType";
	public static final String RECORD_TYPE = "recordType";
	public static final String DATA_FORMAT = "dataFormat";
	public static final String DATA_RECORDS = "dataRecords";
	public static final String UNKNOWN_TAB = "Unknown";
	public static final String ALL_DATA_TAB = "All Data";
	public static final String DATA_PAGE_SIZE = "dataPageSize";

	// data action constants
	public static final String MOVE = "Move";
	public static final String COPY = "Copy";
	public static final String DELETE = "Delete Selected";

	// record type constants
	public static final String COMPOUND_STRUCTURE = "COMPOUND_STRUCTURE";
	public static final String APPLICATION_DATA = "APPLICATION_DATA_SERIALIZED_BINARY";

	// semantic tag constants
	public static final String UNKNOWN = "UNKNOWN";

	// maximum size (in bytes) of data to be displayed
	public static final int MAX_DATA_SIZE = 2097152;	// 2 MiB

	// TODO: for debugging, setting this to a ridiculously low value instead of 2MB.
	// public static final int MAX_DATA_SIZE = 1000;	// 200 bytes

	// file download default constants
	public static final String DEFAULT_FILENAME = "output";
	public static final String DEFAULT_CONTENT_TYPE = "text/plain";

	// Sirius type constants
	public static final int DATA = -1;
	public static final int VIEW = 3;

	/*================================================================
	 * Properties
	 *================================================================*/
	// cached current data item
	private UserDataItem currentData;

	// data list form properties
	private List<RecordFieldType> fields;
	private String[] selectedIds;
	private String dataAction;
	private Long targetFolder;

	// data display page properties
	protected GenericDataRecordCollection<IndexedDataRecord> dataRecords;
	protected SourceDocument sourceDocument;

	/*================================================================
	 * Action methods
	 *================================================================*/
	public String list()
    {
        //logger.debug ( "MONA : entered list()" );
		Folder folder = getRequestFolder(ID);
        //logger.debug ( "MONA : folder 1 = " + folder );
		if (folder == null)
			folder = getCurrentFolder();
        //logger.debug ( "MONA : folder 2 = " + folder );
		if (folder == null) {
			reportUserError("You must select a folder to view its data.");
			return HOME;
		} else {
			if (isCurrentFolder(folder) == false)
				setCurrentFolder(folder);
			TabbedPanel<? extends FolderItem> tabs = getFolderDataTabs();
			String tab = getRequestParameter(TAB);
			if (tab != null)
				tabs.setCurrentTab(tab);
			clearCurrentData();
			return LIST;
		}
	}

	@SkipValidation
	public String paginate() {
        //logger.debug ( "MONA : DataManager.paginate()" );
		setPageNumber(0);
		return LIST;
	}

	@SkipValidation
	public String setPage() {
        //logger.debug ( "MONA : DataManager.setPage()" );
		String page = getRequestParameter(PAGE);
        //logger.debug ( "MONA : page = " + page );
		if (page != null)
			setPageNumber(Integer.parseInt(page));
		else addActionError("You must select a page number to change pages.");
		return LIST;
	}

	@SkipValidation
	public String changeTabSorting() {
		String oldSortType = getDataTabSortType();
		if (oldSortType == null || oldSortType.equals(RECORD_TYPE)) {
			setDataTabSortType(DATA_FORMAT);
		} else setDataTabSortType(RECORD_TYPE);
		refreshFolderDataTabs();
		return LIST;
	}

	@SkipValidation
	public String display() {
        //logger.debug ( "MONA : entered display()" );
		// get selected data ID from request param, if present
		String[] dataId = (String[])getParameters().get(ID);
		if (dataId != null && dataId.length > 0) {
			setCurrentData(getSelectedData(Long.parseLong(dataId[0])));
			return DISPLAY;
		} else if (getCurrentData() != null) {
			return DISPLAY;
		} else {
			addActionError("You must select a data item to view its details.");
			return LIST;
		}
	}

	@SkipValidation
	public String download() {
		return DOWNLOAD;
	}

	@SkipValidation
	public String delete() {
        //logger.debug ( "MONA: entered DataManager.delete()" );
		// delete the current data item
		try {
			UserDataItem currentData = getCurrentData();
            //logger.debug ( "MONA: currentData = " + currentData );
			if (currentData == null) {
				reportUserError("You must select a data item to delete it.");
			} else {
				String dataLabel = currentData.getLabel();
                //logger.debug ( "MONA: dataLabel = " + dataLabel );
				if (deleteData(currentData)) {
					setCurrentData(null);
					reportUserMessage("Data item \"" + dataLabel + "\" successfully deleted.");
					refreshFolderDataTabs();
				} else {
					reportUserError("Data item \"" + dataLabel + "\" could not be deleted.");
					return DISPLAY;
				}
			}
			return LIST;
		} catch (Throwable error) {
			reportError(error, "Error deleting data item");
			return ERROR;
		}
	}

	@SkipValidation
	public String cancel() {
		String dataAction = getDataAction();
		if (dataAction == null) {
			addActionError("You must select an action to manipulate your data.");
		} else {
			String[] selectedIds = getSelectedIds();
			if (selectedIds == null || selectedIds.length < 1) {
				addActionError("You must select one or more data items to " +
					dataAction.toLowerCase() + " them.");
			} else {
				/**/
				String[] button = (String[])getParameters().get("method:cancel");
				if (button != null && button.length > 0 && button[0].equals(DELETE))
					return deleteSelected();
				/**/
				Folder folder = getCurrentFolder();
				Long targetFolder = getTargetFolder();
				if (targetFolder == null) {
					addActionError("You must select a target folder to " +
						dataAction.toLowerCase() + " your data.");
				} else if (targetFolder.equals(folder.getFolderId())) {
					addActionError("You must select a new folder to " +
						dataAction.toLowerCase() + " your data into.");
				} else if (dataAction.equalsIgnoreCase(MOVE)) {
					int moved = moveSelectedDataItems();
					String result = moved + " data item";
					if (moved != 1) result += "s were ";
					else result += " was ";
					result += "successfully moved.";
					reportUserMessage(result);
					refreshFolderDataTabs();
				} else if (dataAction.equalsIgnoreCase(COPY)) {
					int copied = copySelectedDataItems();
					String result = copied + " data item";
					if (copied != 1) result += "s were ";
					else result += " was ";
					result += "successfully copied.";
					reportUserMessage(result);
					refreshFolderDataTabs();
				} else {
					addActionError("You have requested an unrecognized data action, " +
						"please select from the list below.");
				}
			}
		}
		return LIST;
	}

	@SkipValidation
	public String deleteSelected() {
		String[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1) {
			addActionError("You must select one or more data items to delete them.");
		} else {
			int deleted = deleteSelectedDataItems();
			int remaining = selectedIds.length - deleted;
			if (deleted > 0) {
				String result = deleted + " data item";
				if (deleted != 1) result += "s were ";
				else result += " was ";
				result += "successfully deleted.";
				reportUserMessage(result);
				refreshFolderDataTabs();
			}
			if (remaining > 0) {
				String result = remaining + " data item";
				if (remaining != 1) result += "s";
				result += " could not be deleted.";
				reportUserError(result);
			}
		}
		setCurrentData(null);
		return LIST;
	}

	/*================================================================
	 * Data list form property accessor methods
	 *================================================================*/
	public String[] getSelectedIds() {
		return selectedIds;
	}

	public void setSelectedIds ( String[] selectedIds )
    {
		this.selectedIds = selectedIds;
	}

	public String getDataAction() {
		return dataAction;
	}

	public void setDataAction(String dataAction) {
		this.dataAction = dataAction;
	}

	public Long getTargetFolder() {
		return targetFolder;
	}

	public void setTargetFolder(Long targetFolder) {
		this.targetFolder = targetFolder;
	}

	/*================================================================
	 * Data list page property accessor methods
	 *================================================================*/
	@SuppressWarnings("unchecked")
	public TabbedPanel<? extends FolderItem> getFolderDataTabs() {
        //logger.debug ( "MONA : entered getFolderDataTabs()" );
		TabbedPanel<? extends FolderItem> folderData =
			(TabbedPanel<UserDataItem>)getSessionAttribute(FOLDER_DATA);
        //logger.debug ( "MONA : folderData = " + folderData );
		if (folderData == null ||
			folderData.isParentFolder(getCurrentFolder()) == false)
			folderData = refreshFolderDataTabs();
		return folderData;
	}

	@SuppressWarnings("unchecked")
	public void setFolderDataTabs(TabbedPanel<? extends FolderItem> folderData) {
        //logger.debug ( "MONA : entered DataManager.setFolerDataTabs" );
        //logger.debug ( "MONA : folderData = " + folderData );
		setSessionAttribute(FOLDER_DATA, folderData);
	}

	public boolean hasFolderData() {
        //logger.debug ( "MONA: entered DataManager.hasFolderData()" );
		TabbedPanel<? extends FolderItem> folderData = getFolderDataTabs();
        //logger.debug ( "MONA : folderData = " + folderData );
        //logger.debug ( "MONA : folderData 2 = " + folderData.getTabs().getTotalNumberOfElements() );
		return (folderData != null && folderData.getTabs().getTotalNumberOfElements() > 0);
	}

	public List<String> getTabLabels() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else return folderData.getTabLabels();
	}

	public String getFirstTabLabel() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else return folderData.getFirstTabLabel();
	}

	public String getCurrentTabLabel() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<?> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return null;
			else return currentTab.getLabel();
		}
	}

	public boolean isCurrentTabUnknown() {
		String currentTabLabel = getCurrentTabLabel();
		if (currentTabLabel == null)
		{
			return false;
		}
		boolean flag = currentTabLabel.equals(UNKNOWN_TAB);
		return flag;
	}

	public boolean isCurrentTabPhysical() 
	{
		try
		{
			String dataTabSortType = getDataTabSortType();
			if (dataTabSortType.equals(RECORD_TYPE) == false)
			{
				return true;
			}
			String currentTabLabel = getCurrentTabLabel();
			if (currentTabLabel == null)
			{
				return false;
			}
			boolean flag = currentTabLabel.equals(ALL_DATA_TAB);
			return flag;
		}
		catch (Exception e)
		{
			logger.error("", e);
			return false;
		}
	}
		

	public int getCurrentTabSize() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return 0;
		else {
            //logger.debug ( "MONA : currentTabSize = " + folderData.getCurrentTabSize() );
            return folderData.getCurrentTabSize();
        }
	}

    
	//TODO: add sort functionality to user interface
	public List<?> getCurrentDataTab() 
	{
        //logger.debug ( "MONA: entered getCurrentDataTab()" );
        // added this or else new Globus transfer files won't show up in UI
        // without logout/login...
        // However, during debugging of multipage listing problem (github
        // issue #87), it appears this call is not needed and has makes no
        // difference on issue #87...
		//refreshFolderDataTabs();
		try
		{
			TabbedPanel<?> folderData = getFolderDataTabs();

			if (folderData == null)
				return null;
			else {
                logger.debug ( "content = " + folderData.getCurrentTabContents() );
                return folderData.getCurrentTabContents();
            }
		}
		catch(Exception e)
		{
			logger.error("", e);
			return null;
		}
	}


    // Added by Mona to also show new Globus directory data.  Function
    // modelled after the above getCurrentDataTab()
	//TODO: add sort functionality to user interface
	public List<? extends FolderItem> getCurrentAllDataTab() 
	{
        //logger.debug ( "MONA: entered getCurrentAllDataTab()" );
        // added this or else new Globus transfer files won't show up in UI
        // without logout/login
		refreshFolderDataTabs();

		try
		{
			TabbedPanel<? extends FolderItem> folderData = getFolderDataTabs();
            //logger.debug ( "folderData = " + folderData );

			if ( folderData == null )
				return null;
			else {
                //logger.debug ( "content = " + folderData.getCurrentTabContents() );
                return folderData.getCurrentTabContents();
            }
		}
		catch ( Exception e )
		{
			logger.error ( "", e );
			return null;
		}
	}


	@SuppressWarnings("unchecked")
	public Enum getCurrentTabType() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<?> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return null;
			else return (Enum)currentTab.getProperty(DATA_TAB_TYPE);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>>
		getCurrentTabDataRecordMap() {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<?> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return null;
			else return (Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>>)
				currentTab.getProperty(DATA_RECORDS);
		}
	}

	public void setCurrentTabDataRecordMap(
		Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>> dataRecordMap) {
		TabbedPanel<?> folderData = getFolderDataTabs();
		if (folderData == null)
			return;
		else {
			Tab<?> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return;
			else if (dataRecordMap != null)
				currentTab.setProperty(DATA_RECORDS, dataRecordMap);
			else currentTab.removeProperty(DATA_RECORDS);
		}
	}

	@SuppressWarnings("unchecked")
	public List<RecordFieldType> getCurrentTabFields() 
	{
		List<RecordFieldType> emptyList = new ArrayList<RecordFieldType>();

		// get the fields list stored in the action
		if (fields != null)
			return fields;
		// if not not found, construct the proper fields list
		else try {
			Workbench workbench = getWorkbench();
			RecordType recordType = (RecordType)getCurrentTabType();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (recordType == null)
				throw new NullPointerException("No record type has been registered with " +
					"the current data tab.");
			else {
				Set<RecordFieldType> fieldSet = getWorkbench().getRecordFields(recordType);
				if (fieldSet == null || fieldSet.size() < 1)
					return emptyList;
				else {
					fields = sortRecordFields(fieldSet);
					return fields;
				}
			}
		} catch (RuntimeException error) {
			//TODO: this should be a proper checked exception
			//logger.error("", error);
			reportCaughtError(error, "Error retrieving record field list for current data tab");
			return emptyList;
		} catch (Throwable error) {
			//logger.error("", error);
			reportError(error, "Error retrieving record field list for current data tab");
			return emptyList;
		}
	}

	public SourceDocument getSourceDocument(UserDataItem dataItem) {
		// first try the source document stored in the action
		if (sourceDocument != null && currentData != null &&
			dataItem != null && currentData.getUserDataId() == dataItem.getUserDataId())
			return sourceDocument;
		// if not found, retrieve it from the workbench
		else try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (dataItem == null)
				throw new NullPointerException("No data item is currently selected.");
			else {
				sourceDocument = session.getSourceDocument(dataItem);
				currentData = dataItem;
				return sourceDocument;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving source document from selected data item");
			return null;
		}
	}

	public boolean hasSourceDocument(UserDataItem dataItem) {
		if (dataItem == null)
			return false;
		else return (getSourceDocument(dataItem) != null);
	}

	public GenericDataRecordCollection<IndexedDataRecord> getDataRecords(UserDataItem dataItem) {
		Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>> dataRecordMap =
			getCurrentTabDataRecordMap();
		if (dataRecordMap != null && dataItem != null) {
			// this loop is necessary because the argument data item instance
			// might transiently differ from the map key, even if they have the
			// same ID and therefore represent the same data item
			for (UserDataItem storedDataItem : dataRecordMap.keySet()) {
				if (storedDataItem.getUserDataId() == dataItem.getUserDataId()) {
					return dataRecordMap.get(storedDataItem);
				}
			}
			return null;
		} else return null;
	}

	public List<IndexedDataRecord> getDataRecordList(UserDataItem dataItem) {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords(dataItem);
		if (dataRecords == null)
			return null;
		else return dataRecords.toList();
	}

	public boolean hasDataRecords(UserDataItem dataItem) {
		if (dataItem == null)
			return false;
		else return (getDataRecords(dataItem) != null);
	}

	public String getRecordField(RecordFieldType recordFieldType) {
		if (recordFieldType == null)
			return null;
		else return getConceptLabel("RecordField", recordFieldType.toString());
	}

	public boolean isPrimaryId(RecordFieldType recordFieldType) {
		if (recordFieldType == null)
			return false;
		else return recordFieldType.equals(RecordFieldType.PRIMARY_ID);
	}

	public String getDataRecordField(DataRecord dataRecord, RecordFieldType recordFieldType) {
		if (dataRecord == null || recordFieldType == null)
			return null;
		else return truncateText(dataRecord.getField(recordFieldType).getValueAsString());
	}

	public boolean canDisplay() {
		String currentTabLabel = getCurrentTabLabel();
		if (currentTabLabel == null)
			return false;
		else return (currentTabLabel.contains("Structure") ||
			currentTabLabel.equals(getConceptLabel("RecordType", APPLICATION_DATA)));
	}

	public int getSiriusType() {
		String currentTabLabel = getCurrentTabLabel();
		if (currentTabLabel != null &&
			currentTabLabel.equals(getConceptLabel("RecordType", APPLICATION_DATA)))
			return VIEW;
		else return DATA;
	}

	/*
	 * "Unknown" and "All Data" tab property getters
	 */
	public String getPrimaryId(UserDataItem dataItem) {
		//TODO: this functionality should be in a data controller class
		if (dataItem == null)
			return null;
		else 
		{
			Workbench workbench = getWorkbench();
			if (workbench.canRead(dataItem.getType()) == false) 
			{
				debug("Error extracting data records from data item " + dataItem.getUserDataId() + ": data item cannot be read.");
				return null;
			} else try {
				GenericDataRecordCollection<IndexedDataRecord> dataRecords =
					workbench.extractDataRecords(dataItem);
				if (dataRecords == null || dataRecords.size() < 1) {
					debug("Error extracting data records from data item " +
						dataItem.getUserDataId() + ": data record collection parsed " +
						"from data item is null or empty.");
					return null;
				} else {
					//TODO: rather than hack into the first DataRecord, actually
					// list out DataRecords in the task input selection interface,
					// rather than just UserDataItems
					return truncateText(
						dataRecords.get(0).getField(RecordFieldType.PRIMARY_ID).getValueAsString());
				}
			} catch (Throwable error) 
			{
				reportCaughtError(error, "Error extracting data records from data item " + dataItem.getUserDataId());
				return null;
			}
		}
	}

	public String getLabel(UserDataItem dataItem) {
        //logger.debug ( "MONA : entered DataManager.getLabel() 1" );
		if (dataItem == null)
			return null;
		else
        {
            // COSMIC2 does not want to truncate the label...
            //return truncateText(dataItem.getLabel());
            return dataItem.getLabel();
        }
	}

	public String getLabel ( UserDataDirItem dataItem )
    {
        //logger.debug ( "MONA : entered DataManager.getLabel() 2" );
		if ( dataItem == null )
			return null;
		else
        {
            // COSMIC2 does not want to truncate the label...
            //return truncateText ( dataItem.getLabel() );
            return dataItem.getLabel();
        }
	}

    public String getClassName ( UserDataItem item )
    {
        //logger.debug ( "MONA : entered DataManager.getClassName() 1" );
        return ( "UserDataItem" );
    }

    public String getClassName ( UserDataDirItem item )
    {
        //logger.debug ( "MONA : entered DataManager.getClassName() 2" );
        return ( "UserDataDirItem" );
    }

	// BIG_FILES
	public long getDataLength(UserDataItem dataItem) {
		if (dataItem == null)
		{
			return 0;
		}
		try
		{
			return getSourceDocument(dataItem).getDataLength();
		}
		catch (Exception e)
		{
			logger.error("", e);
			return 0;
		}
	}

	public long getDataLength ( UserDataDirItem dataItem )
    {
		if ( dataItem == null )
		{
			return 0;
		}
		try
		{
			return dataItem.getSize();
		}
		catch ( Exception e )
		{
			logger.error ( "", e );
			return 0;
		}
	}


	public String getOwner(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else try{
			return dataItem.getUser().getUsername();
		} catch (Throwable error) {
			reportError(error, "Error retrieving username of user who owns data item " +
				dataItem.getUserDataId());
			return null;
		}
	}

	public String getGroup(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else try {
			return dataItem.getGroup().getGroupname();
		} catch (Throwable error) {
			reportError(error, "Error retrieving group name of group that owns data item " +
				dataItem.getUserDataId());
			return null;
		}
	}

	public String getCreationDate(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else try {
			return formatDate(dataItem.getCreationDate());
		} catch (Throwable error) {
			reportError(error, "Error retrieving creation date of data item " +
				dataItem.getUserDataId());
			return null;
		}
	}

	public String getCreationDate ( UserDataDirItem dataItem )
    {
		if ( dataItem == null )
			return null;
		else try
        {
			return formatDate ( dataItem.getCreationDate() );
		}
        catch (Throwable error)
        {
			reportError ( error,
                "Error retrieving creation date of data item " +
				dataItem.getUserDataId() );
			return null;
		}
	}

	public String getEntityType(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else return getConceptLabel("EntityType", dataItem.getEntityType().toString());
	}

	public String getDataType(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else return getConceptLabel("DataType", dataItem.getDataType().toString());
	}

	public String getDataType ( UserDataDirItem dataItem )
    {
		if ( dataItem == null )
			return null;
		else
            return getConceptLabel ( "DataType",
                dataItem.getDataType().toString() );
	}

	public String getDataFormat(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else return getConceptLabel("DataFormat", dataItem.getDataFormat().toString());
	}

	public String getDataFormat ( UserDataDirItem dataItem )
    {
		if ( dataItem == null )
			return null;
		else
            return getConceptLabel ( "DataFormat",
                dataItem.getDataFormat().toString() );
	}

	/*================================================================
	 * Data display page property accessor methods
	 *================================================================*/
	public UserDataItem getCurrentData() {
        //logger.debug ( "MONA : entered DataManager.getCurrentData()" );
        //logger.debug ( "MONA : currentData 1 = " + currentData );
		// first try the data item stored in the action
		if (currentData != null)
			return currentData;
		// if not found, retrieve it from the session
		else {
			currentData = (UserDataItem)getSessionAttribute(CURRENT_DATA);
            //logger.debug ( "MONA : currentData 2 = " + currentData );
			return currentData;
		}
	}

	public void setCurrentData(UserDataItem dataItem) {
        //logger.debug ( "MONA : entered DataManager.setCurrentData()" );
        //logger.debug ( "MONA : dataItem = " + dataItem );
		if (dataItem == null)
			clearCurrentData();
		else {
			setSessionAttribute(CURRENT_DATA, dataItem);
			currentData = dataItem;
		}
	}

	public void clearCurrentData() {
        //logger.debug ( "MONA : entered DataManager.clearCurrentData()" );
		clearSessionAttribute(CURRENT_DATA);
		currentData = null;
	}

	public String getLabel() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getLabel(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving label of current data item");
			return null;
		}
	}

	public String getOwner() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getOwner(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving owner of current data item");
			return null;
		}
	}

	public String getGroup() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getGroup(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving group of current data item");
			return null;
		}
	}

	public String getCreationDate() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getCreationDate(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving creation date of current data item");
			return null;
		}
	}

	public String getEntityType() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getEntityType(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving entity type of current data item");
			return null;
		}
	}

	public String getDataType() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getDataType(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving data type of current data item");
			return null;
		}
	}

	public String getDataFormat() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return getDataFormat(currentData);
		} catch (Throwable error) {
			reportError(error, "Error retrieving data format of current data item");
			return null;
		}
	}

	public Set<String> getFields() {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return currentData.metaData().keySet();
		} catch (Throwable error) {
			reportError(error, "Error retrieving fields of current data item");
			return null;
		}
	}

	public boolean hasFields() {
		Set<String> fields = getFields();
		return (fields != null && fields.size() > 0);
	}

	public String getFieldLabel(String field) {
		return getConceptLabel("RecordField", field);
	}

	public String getFieldLabel(RecordFieldType field) {
		if (field == null)
			return null;
		else return getFieldLabel(field.toString());
	}

	public String getFieldValue(String field) {
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null)
				throw new NullPointerException("No data item is currently selected.");
			else return truncateText(currentData.metaData().get(field));
		} catch (Throwable error) {
			reportError(error, "Error retrieving value of field \"" + field +
				"\" of current data item");
			return null;
		}
	}

	public String getFieldValue(DataRecord dataRecord, RecordFieldType field) {
		if (dataRecord == null)
			return null;
		else try {
			return dataRecord.getField(field).getValueAsString();
		} catch (Throwable error) {
			reportError(error, "Error retrieving value of field \"" + field +
				"\" of current data record");
			return null;
		}
	}

	public GenericDataRecordCollection<IndexedDataRecord> getDataRecords() {
		// first try the data record collection stored in the action
		if (dataRecords != null)
			return dataRecords;
		// if not found, retrieve it from the workbench
		else {
			dataRecords = getDataRecords(getCurrentData());
			return dataRecords;
		}
	}

	public List<IndexedDataRecord> getDataRecordList() {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords();
		if (dataRecords == null)
			return null;
		else return dataRecords.toList();
	}

	public boolean hasDataRecords() {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords();
		if (dataRecords == null)
			return false;
		else return (dataRecords.size() > 0);
	}

	public List<RecordFieldType> getDataRecordFields() {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords();
		if (dataRecords == null)
			return null;
		else return sortRecordFields(dataRecords.getFields());
	}

	public SourceDocument getSourceDocument() {
		// first try the source document stored in the action
		if (sourceDocument != null)
			return sourceDocument;
		// if not found, retrieve it from the workbench
		else {
			sourceDocument = getSourceDocument(getCurrentData());
			return sourceDocument;
		}
	}

	public boolean hasSourceDocument() {
		return (getSourceDocument() != null);
	}

	/*
	 * This method is where dynamically dispatched source data rendering mechanisms should be employed.
	 * The source data should be properly formatted HTML to be placed directly into the data display page.
	 * The contents of this HTML can be whatever is appropriate for displaying the particular source data
	 * in a web page.
	 *
	 * See SetTaskOutput.getFormattedOutput()
	 */
	public String getFormattedSourceData() 
	{
		SourceDocument sourceDocument = getSourceDocument();

        if ( sourceDocument.getDataType() == DataType.IMAGE )
        {
            // Tianqi, start here
        }
        else
        {
            try 
		    {
			    String theData = getSourceDataAsString(sourceDocument);
			    if (theData != null)
			    {
				    //TODO: make sure the saved data item isn't meant to be rendered as HTML
				    String output = StringEscapeUtils.escapeHtml(theData);
				    return "<pre class=\"source\">" + output + "</pre>";
			    } 
		    } catch (Throwable error) 
		    {
			    reportError(error, "Error retrieving or formatting SourceDocument data");
		    }
        }
		return null;
	}

	/*
		Gets the data as a string if the data item isn't too big.  If it is, returns
		a canned message instead.  
	*/
	public String getSourceDataAsString(SourceDocument sd) throws Exception
	{
		if (sd == null)
		{
			return null;
		}

		// TODO: make sure the file isn't binary.
		// TODO: display the beginning of the file, or create a viewer that streams it.
		if (sd.getDataLength() > MAX_DATA_SIZE)
		{
			return "Sorry, this file is too large to display.\n" + 
				"To view this file, please download it to your local machine.\n";
		}
		byte[] theData = sd.getData();
		if (theData != null)
		{
			return new String(theData);
		}
		return null;
	}

	/*================================================================
	 * Data download property accessor methods
	 *================================================================*/
	public String getFilename() {
		String filename = getLabel();
		if (filename != null)
			return filename;
		else return DEFAULT_FILENAME;
	}

	public String getContentType() {
		String filename = getFilename();
		if (filename == null || filename.equals(DEFAULT_FILENAME))
			return DEFAULT_CONTENT_TYPE;
		else {
			String[] filenameTokens = filename.split("\\.");
			String extension = filenameTokens[filenameTokens.length-1];
			String contentType = getConceptLabel("FileType", extension);
			if (contentType.equals(extension))
				return DEFAULT_CONTENT_TYPE;
			else return contentType;
		}
	}

	public long getDataLength() 
	{
		SourceDocument document = getSourceDocument();
		try 
		{
			if (document == null)
			{
				return 0;
			}
			return document.getDataLength();
		} 
		catch (Throwable error) 
		{
			reportError(error, "Error retrieving size of data " + "from SourceDocument");
			logger.error("", error);
			return 0;
		}
	}


	public InputStream getInputStream() {
		SourceDocument document = getSourceDocument();
		if (document == null)
			return null;
		else try {
			return document.getDataAsStream();
		} catch (Throwable error) {
			reportError(error, "Error retrieving streamed data " +
				"from SourceDocument");
			return null;
		}
	}

	/*================================================================
	 * Page methods
	 *================================================================*/
	public Page<?> getCurrentPage() {
        //logger.debug ( "MONA : entered DataManager.getCurrentPage()" );
        TabbedPanel tp = getFolderDataTabs();
        //logger.debug ( "MONA: tp = " + tp );
        //logger.debug ( "MONA: tp getTabLabels = " + tp.getTabLabels() );
        //logger.debug ( "MONA: size = " + tp.getCurrentTabSize() );
        //logger.debug ( "MONA: labels = " + tp.getTabLabels() );
        //logger.debug ( "MONA: first label = " + tp.getFirstTabLabel() );

        Page tpt = tp.getTabs();
        //logger.debug ( "MONA: tpt = " + tpt );
        //logger.debug ( "MONA: tpt page number = " + tpt.getPageNumber() );
        //logger.debug ( "MONA: tpt page number label = " + tpt.getPageNumberLabel() );
        //logger.debug ( "MONA: tpt last page number = " + tpt.getLastPageNumber() );
        //logger.debug ( "MONA: tpt last page number label = " + tpt.getLastPageNumberLabel() );
        //logger.debug ( "MONA: tpt next page number = " + tpt.getNextPageNumber() );
        //logger.debug ( "MONA: tpt next page number label = " + tpt.getNextPageNumberLabel() );
        //logger.debug ( "MONA: tpt previous page number = " + tpt.getPreviousPageNumber() );
        //logger.debug ( "MONA: tpt previous page number label = " + tpt.getPreviousPageNumberLabel() );
        //logger.debug ( "MONA: tpt page size = " + tpt.getPageSize() );
        //logger.debug ( "MONA: tpt this page number elements = " + tpt.getThisPageNumberOfElements() );
        //logger.debug ( "MONA: tpt total number elements = " + tpt.getTotalNumberOfElements() );
        //logger.debug ( "MONA: tpt page first element number = " + tpt.getThisPageFirstElementNumber() );
        //logger.debug ( "MONA: tpt page last element number = " + tpt.getThisPageLastElementNumber() );
        List tptpe = tpt.getThisPageElements();
        //logger.debug ( "MONA: tptpe = " + tptpe );
        List tptpepnl = tpt.getPageNumberLabels();
        //logger.debug ( "MONA: tptpepnl = " + tptpepnl );

        List tpctc = tp.getCurrentTabContents();
        //logger.debug ( "MONA: tpctc = " + tpctc );
        Tab tpct = tp.getCurrentTab();
        //logger.debug ( "MONA: tpct = " + tpct );
        //logger.debug ( "MONA: tpct getLabel = " + tpct.getLabel() );
        Page<?> tptc = tpct.getContents();
        //logger.debug ( "MONA: tptc = " + tptc );

        //logger.debug ( "MONA: return = " + getFolderDataTabs().getCurrentTab().getContents() );
		return getFolderDataTabs().getCurrentTab().getContents();
	}

	public Integer getPageSize() {
		return getCurrentPage().getPageSize();
	}

	public String getPageSizeString() {
		return getPageSize().toString();
	}

	@SuppressWarnings("unchecked")
	public void setPageSize(Integer pageSize) {
        //logger.debug ( "MONA : DataManager.setPageSize()" );
        //logger.debug ( "MONA : pageSize = " + pageSize );
		try {
			getController().setUserPreference(DATA_PAGE_SIZE, pageSize.toString());
			TabbedPanel<?> dataPanel = getFolderDataTabs();
			if (dataPanel != null && pageSize != null) {
				for (Tab<?> dataTab : dataPanel.getTabs().getAllElements()) {
					List<?> dataList = dataTab.getContents().getAllElements();
					dataTab.setContents(
						new ListPage(dataList, pageSize));
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error saving user's selected data page size");
		}
		getCurrentPage().setPageSize(pageSize);
	}

	public Integer getPageNumber() {
        //logger.debug ( "MONA : DataManager.getPageNumber()" );
        //logger.debug ( "MONA : return = " + getCurrentPage().getPageNumber() );
		return getCurrentPage().getPageNumber();
	}

	@SuppressWarnings("unchecked")
	public void setPageNumber(Integer pageNumber) {
        //logger.debug ( "MONA : DataManager.setPageNumber()" );
        //logger.debug ( "MONA : pageNumber = " + pageNumber );
		getCurrentPage().setPageNumber(pageNumber);
	}

	public boolean isFirstPage() {
        //logger.debug ( "MONA : DataManager.isFirstPage()" );
        //logger.debug ( "MONA : return = " + getCurrentPage().isFirstPage() );
		return getCurrentPage().isFirstPage();
	}

	public boolean isLastPage() {
		return getCurrentPage().isLastPage();
	}

	public boolean hasPreviousPage() {
		return getCurrentPage().hasPreviousPage();
	}

	public int getPreviousPageNumber() {
		return getCurrentPage().getPreviousPageNumber();
	}

	public boolean hasNextPage() {
        //logger.debug ( "MONA : DataManager.hasNextPage()" );
        //logger.debug ( "MONA : return = " + getCurrentPage().hasNextPage() );
		return getCurrentPage().hasNextPage();
	}

	public int getNextPageNumber() {
        //logger.debug ( "MONA : DataManager.getNextPageNumber()" );
        //logger.debug ( "MONA : return = " + getCurrentPage().getNextPageNumber() );
		return getCurrentPage().getNextPageNumber();
	}

	public int getLastPageNumber() {
        //logger.debug ( "MONA : DataManager.getLastPageNumber()" );
        //logger.debug ( "MONA : return = " + getCurrentPage().getLastPageNumber() );
		return getCurrentPage().getLastPageNumber();
	}

	public int getThisPageFirstElementNumber() {
		return getCurrentPage().getThisPageFirstElementNumber();
	}

	public int getThisPageLastElementNumber() {
		return getCurrentPage().getThisPageLastElementNumber();
	}

	public int getThisPageNumberOfElements() {
		return getCurrentPage().getThisPageNumberOfElements();
	}

	/*================================================================
	 * Internal property accessor methods
	 *================================================================*/
	protected UserDataItem getRequestData(String parameter) {
		String dataId = getRequestParameter(parameter);
		if (dataId == null)
			return null;
		else try {
			return getSelectedData(Long.parseLong(dataId));
		} catch (NumberFormatException error) {
			return null;
		}
	}

	protected UserDataItem getSelectedData(Long dataId) {
		try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else return session.findUserDataItem(dataId);
		} catch (Throwable error) {
			reportError(error, "Error retrieving selected data item");
			return null;
		}
	}

	protected UserDataDirItem getSelectedDirData ( Long dataId )
    {
		try
        {
			WorkbenchSession session = getWorkbenchSession();
			if ( session == null )
				throw new NullPointerException ( "No session is present." );
			else
                return session.findUserDataDirItem ( dataId );
		}
        catch ( Throwable error )
        {
			reportError ( error, "Error retrieving selected data item" );
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	protected List<SourceDocument> getSelectedDocuments() {
		return (List<SourceDocument>)getSessionAttribute(SELECTED_DATA_ITEMS);
	}

	protected void setSelectedDocuments() {
        //logger.debug ( "MONA : entered DataManager.setSelectedDocuments()" );
		String[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			clearSessionAttribute(SELECTED_DATA_ITEMS);
		else {
			List<SourceDocument> documents =
				new Vector<SourceDocument>(selectedIds.length);

			for (int i=0; i<selectedIds.length; i++) {
                /* Original
			    UserDataItem dataItem = getSelectedData(selectedIds[i]);
				if (dataItem == null)
				    throw new NullPointerException("Data item with ID " +
					    selectedIds[i] + " was not found.");
				else documents.add(dataItem);
                */

                String[] parts = selectedIds[i].split ( "-" );
                String className = parts[0].trim();
                Long id = Long.parseLong ( parts[1].trim() );

                if ( className.equals ( "UserDataItem" ) )
                {
				    UserDataItem dataItem = getSelectedData ( id );
				    if ( dataItem == null )
					    throw new NullPointerException (
                            "Data item with ID " + id + " was not found.");
				    else
                        documents.add ( dataItem );
                }
                /*
                else if ( className.equals ( "UserDataDirItem" ) )
                {
				    UserDataDirItem dataItem = getSelectedDirData ( id );
				    if ( dataItem == null )
					    throw new NullPointerException (
                            "Data item with ID " + id + " was not found.");
				    else
                        documents.add ( dataItem );
                }
                */
			}

			if (documents.size() > 0)
				setSessionAttribute(SELECTED_DATA_ITEMS, documents);
			else clearSessionAttribute(SELECTED_DATA_ITEMS);
		}
	}

	protected String getDataTabSortType() {
		return getFolderController().getFolderPreference(getCurrentFolder(), DATA_TAB_SORT_TYPE);
	}

	protected void setDataTabSortType(String dataTabSortType) {
		if (dataTabSortType == null)
			clearDataTabSortType();
		else if (dataTabSortType.equals(RECORD_TYPE) ||
			dataTabSortType.equals(DATA_FORMAT)) {
			getFolderController().setFolderPreference(
				getCurrentFolder(), DATA_TAB_SORT_TYPE, dataTabSortType);
		} else {
			debug("Could not set data tab sorting preference, " +
				"because the provided value was not a recognized sort type.");
		}
	}

	protected void clearDataTabSortType() {
		getFolderController().clearFolderPreference(getCurrentFolder(), DATA_TAB_SORT_TYPE);
	}

	@SuppressWarnings("unchecked")
	//###
    /* Updated to also include new Globus folder data items.
     */
	//protected TabbedPanel<T> refreshFolderDataTabs() 
	protected TabbedPanel<? extends FolderItem> refreshFolderDataTabs() 
	{
        //logger.debug ( "MONA : entered DataManager.refreshFolderDataTabs()" );
		try 
		{
			Workbench workbench = getWorkbench();
			WorkbenchSession session = getWorkbenchSession();
			Folder folder = getCurrentFolder();
			TabbedPanel<UserDataItem> folderData = null;
			//TabbedPanel<? extends FolderItem> folderData = null;

			if (workbench == null)
			{
				throw new NullPointerException("No workbench is present.");
			}
			if (session == null)
			{
				throw new NullPointerException("No session is present.");
			}
			if (folder == null)
			{
				throw new NullPointerException("No folder is currently selected.");
			}

			// retrieve user's preferred data tab sort type
            // Looks like this isn't settable by the user and defaults to "recordType" (Mona)
			String dataTabSortType = getDataTabSortType();
            //logger.debug ( "MONA : entered dataTabSortType = " + dataTabSortType );
			if (dataTabSortType == null)
			{
				dataTabSortType = RECORD_TYPE;
				setDataTabSortType(dataTabSortType);
			}
			// retrieve properly sorted data map based on user's preferred data tab sort type
			Map<? extends Enum, List<UserDataItem>> dataMap = null;
			if (dataTabSortType.equals(DATA_FORMAT)) 
			{
				//TODO: need workbench method for this
				dataMap = workbench.sortDataItemsByDataFormat(folder);
			} else 
			{
				dataMap = workbench.sortDataItemsByRecordType(folder);
			}
            //logger.debug ( "MONA : dataMap = " + dataMap );

            // Now add to the dataMap Globus folder data items
			List allDataList = folder.findDataAllItems();
            //logger.debug ( "MONA : allDataList = " + allDataList );
            //logger.debug ( "MONA : allDataList.size = " + allDataList.size() );

			if ( allDataList != null &&  allDataList.size() > 0 )
			{
				folderData = new TabbedPanel<UserDataItem>(folder);
				//folderData = new TabbedPanel<? extends FolderItem>(folder);
				String pageSize = getController().getUserPreference(DATA_PAGE_SIZE);
                //logger.debug ( "MONA : pageSize = " + pageSize );

				// physical view tab = All Data Tab
				Page<UserDataItem> allDataPage = null;
				//Page<? extends FolderItem> allDataPage = null;

				if (pageSize != null) 
				{
					try 
					{
						allDataPage = new ListPage<UserDataItem>(allDataList, Integer.parseInt(pageSize));
					} catch (NumberFormatException error) 
					{
						allDataPage = new ListPage<UserDataItem>(allDataList);
					} 
				}
                else 
					allDataPage = new ListPage<UserDataItem>(allDataList);
                
                //logger.debug ( "MONA : allDataPage = " + allDataPage );
                //logger.debug ( "MONA : allDataPage.getPageSize = " + allDataPage.getPageSize() );
				Tab<UserDataItem> allDataTab = new Tab<UserDataItem>(allDataPage, ALL_DATA_TAB);
				//Tab<? extends FolderItem> allDataTab = new Tab<? extends FolderItem>(allDataPage, ALL_DATA_TAB);
                //logger.debug ( "MONA : allDataTab = " + allDataTab );
				List<Tab<UserDataItem>> dataTabs =
                    new Vector<Tab<UserDataItem>> ( allDataList.size() );
                /*
				List<Tab<? extends FolderItem>> dataTabs =
                    new Vector<Tab<? extends FolderItem>> ( allDataList.size() );
                */
				dataTabs.add(allDataTab);
                //logger.debug ( "MONA : allDataTab = " + allDataTab );
				folderData.setTabs(new ListPage<Tab<UserDataItem>>(dataTabs));
				//folderData.setTabs(new ListPage<Tab<FolderItem>>(dataTabs));
                //logger.debug ( "MONA : folderData = " + folderData );
				folderData.sortTabs();
			}

			setFolderDataTabs(folderData);
			return folderData;
		} 
		catch (Throwable error) 
		{
			reportError(error, "Error retrieving tabbed folder data");
			return null;
		}
	}

	protected boolean deleteData(UserDataItem dataItem) {
        //logger.debug ( "MONA: entered DataManager.deleteData(UserDataItem)" );
		try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (dataItem == null)
				throw new NullPointerException("Data item was not found.");
			else {
				Long id = dataItem.getUserDataId();
				session.deleteUserDataItem(dataItem);
				dataItem = session.findUserDataItem(id);
				if (dataItem == null)
					return true;
				else return false;
			}
		} catch (Throwable error) {
			reportError(error, "Error deleting data item");
			return false;
		}
	}

    /**
     * Delete the dataItem and the directory which contains the dataItem.
     * @author Mona Wong
     * @param dataItem - the UserDataDirItem object to delete
     * @return the number of items deleted
     **/
	protected int deleteData ( UserDataDirItem dataItem )
    {
        //logger.debug ( "MONA: entered DataManager.deleteData(UserDataDirItem)" );
        int deleted = 0;

		if ( dataItem == null )
	        return ( deleted );

		try
        {
            String globusRoot =
                Workbench.getInstance().getProperties().getProperty
                ( "database.globusRoot" );
            //logger.debug ( "MONA: globusRoot = " + globusRoot );
            String link_username = ( String ) getSession().get
                ( OauthConstants.LINK_USERNAME );
            //logger.debug ( "MONA: link_username = " + link_username );
			WorkbenchSession session = getWorkbenchSession();
            //logger.debug ( "MONA: session = " + session );
			if ( session == null )
				throw new NullPointerException ( "No session is present." );

            else if ( globusRoot == null || link_username == null )
				throw new NullPointerException
                    ( "Cannot determine file location." );

			else
            {
                // First, delete the directory
                Long userId = session.getUser().getUserId();
                //logger.debug ( "MONA: userId = " + userId );
                String label = dataItem.getLabel();
                File file = new File ( globusRoot + "/" + link_username +
                    "/" + label );
                //logger.debug ( "MONA: file = " + file );
                if ( file.isDirectory() )
                {
                    //logger.debug ( "MONA: deleted " + file );
                    FileUtils.deleteDirectory ( file );
                }
                else
                {
                    File path = new File ( file.getParent() );
                    //logger.debug ( "MONA: deleted " + path );
                    FileUtils.deleteDirectory ( path );
                }

                List<UserDataDirItem> list = session.findUserDataDirItems
                    ( label ); 
                //logger.debug ( "MONA: list = " + list );
				//Long id = dataItem.getUserDataId();
                //logger.debug ( "MONA: id = " + id );

                for ( UserDataDirItem item : list )
                {
                    /*
                    logger.debug ( "MONA: item = " + item );
                    logger.debug ( "MONA: item.getLabel = " +
                        item.getLabel() );
                    */
				    session.deleteUserDataDirItem ( item );
                    deleted++;
                }

                return ( deleted );
			}
		}

        catch ( Throwable error )
        {
			reportError ( error, "Error deleting data item" );
			return ( deleted );
		}
	}

	protected int moveSelectedDataItems() {
		// get IDs of selected data items to move
		int moved = 0;
		String[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return moved;

		// get target folder to move to
		Folder folder = getFolderController().getFolder(getTargetFolder());
		if (folder == null)
			addActionError("Error moving data item to folder with ID " +
				getTargetFolder() + ": folder not found.");

		// move selected data items to target folder
		else for (int i=0; i<selectedIds.length; i++) {
            String[] parts = selectedIds[i].split ( "-" );
            String className = parts[0].trim();
            Long id = Long.parseLong ( parts[1].trim() );

            if ( className.equals ( "UserDataItem" ) )
            {
			    UserDataItem dataItem = getSelectedData ( id );
			    if ( dataItem == null )
				    addActionError ( "Error moving data item with ID " + id +
                        ": item not found." );
			    else try
                {
				    getWorkbenchSession().move ( dataItem, folder );
				    moved++;
			    } catch ( Throwable error )
                {
				    reportError ( error, "Error moving data item \"" +
                        dataItem.getLabel() + "\" to folder \"" +
                        folder.getLabel() + "\"" );
			    }
            }
            else if ( className.equals ( "UserDataDirItem" ) )
            {
			    UserDataDirItem dataItem = getSelectedDirData ( id );
			    if ( dataItem == null )
				    addActionError ( "Error moving data item with ID " + id +
                        ": item not found." );
			    else try
                {
				    getWorkbenchSession().move ( dataItem, folder );
				    moved++;
			    } catch ( Throwable error )
                {
				    reportError ( error, "Error moving data item \"" +
                        dataItem.getLabel() + "\" to folder \"" +
                        folder.getLabel() + "\"" );
			    }
            }
		}
		return moved;
	}

	protected int copySelectedDataItems() {
		// get IDs of selected data items to copy
		int copied = 0;
		String[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return copied;

		// get target folder to copy to
		Folder folder = getFolderController().getFolder(getTargetFolder());
		if (folder == null)
			addActionError("Error copying data item to folder with ID " +
				getTargetFolder() + ": folder not found.");

		// copy selected data items to target folder
		else for (int i=0; i<selectedIds.length; i++) {
            String[] parts = selectedIds[i].split ( "-" );
            String className = parts[0].trim();
            Long id = Long.parseLong ( parts[1].trim() );

            if ( className.equals ( "UserDataItem" ) )
            {
			    UserDataItem dataItem = getSelectedData ( id );
			    if ( dataItem == null )
				    addActionError ( "Error copying data item with ID " +
					    id + ": item not found." );
			    else try
                {
				    getWorkbenchSession().copy ( dataItem, folder );
				    copied++;
			    } catch ( Throwable error )
                {
				    reportError ( error, "Error copying data item \"" +
                        dataItem.getLabel() + "\" to folder \"" +
                        folder.getLabel() + "\"" );
			    }
            }
            else if ( className.equals ( "UserDataDirItem" ) )
            {
			    UserDataDirItem dataItem = getSelectedDirData ( id );
			    if ( dataItem == null )
				    addActionError ( "Error copying data item with ID " +
					    id + ": item not found." );
			    else try
                {
				    getWorkbenchSession().copy ( dataItem, folder );
				    copied++;
			    } catch ( Throwable error )
                {
				    reportError ( error, "Error copying data item \"" +
                        dataItem.getLabel() + "\" to folder \"" +
                        folder.getLabel() + "\"" );
			    }
            }
		}
		return copied;
	}

	protected int deleteSelectedDataItems() {
		int deleted = 0;
		String[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return deleted;
		else {
			User user = null;
			
			for (int i=0; i<selectedIds.length; i++) {
                String[] parts = selectedIds[i].split ( "-" );
                String className = parts[0].trim();
                Long id = Long.parseLong ( parts[1].trim() );

                if ( className.equals ( "UserDataItem" ) )
                {
			        UserDataItem dataItem = getSelectedData ( id );

			        if ( dataItem == null )
					    addActionError ( "Error deleting data item with ID " +
					        id + ": item not found." );
			        else try
                    {
					    if ( deleteData ( dataItem ) )
					    {
						    user = dataItem.getUser();
						    deleted++;
					    }
			        }
                    catch ( Throwable error )
                    {
					    reportError ( error, "Error deleting data item \"" +
							dataItem.getLabel() + "\"");
			        }
                }
                else if ( className.equals ( "UserDataDirItem" ) )
                {
			        UserDataDirItem dataItem = getSelectedDirData ( id );

			        if ( dataItem == null )
					    addActionError ( "Error deleting data item with ID " +
					        id + ": item not found." );
			        else try
                    {
                        /*
					    if ( deleteData ( dataItem ) )
					    {
						    user = dataItem.getUser();
						    deleted++;
					    }
                        */

                        int count = deleteData ( dataItem );

                        if ( count > 0 )
                        {
                            deleted += count;
						    user = dataItem.getUser();
                        }
			        }
                    catch ( Throwable error )
                    {
					    reportError ( error, "Error deleting data item \"" +
							dataItem.getLabel() + "\"");
			        }
                }
			}
			
			if ( user != null )
			{
				clearActionErrors();
				refreshUserDataSize ( user );
			}
		}

		return deleted;
	}

	@SuppressWarnings("unchecked")
	protected String[] sortConceptSet(Set conceptSet) {
		if (conceptSet == null || conceptSet.size() < 1)
			return null;
		else {
			String[] concepts = new String[conceptSet.size()];
			int i = 0;
			for (Object concept : conceptSet) {
				concepts[i] = concept.toString();
				i++;
			}
			Arrays.sort(concepts, new ConceptComparator());
			return concepts;
		}
	}

	@SuppressWarnings("unchecked")
	protected Map<String, String> mapConceptSet(Set conceptSet, String conceptType) {
		String[] sortedConceptSet = sortConceptSet(conceptSet);
		Map<String, String> conceptMap = new TreeMap<String, String>(new ConceptComparator());
		if (sortedConceptSet != null)
			for (String concept : sortedConceptSet) {
				String text = getConceptLabel(conceptType, concept);
				if (text.trim().startsWith(conceptType) == false)
					conceptMap.put(concept, text);
			}
		return conceptMap;
	}

	@SuppressWarnings("unchecked")
	protected List<RecordFieldType> sortRecordFields(Set<RecordFieldType> recordFieldSet) {
		if (recordFieldSet == null || recordFieldSet.size() < 1)
			return null;
		else {
			RecordFieldType[] recordFields = new RecordFieldType[recordFieldSet.size()];
			int i = 0;
			for (RecordFieldType recordField : recordFieldSet) {
				recordFields[i] = recordField;
				i++;
			}
			Arrays.sort(recordFields, new RecordFieldTypeComparator());
			return Arrays.asList(recordFields);
		}
	}

	protected Set<EntityType> getEntityTypeSet() {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else {
				Set<EntityType> entityTypes =
					new HashSet<EntityType>(workbench.getEntityTypes());
				entityTypes.add(EntityType.valueOf(UNKNOWN));
				return entityTypes;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered entity types");
			return null;
		}
	}

	protected Set<EntityType> getSearchableEntityTypeSet() {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else {
				Set<EntityType> entityTypes =
					new HashSet<EntityType>(workbench.getSearchableEntityTypes());
				entityTypes.add(EntityType.valueOf(UNKNOWN));
				return entityTypes;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered entity types");
			return null;
		}
	}

	protected Set<DataType> getUnknownDataTypeSet() {
		Set<DataType> unknown = new HashSet<DataType>(1);
		unknown.add(DataType.UNKNOWN);
		return unknown;
	}

	protected Set<DataType> getDataTypeSet(EntityType entityType) {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (entityType == null || entityType.toString() == null)
				throw new NullPointerException("Selected entity type is not present.");
			else {
				Set<RecordType> recordTypes = workbench.getRecordTypes(entityType);
				Set<DataType> dataTypes = new HashSet<DataType>(recordTypes.size());
				for (RecordType recordType : recordTypes) {
					dataTypes.add(workbench.getDataType(recordType));
				}
				dataTypes.add(DataType.valueOf(UNKNOWN));
				return dataTypes;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered data types " +
				"for specified entity type");
			return null;
		}
	}

	protected Set<DataType> getSearchableDataTypeSet(EntityType entityType) {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (entityType == null || entityType.toString() == null)
				throw new NullPointerException("Selected entity type is not present.");
			else {
				Set<RecordType> recordTypes = workbench.getSearchableRecordTypes();
				Set<DataType> dataTypes = new HashSet<DataType>();
				for (RecordType recordType : recordTypes) {
					if (workbench.getEntityType(recordType).equals(entityType))
						dataTypes.add(workbench.getDataType(recordType));
				}
				dataTypes.add(DataType.valueOf(UNKNOWN));
				return dataTypes;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered data types " +
				"for specified entity type");
			return null;
		}
	}

	protected RecordType getRecordType(EntityType entityType, DataType dataType) {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else {
				Set<RecordType> recordTypes = workbench.getRecordTypes(entityType);
				for (RecordType recordType : recordTypes) {
					if (workbench.getDataType(recordType).equals(dataType))
						return recordType;
				}
				return null;
			}
		} catch (Throwable error) {
			reportError(error, "Error converting entity type and data type to record type");
			return null;
		}
	}

	protected Set<DataFormat> getUnknownDataFormatSet() {
		Set<DataFormat> unknown = new HashSet<DataFormat>(1);
		unknown.add(DataFormat.UNKNOWN);
		return unknown;
	}

	protected Set<DataFormat> getDataFormatSet() {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else {
				Set<DataFormat> dataFormats =
					new HashSet<DataFormat>(workbench.getRegisteredDataFormats());
				dataFormats.add(DataFormat.valueOf(UNKNOWN));
				return dataFormats;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered data formats");
			return null;
		}
	}

	protected Set<DataFormat> getDataFormatSet(RecordType recordType) {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (recordType == null || recordType.toString() == null)
				throw new NullPointerException("Selected record type is not present.");
			else {
				Set<DataFormat> dataFormats =
					new HashSet<DataFormat>(workbench.getRegisteredDataFormats(recordType));
				dataFormats.add(DataFormat.valueOf(UNKNOWN));
				return dataFormats;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered data formats " +
				"for specified record type");
			return null;
		}
	}

	protected Set<DataFormat> getDataFormatSet(EntityType entityType, DataType dataType) {
		try {
			Workbench workbench = getWorkbench();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (entityType == null || entityType.toString() == null)
				throw new NullPointerException("Selected entity type is not present.");
			else if (dataType == null || dataType.toString() == null)
				throw new NullPointerException("Selected data type is not present.");
			else {
				RecordType recordType = getRecordType(entityType, dataType);
				if (recordType == null)
					return null;
				else return getDataFormatSet(recordType);
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving set of registered data formats " +
				"for specified entity type and data type");
			return null;
		}
	}
}
