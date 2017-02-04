package org.ngbw.web.actions;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import org.apache.commons.lang.StringEscapeUtils;
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
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.util.UserDataItemSortableField;
import org.ngbw.web.model.Page;
import org.ngbw.web.model.Tab;
import org.ngbw.web.model.TabbedPanel;
import org.ngbw.web.model.impl.ConceptComparator;
import org.ngbw.web.model.impl.ListPage;
import org.ngbw.web.model.impl.RecordFieldTypeComparator;

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
	private Long[] selectedIds;
	private String dataAction;
	private Long targetFolder;

	// data display page properties
	protected GenericDataRecordCollection<IndexedDataRecord> dataRecords;
	protected SourceDocument sourceDocument;

	/*================================================================
	 * Action methods
	 *================================================================*/
	public String list() {
		Folder folder = getRequestFolder(ID);
		if (folder == null)
			folder = getCurrentFolder();
		if (folder == null) {
			reportUserError("You must select a folder to view its data.");
			return HOME;
		} else {
			if (isCurrentFolder(folder) == false)
				setCurrentFolder(folder);
			TabbedPanel<UserDataItem> tabs = getFolderDataTabs();
			String tab = getRequestParameter(TAB);
			if (tab != null)
				tabs.setCurrentTab(tab);
			clearCurrentData();
			return LIST;
		}
	}

	@SkipValidation
	public String paginate() {
		setPageNumber(0);
		return LIST;
	}

	@SkipValidation
	public String setPage() {
		String page = getRequestParameter(PAGE);
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
		// delete the current data item
		try {
			UserDataItem currentData = getCurrentData();
			if (currentData == null) {
				reportUserError("You must select a data item to delete it.");
			} else {
				String dataLabel = currentData.getLabel();
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
			Long[] selectedIds = getSelectedIds();
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
		Long[] selectedIds = getSelectedIds();
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
	public Long[] getSelectedIds() {
		return selectedIds;
	}

	public void setSelectedIds(Long[] selectedIds) {
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
	public TabbedPanel<UserDataItem> getFolderDataTabs() {
		TabbedPanel<UserDataItem> folderData =
			(TabbedPanel<UserDataItem>)getSessionAttribute(FOLDER_DATA);
		if (folderData == null ||
			folderData.isParentFolder(getCurrentFolder()) == false)
			folderData = refreshFolderDataTabs();
		return folderData;
	}

	@SuppressWarnings("unchecked")
	public void setFolderDataTabs(TabbedPanel<UserDataItem> folderData) {
		setSessionAttribute(FOLDER_DATA, folderData);
	}

	public boolean hasFolderData() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		return (folderData != null && folderData.getTabs().getTotalNumberOfElements() > 0);
	}

	public List<String> getTabLabels() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else return folderData.getTabLabels();
	}

	public String getFirstTabLabel() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else return folderData.getFirstTabLabel();
	}

	public String getCurrentTabLabel() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<UserDataItem> currentTab = folderData.getCurrentTab();
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
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return 0;
		else return folderData.getCurrentTabSize();
	}

	//TODO: add sort functionality to user interface
	public List<UserDataItem> getCurrentDataTab() 
	{
		try
		{
			TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
			if (folderData == null)
				return null;
			else return folderData.getCurrentTabContents();
		}
		catch(Exception e)
		{
			logger.error("", e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public Enum getCurrentTabType() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<UserDataItem> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return null;
			else return (Enum)currentTab.getProperty(DATA_TAB_TYPE);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>>
		getCurrentTabDataRecordMap() {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return null;
		else {
			Tab<UserDataItem> currentTab = folderData.getCurrentTab();
			if (currentTab == null)
				return null;
			else return (Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>>)
				currentTab.getProperty(DATA_RECORDS);
		}
	}

	public void setCurrentTabDataRecordMap(
		Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>> dataRecordMap) {
		TabbedPanel<UserDataItem> folderData = getFolderDataTabs();
		if (folderData == null)
			return;
		else {
			Tab<UserDataItem> currentTab = folderData.getCurrentTab();
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
		if (dataItem == null)
			return null;
		else return truncateText(dataItem.getLabel());
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

	public String getDataFormat(UserDataItem dataItem) {
		if (dataItem == null)
			return null;
		else return getConceptLabel("DataFormat", dataItem.getDataFormat().toString());
	}

	/*================================================================
	 * Data display page property accessor methods
	 *================================================================*/
	public UserDataItem getCurrentData() {
		// first try the data item stored in the action
		if (currentData != null)
			return currentData;
		// if not found, retrieve it from the session
		else {
			currentData = (UserDataItem)getSessionAttribute(CURRENT_DATA);
			return currentData;
		}
	}

	public void setCurrentData(UserDataItem dataItem) {
		if (dataItem == null)
			clearCurrentData();
		else {
			setSessionAttribute(CURRENT_DATA, dataItem);
			currentData = dataItem;
		}
	}

	public void clearCurrentData() {
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
        logger.debug ( "*** MONA : entered DataManager.getFormattedSourceData()" );
		SourceDocument sourceDocument = getSourceDocument();
        logger.debug ( "*** MONA : sourceDocument = " + sourceDocument );
        logger.debug ( "*** MONA : data type = " + sourceDocument.getDataType() );
        try {
            logger.debug ( "*** MONA : data = " + sourceDocument.getData() );
        }
        catch ( Exception e ) {
            logger.debug ( "*** MONA : unable to get data" );
        }
        logger.debug ( "*** MONA : name = " + sourceDocument.getName() );

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
	public Page<? extends FolderItem> getCurrentPage() {
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
		try {
			getController().setUserPreference(DATA_PAGE_SIZE, pageSize.toString());
			TabbedPanel<UserDataItem> dataPanel = getFolderDataTabs();
			if (dataPanel != null && pageSize != null) {
				for (Tab<UserDataItem> dataTab : dataPanel.getTabs().getAllElements()) {
					List<UserDataItem> dataList = dataTab.getContents().getAllElements();
					dataTab.setContents(
						new ListPage<UserDataItem>(dataList, pageSize));
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error saving user's selected data page size");
		}
		getCurrentPage().setPageSize(pageSize);
	}

	public Integer getPageNumber() {
		return getCurrentPage().getPageNumber();
	}

	@SuppressWarnings("unchecked")
	public void setPageNumber(Integer pageNumber) {
		getCurrentPage().setPageNumber(pageNumber);
	}

	public boolean isFirstPage() {
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
		return getCurrentPage().hasNextPage();
	}

	public int getNextPageNumber() {
		return getCurrentPage().getNextPageNumber();
	}

	public int getLastPageNumber() {
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

	@SuppressWarnings("unchecked")
	protected List<SourceDocument> getSelectedDocuments() {
		return (List<SourceDocument>)getSessionAttribute(SELECTED_DATA_ITEMS);
	}

	protected void setSelectedDocuments() {
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			clearSessionAttribute(SELECTED_DATA_ITEMS);
		else {
			List<SourceDocument> documents =
				new Vector<SourceDocument>(selectedIds.length);
			for (int i=0; i<selectedIds.length; i++) {
				UserDataItem dataItem = getSelectedData(selectedIds[i]);
				if (dataItem == null)
					throw new NullPointerException("Data item with ID " +
						selectedIds[i] + " was not found.");
				else documents.add(dataItem);
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
	protected TabbedPanel<UserDataItem> refreshFolderDataTabs() 
	{
		try 
		{
			Workbench workbench = getWorkbench();
			WorkbenchSession session = getWorkbenchSession();
			Folder folder = getCurrentFolder();
			TabbedPanel<UserDataItem> folderData = null;
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
			String dataTabSortType = getDataTabSortType();
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

			if (dataMap != null && dataMap.size() > 0)
			{
				folderData = new TabbedPanel<UserDataItem>(folder);
				List<Tab<UserDataItem>> dataTabs =
				new Vector<Tab<UserDataItem>>(dataMap.size());
				String pageSize = getController().getUserPreference(DATA_PAGE_SIZE);

				// physical view tab = All Data Tab
				Page<UserDataItem> allDataPage = null;
				List<UserDataItem> allDataList = folder.findDataItems();
				if (pageSize != null) 
				{
					try 
					{
						allDataPage = new ListPage<UserDataItem>(allDataList, Integer.parseInt(pageSize));
					} catch (NumberFormatException error) 
					{
						allDataPage = new ListPage<UserDataItem>(allDataList);
					} 
				} else 
				{
					allDataPage = new ListPage<UserDataItem>(allDataList);
				}
				Tab<UserDataItem> allDataTab = new Tab<UserDataItem>(allDataPage, ALL_DATA_TAB);
				dataTabs.add(allDataTab);
				folderData.setTabs(new ListPage<Tab<UserDataItem>>(dataTabs));
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
	protected int moveSelectedDataItems() {
		// get IDs of selected data items to move
		int moved = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return moved;

		// get target folder to move to
		Folder folder = getFolderController().getFolder(getTargetFolder());
		if (folder == null)
			addActionError("Error moving data item to folder with ID " +
				getTargetFolder() + ": folder not found.");

		// move selected data items to target folder
		else for (int i=0; i<selectedIds.length; i++) {
			UserDataItem dataItem = getSelectedData(selectedIds[i]);
			if (dataItem == null)
				addActionError("Error moving data item with ID " +
					selectedIds[i] + ": item not found.");
			else try {
				getWorkbenchSession().move(dataItem, folder);
				moved++;
			} catch (Throwable error) {
				reportError(error, "Error moving data item \"" + dataItem.getLabel() +
					"\" to folder \"" + folder.getLabel() + "\"");
			}
		}
		return moved;
	}

	protected int copySelectedDataItems() {
		// get IDs of selected data items to copy
		int copied = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return copied;

		// get target folder to copy to
		Folder folder = getFolderController().getFolder(getTargetFolder());
		if (folder == null)
			addActionError("Error copying data item to folder with ID " +
				getTargetFolder() + ": folder not found.");

		// copy selected data items to target folder
		else for (int i=0; i<selectedIds.length; i++) {
			UserDataItem dataItem = getSelectedData(selectedIds[i]);
			if (dataItem == null)
				addActionError("Error copying data item with ID " +
					selectedIds[i] + ": item not found.");
			else try {
				getWorkbenchSession().copy(dataItem, folder);
				copied++;
			} catch (Throwable error) {
				reportError(error, "Error copying data item \"" + dataItem.getLabel() +
					"\" to folder \"" + folder.getLabel() + "\"");
			}
		}
		return copied;
	}

	protected int deleteSelectedDataItems() {
		int deleted = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return deleted;
		else {
			User user = null;
			
			for (int i=0; i<selectedIds.length; i++) {
				UserDataItem dataItem = getSelectedData(selectedIds[i]);
				if (dataItem == null)
					addActionError("Error deleting data item with ID " +
							selectedIds[i] + ": item not found.");
				else try {
					if (deleteData(dataItem))
					{
						user = dataItem.getUser();
						deleted++;
					}
				} catch (Throwable error) {
					reportError(error, "Error deleting data item \"" +
							dataItem.getLabel() + "\"");
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
