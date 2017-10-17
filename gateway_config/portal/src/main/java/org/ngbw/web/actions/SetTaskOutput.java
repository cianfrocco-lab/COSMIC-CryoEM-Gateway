package org.ngbw.web.actions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import org.apache.log4j.Logger;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.DataDetails;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.core.GenericDataRecordCollection;
import org.ngbw.sdk.api.core.SourceDocumentTransformer;
import org.ngbw.sdk.core.shared.IndexedDataRecord;
import org.ngbw.sdk.core.shared.SourceDocumentType;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.core.types.RecordFieldType;
import org.ngbw.sdk.core.types.RecordType;
import org.ngbw.sdk.database.DataRecord;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.UserDataItem;

/**
 * Struts action class to handle creation (create/edit)
 * of tasks in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class SetTaskOutput extends ManageTasks
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(SetTaskOutput.class.getName());
	// session attribute key constants
	public static final String TASK_OUTPUT_KEY = "taskOutputKey";
	public static final String TASK_OUTPUT_FILE = "taskOutputFile";
	public static final String TRANSFORMER = "transformer";
	public static final String TRANSFORMED_RECORD = "transformedRecord";

	// parameter attribute key constants
	public static final String KEY = "key";
	public static final String FILE = "file";
	public static final String INDEX = "index";

	// result constants
	public static final String DISPLAY_OUTPUT = "displayOutput";
	public static final String DISPLAY_OUTPUT_LIST = "displayOutputList";
	public static final String DISPLAY_OUTPUT_FILE = "displayOutputFile";
	public static final String DISPLAY_TRANSFORMED_OUTPUT = "displayTransformedOutput";
	public static final String DISPLAY_TRANSFORMED_RECORD = "displayTransformedRecord";

	// semantic tag constants
	public static final String PROTEIN = "PROTEIN";
	public static final String PROTEIN_SIMILARITY_SEARCH_HIT = "PROTEIN_SIMILARITY_SEARCH_HIT";
	public static final String NUCLEIC_ACID = "NUCLEIC_ACID";
	public static final String NUCLEIC_ACID_SIMILARITY_SEARCH_HIT = "NUCLEIC_ACID_SIMILARITY_SEARCH_HIT";
	public static final String BLAST_OUTPUT = "BLAST_OUTPUT";
	public static final String FASTA_OUTPUT = "FASTA_OUTPUT";

	// task output file default constants
	public static final String DEFAULT_FILENAME = "output";
	public static final String DEFAULT_CONTENT_TYPE = "text/plain";

	// transformed record list page constants
	public static final String PAGE_SIZE = "pageSize";
	public static final String PAGE_NUMBER = "pageNumber";

	// record field constants
	public static final String QUERY = "QUERY";
	public static final String PRIMARY_ID = "PRIMARY_ID";
	public static final String NAME = "NAME";

	/*================================================================
	 * Properties
	 *================================================================*/
	// task output form properties
	private UserDataItem dataItem;
	private String entityType;
	private String dataType;
	private String dataFormat;

	// task output list form properties
	private String[] selectedOutputs;

	/*================================================================
	 * Action methods
	 *================================================================*/
	@SkipValidation
	public String input() {
		if (getTaskOutput() == null)
		{
			throw new NullPointerException("A call to SetTaskOutput.input() was made " +
				"with no selected output data item stored in the session.");
		}
		// initially, display label = filename.  User can change the label.
		setDefaultLabel();
		return INPUT;
	}

	// If label is empty, set it to match filename.
	private String setDefaultLabel()
	{
		// set dataItem member if not already set
		getDataItem();

		// get label from dataItem
		String label = getLabel();

		if (label == null || label.trim().equals("")) 
		{
			// set label equal to filename of dataitem.
			label = getFilename();
			if (label != null && label.trim().equals("") == false)
				setLabel(label);
		}
		return label;
	}

	public String execute() throws Exception {
		// set the label, if necessary
		setDefaultLabel();
		// save the data item
		try {
			if (validateData()) {
				setDataItem(saveDataItem());
				setCurrentData(getDataItem());
				reportUserMessage("Task output \"" + getLabel() + "\" successfully saved.");
				return display();
			} else return INPUT;
		} catch (Throwable error) {
			reportUserError(error, "Error saving data item \"" + getLabel() + "\"");
			return ERROR;
		}
	}

	@SkipValidation
	public String reload() {
		try {
			resolveConcepts();
			return INPUT;
		} catch (Throwable error) {
			reportUserError(error, "Error resolving UserDataItem properties");
			return ERROR;
		}
	}

	@SkipValidation
	public String cancel() {
		// discard input and return
		addActionMessage("Task output not saved.");
		return DISPLAY_OUTPUT;
	}

	/*
	 * This method is called when a user wishes to return from an
	 * individual output display page to the proper "parent" page.
	 * If the current task has more than one output, then this page
	 * should be the output list page.  Otherwise, it should be the
	 * greater task display.
	 */
	@SkipValidation
	public String display() {
		// forward to task display if this is the only output,
		// otherwise show the whole list
		Task task = getCurrentTask();
		if (task == null) {
			return LIST;
		} else try {
			Map<String, List<TaskOutputSourceDocument>> output =
				getWorkbenchSession().getOutput(task);
			if (output == null || output.size() < 2) {
				return DISPLAY;
			} else return DISPLAY_OUTPUT_LIST;
		} catch (Throwable error) {
			reportError(error, "Error retrieving output for task " + task.getTaskId());
			return DISPLAY;
		}
	}

	/*
	 * This method is called when a user selects an individual output item
	 * to be viewed from the list of outputs.  This is where dynamic interface
	 * dispatching should occur, based on the readability of the selected
	 * output item.
	 */
	@SkipValidation
	public String selectOutput() {
		// get selected output ID from request param, if present
		String[] outputKey = (String[])getParameters().get(KEY);
		String[] outputFile = (String[])getParameters().get(FILE);
		if (outputKey != null && outputKey.length > 0 &&
			outputFile != null && outputFile.length > 0) {
			Task task = getCurrentTask();
			if (task == null) {
				addActionError("You must select a task to view its output.");
				return LIST;
			} else {
				Map<String, List<TaskOutputSourceDocument>> output = getOutput(task);
				if (output == null || output.containsKey(outputKey[0]) == false) {
					addActionError("The output you selected is not present in the current task.");
					return displayOutput();
				} else {
					setTaskOutputKey(outputKey[0]);
					List<TaskOutputSourceDocument> selectedOutput = output.get(outputKey[0]);
					if (selectedOutput == null ||
						containsOutputDocument(selectedOutput, outputFile[0]) == false) {
						addActionError("The output you selected is not present in the current task.");
						return displayOutput();
					} else {
						setTaskOutputFile(outputFile[0]);
						return DISPLAY_OUTPUT;
					}
				}
			}
		} else if (getTaskOutput() != null) {
			return DISPLAY_OUTPUT;
		} else {
			addActionError("You must select an output to view its contents.");
			return displayOutput();
		}
	}

	/*
	 * This method is called when a user wishes to view the output
	 * of a task.  If the selected task has more than one output, then
	 * the resulting page should be the output list page.  Otherwise,
	 * it should be the individual output display page for the one
	 * output item.
	 */
	@SkipValidation
	public String displayOutput() {
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) {
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
		}
		// forward to individual output display if there's only one output,
		// otherwise show the whole list
		Task task = getCurrentTask();
		if (task == null) {
			addActionError("You must select a task to view its output.");
			return LIST;
		} else {
			Map<String, List<TaskOutputSourceDocument>> output = getOutput(task);
			if (output == null || output.size() < 1) {
				addActionError("You must select a successfully completed task to view its output.");
				return LIST;
			} else if (output.size() == 1) {
				String outputKey = output.keySet().toArray(new String[1])[0];
				setTaskOutputKey(outputKey);
				List<TaskOutputSourceDocument> selectedOutput = output.get(outputKey);
				if (selectedOutput.size() == 1) {
					setTaskOutputFile(selectedOutput.get(0).getName());
					return DISPLAY_OUTPUT;
				} else return DISPLAY_OUTPUT_LIST;
			} else return DISPLAY_OUTPUT_LIST;
		}
	}

	@SkipValidation
	public String displayOutputFile() {
		// get selected output ID from request param, if present
		String[] outputKey = (String[])getParameters().get(KEY);
		String[] outputFile = (String[])getParameters().get(FILE);
		if (outputKey != null && outputKey.length > 0 &&
			outputFile != null && outputFile.length > 0) {
			setTaskOutputKey(outputKey[0]);
			setTaskOutputFile(outputFile[0]);
		}
		// display correct output file
		if (getTaskOutput() == null) {
			return displayOutput();
		} else return DISPLAY_OUTPUT_FILE;
	}

	/*
	 * This method is called when a user wishes to transform a single
	 * data record from a parsed output source document into a relevant
	 * transformable type.
	 */
	@SkipValidation
	public String displayTransformedOutput() {
		// get selected task ID from request param, if present
		String[] index = (String[])getParameters().get(INDEX);
		if (index != null && index.length > 0) {
			if (transformOutput(Integer.parseInt(index[0])) == false) {
				addActionError("You must select an output record to view its contents.");
				return DISPLAY_OUTPUT;
			} else {
				setPageSize(20);
				setPageNumber(0);
				return DISPLAY_TRANSFORMED_OUTPUT;
			}
		} else if (getTransformedRecords() != null)
			return DISPLAY_TRANSFORMED_OUTPUT;
		else {
			addActionError("You must select an output record to view its contents.");
			return DISPLAY_OUTPUT;
		}
	}

	@SkipValidation
	public String displayTransformedRecord() {
		// get selected result item index from request param, if present
		String[] index = (String[])getParameters().get(ID);
		if (index != null && index.length > 0) {
			setTransformedRecord(getTransformedRecordPage().get(Integer.parseInt(index[0])));
			return DISPLAY_TRANSFORMED_RECORD;
		} else if (getTransformedRecord() != null) {
			return DISPLAY_TRANSFORMED_RECORD;
		} else {
			addActionError("You must select an output record to view its details.");
			return INPUT;
		}
	}

	@SkipValidation
	public String paginate() {
		setPageNumber(0);
		return INPUT;
	}

	@SkipValidation
	public String setPage() {
		// get selected page from request param, if present
		String[] page = (String[])getParameters().get(PAGE);
		if (page != null && page.length > 0) {
			setPageNumber(Integer.parseInt(page[0]));
		} else {
			addActionError("You must select a page number to change pages.");
		}
		return INPUT;
	}

	@SkipValidation
	public String save() {
		try {
			IndexedDataRecord dataRecord = getTransformedRecord();
			UserDataItem dataItem = getWorkbenchSession().
				saveUserDataItem(getUserDataItem(dataRecord), getCurrentFolder());
			String message = "Data item \"" + truncateText(dataItem.getLabel()) +
				"\" successfully saved.";
			reportUserMessage(message);
			refreshFolderDataTabs();
			return DISPLAY_TRANSFORMED_OUTPUT;
		} catch (Throwable error) {
			reportError(error, "Error saving selected output data record");
			return ERROR;
		}
	}

	@SkipValidation
	public String saveSelected() {
		try {
			String[] selectedIds = getSelectedIds();
			if (selectedIds == null || selectedIds.length < 1) {
				addActionError("You must select one or more output records to save them.");
			} else {
				int saved = saveSelectedDataItems();
				String result = saved + " data item";
				if (saved != 1) result += "s were ";
				else result+= " was ";
				result += " successfully saved.";
				reportUserMessage(result);
				refreshFolderDataTabs();
			}
			return DISPLAY_TRANSFORMED_OUTPUT;
		} catch (Throwable error) {
			reportError(error, "Error saving selected output records");
			return ERROR;
		}
	}

	@SkipValidation
	public String downloadSelected() {
/**/
debug("SetTaskOutput.downloadSelected() has been called.");
/**/
		setSelectedDataItems();
		return ARCHIVE_DATA;
	}

	/*================================================================
	 * Data list form property accessor methods
	 *================================================================*/
	public String[] getSelectedOutputs() {
		return selectedOutputs;
	}

	public void setSelectedOutputs(String[] selectedOutputs) {
		this.selectedOutputs = selectedOutputs;
	}

	/*================================================================
	 * Output saving form property accessor methods
	 *================================================================*/
	public UserDataItem getDataItem() {
		if (dataItem == null)
			setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
		return dataItem;
	}

	public void setDataItem(UserDataItem dataItem) {
		this.dataItem = dataItem;
	}

	public String getLabel() {
		if (dataItem != null) return dataItem.getLabel();
		else return null;
	}

	public void setLabel(String label) {
		if (dataItem == null)
			setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
		dataItem.setLabel(label);
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
	// generate the list for dataformat dropdonw
	// try to guess the format if possible, but also allow user to override the option
	public List<String> getPhyloDataFormats() {
		List<String> dataFormatList = new ArrayList<String>();

		// BIG_FILES: just returning empty dataFormatList, commented out the rest.
		// If I can rewrite DataDetails.diagnoseFormat in a stream oriented way
		// I can put this logic back.

		return dataFormatList;

		/*
		SourceDocument taskOutput = getTaskOutput();
		if (taskOutput == null) return dataFormatList;
		byte[] data = null;
		try {
			data = taskOutput.getData();
		} catch (Throwable error) {
			reportError(error, "Error retrieving data from SourceDocument");
		}
		String dataFormat = DataDetails.diagnoseFormat(data).toString();
		dataFormatList.add(dataFormat);
		dataFormatList.add("UNKNOWN");
		//dataFormatList.add("TEXT");
		return dataFormatList;
		*/
	}

	/*================================================================
	 * Task output list display page property accessor methods
	 *================================================================*/
	public String getOutputKey(Map.Entry<String, List<TaskOutputSourceDocument>> mapEntry) {
		if (mapEntry == null)
			return null;
		else return mapEntry.getKey();
	}

	public String formatOutputKey(String outputKey) {
		if (outputKey == null)
			return null;
		else {
			// if the last character is an underscore, remove it
			if (outputKey.charAt(outputKey.length() - 1) == '_')
				return outputKey.substring(0, outputKey.length() - 1).trim();
			else return outputKey.trim();
		}
	}

	public String formatOutputKey(Map.Entry<String, List<TaskOutputSourceDocument>> mapEntry) {
		if (mapEntry == null)
			return null;
		else return formatOutputKey(getOutputKey(mapEntry));
	}

	public String getOutputFile(SourceDocument document) {
		if (document == null)
			return null;
		else return document.getName();
	}

	public long getOutputFileSize(SourceDocument outputFile) {
		if (outputFile == null)
			return 0;
		else try {
			return outputFile.getDataLength();
		} catch (Throwable error) {
			reportError(error, "Error retrieving size of data " +
				"from SourceDocument");
			return 0;
		}
	}

	public boolean canDisplay(SourceDocument outputFile) {
		if (outputFile == null)
			return false;
		else try {
			return outputFile.getDataLength() <= MAX_DATA_SIZE;
		} catch (Throwable error) {
			reportError(error, "Error retrieving size of data " +
				"from SourceDocument");
			return false;
		}
	}
	/*================================================================
	 * Task output display page property accessor methods
	 *================================================================*/
	public TaskOutputSourceDocument getTaskOutput() {
		//TODO: implement the proper user interface for selecting the inner map key for output
		try {
			WorkbenchSession session = getWorkbenchSession();
			Task task = getCurrentTask();
			String outputKey = getTaskOutputKey();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else if (outputKey == null)
				throw new NullPointerException("No task output parameter is currently selected.");
			else {
				Map<String, List<TaskOutputSourceDocument>> output = session.getOutput(task);
				if (output == null || output.size() < 1)
					throw new NullPointerException("The currently selected task has no output.");
				else {
					List<TaskOutputSourceDocument> selectedOutput = output.get(outputKey);
					if (selectedOutput == null || selectedOutput.size() < 1)
						throw new NullPointerException("The currently selected task output " +
							"parameter has no associated output files.");
					else if (selectedOutput.size() == 1) {
						return selectedOutput.get(0);
					} else {
						String outputFile = getTaskOutputFile();
						if (outputFile == null)
							throw new NullPointerException("No output file is selected " +
								"for the currently selected task output parameter.");
						else {
							TaskOutputSourceDocument outputDocument =
								getOutputDocument(selectedOutput, outputFile);
							if (outputDocument == null)
								throw new NullPointerException("No output file is present " +
									"for the currently selected task output filename.");
							else return outputDocument;
						}
					}
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving task output");
			return null;
		}
	}

	public String getTaskOutputString() {
		SourceDocument taskOutput = getTaskOutput();
		try {
			if (taskOutput == null)
				return null;
			else if (taskOutput.getDataLength() > MAX_DATA_SIZE)
				return null;	//TODO: should be a checked exception
			else {
				byte[] data = taskOutput.getData();
				if (data == null)
					return null;
				else return new String(data).trim();
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving data from " +
				"task output SourceDocument");
			return null;
		}
	}

	public long getOutputFileSize() {
		return getOutputFileSize(getTaskOutput());
	}

	public boolean canDisplay() {
		String contentType = getContentType();
		if (contentType != null && (contentType.startsWith("text") || contentType.startsWith("image")))
			return true;
		else return false;
	}

	/*
	 * This method is where dynamically dispatched output rendering mechanisms should be employed.
	 * The output should be properly formatted HTML to be placed directly into the output page.
	 * The contents of this HTML can be whatever is appropriate for displaying the particular output
	 * in a web page.
	 *
	 * This output is only appropriate for raw data that is meant to be viewed directly in a
	 * web page.  If the data corresponds to a more complex user interface, such as a
	 * DataRecordColllection that must be parsed into a form table with checkbox-selectable rows
	 * for saving as UserDataItems, then another interface will have to be provided as well.
	 *
	 * See DataManager.getFormattedSourceData()
	 */
	public String getFormattedOutput() {
		//TODO: implement dynamic tool-based rendering
		SourceDocument taskOutput = getTaskOutput();
		String contentType = getContentType();
		if (contentType == null || taskOutput == null)
			return null;
		else {
			byte[] data = null;
			try {
				if (taskOutput.getDataLength() > MAX_DATA_SIZE)
					return "<h4>Sorry, this file is too large to display. " +
						"To view this file, please download it " +
						"to your local machine.</h4>";
				else data = taskOutput.getData();
			} catch (Throwable error) {
				reportError(error, "Error retrieving data from SourceDocument");
			}
			if (data == null) {
				addActionError("There was an error retrieving the output data you selected.");
				return null;
			} else if (contentType.equals("text/html")) {
				String output = new String(data);
				String tool = getTool();
				if (tool != null && tool.equals("BOXSHADE"))
					return "<div class=\"boxshade\">" + output + "</div>";
				else return output;
			} else if (contentType.startsWith("text")) {
				return "<pre class=\"source\">" +
					StringEscapeUtils.escapeHtml(new String(data)) + "</pre>";
			} else if (contentType.startsWith("image"))
				return "<img src=\"setTaskOutput!displayOutputFile.action\"/>";
			else return null;
		}
	}

	public boolean canRead() {
		try 
		{
			Workbench workbench = getWorkbench();
			SourceDocument taskOutput = getTaskOutput();
			if (workbench == null)
			{
				throw new NullPointerException("No workbench is present.");
			}
			else if (taskOutput == null)
			{
				throw new NullPointerException("No task output is currently selected.");
			}
			else 
			{
				SourceDocumentType type = taskOutput.getType();
				if (type == null)
				{
					throw new NullPointerException("Currently selected task output has no " + "source document type.");
				}
				else 
				{
					return workbench.canRead(type);
				}
			}
		} catch (Throwable error) 
		{
			reportError(error, "Error determining whether currently selected " + "task output can be parsed into data records");
			return false;
		}
	}

	public boolean canTransform() {
		try {
			Workbench workbench = getWorkbench();
			SourceDocument taskOutput = getTaskOutput();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (taskOutput == null)
				throw new NullPointerException("No task output is currently selected.");
			else {
				SourceDocumentType type = taskOutput.getType();
				if (type == null)
					throw new NullPointerException("Currently selected task output has no " +
						"source document type.");
				else {
					Set<RecordType> targetTypes =
						workbench.getTransformationTargetRecordTypes(type);
					if (targetTypes != null && targetTypes.size() > 0)
						return true;
					else return false;
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error determining whether currently selected " +
				"task output can be transformed into a data record collection");
			return false;
		}
	}

	//TODO: This method extends getDataRecords() from ManageData.  This might be a problem.
	public GenericDataRecordCollection<IndexedDataRecord> getDataRecords() {
		// get the data records stored in the action
		if (dataRecords != null) {
			return dataRecords;
		}
		// if not found, retrieve the proper data records
		else try {
			Workbench workbench = getWorkbench();
			SourceDocument taskOutput = getTaskOutput();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (taskOutput == null)
				throw new NullPointerException("No task output is currently selected.");
			else {
 				dataRecords = workbench.read(taskOutput);
				return dataRecords;
			}
		} catch (Throwable error) {
			reportError(error, "Error parsing currently selected " +
				"task output into data records.");
			return null;
		}
	}

	public List<IndexedDataRecord> getDataRecordList() {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords();
		if (dataRecords == null)
			return null;
		else return dataRecords.toList();
	}

	public List<RecordFieldType> getRecordFields() {
		GenericDataRecordCollection<IndexedDataRecord> dataRecords = getDataRecords();
		if (dataRecords == null)
			return null;
		else return sortRecordFields(dataRecords.getFields());
	}

	public String getRecordField(RecordFieldType recordField) {
		if (recordField == null)
			return null;
		else return getConceptLabel("RecordField", recordField.toString());
	}

	public String getDataRecordField(IndexedDataRecord dataRecord, RecordFieldType recordField) {
		if (dataRecord == null || recordField == null)
			return null;
		else return dataRecord.getField(recordField).getValueAsString();
	}

	public String getShortDataRecordField(IndexedDataRecord dataRecord, RecordFieldType recordField) {
		String fieldValue = getDataRecordField(dataRecord, recordField);
		if (fieldValue == null)
			return null;
		else return truncateText(fieldValue);
	}

	public boolean isQuery(RecordFieldType recordField) {
		if (recordField == null)
			return false;
		else return recordField.equals(RecordFieldType.QUERY);
	}

	public boolean isPrimaryId(RecordFieldType recordField) {
		if (recordField == null)
			return false;
		else return recordField.equals(RecordFieldType.PRIMARY_ID);
	}

	/*================================================================
	 * Transformed task output record list page property accessor methods
	 *================================================================*/
	public List<IndexedDataRecord> getTransformedRecordList() {
		GenericDataRecordCollection<IndexedDataRecord> transformedRecords =
			getTransformedRecords();
		if (transformedRecords == null)
			return null;
		else return transformedRecords.toList();
	}

	public List<IndexedDataRecord> getTransformedRecordPage() {
		SourceDocumentTransformer transformer = getTransformer();
		if (transformer == null)
			return null;
		else try {
/**/
debug("Attempting to retrieve transformed record page...");
debug("Number of first element on this page = " + getThisPageFirstElementNumber());
debug("Number of last element on this page = " + getThisPageLastElementNumber());
/**/
			return transformer.getDataRecordCollection(
				getThisPageFirstElementNumber(),
				(getThisPageLastElementNumber() + 1)).toList();
		} catch (Throwable error) {
			reportError(error, "Error retrieving transformed record page");
			return null;
		}
	}

	public boolean hasTransformedRecords() {
		SourceDocumentTransformer transformer = getTransformer();
		return (transformer != null && transformer.getTotalDataRecordCount() > 0);
	}

	public int getResultCount() {
		SourceDocumentTransformer transformer = getTransformer();
		if (transformer == null)
			return 0;
		else return transformer.getTotalDataRecordCount();
	}

	public List<RecordFieldType> getTransformedRecordFields() {
		Workbench workbench = getWorkbench();
		SourceDocumentTransformer transformer = getTransformer();
		if (transformer == null)
			return null;
		else return sortRecordFields(workbench.getRecordFields(transformer.getTargetType()));
	}

	public String getTransformedRecordField(IndexedDataRecord dataRecord, RecordFieldType recordField) {
		if (dataRecord == null || recordField == null)
			return null;
		else return truncateText(dataRecord.getField(recordField).getValueAsString());
	}

	public String getFieldValue(RecordFieldType field) {
		try {
			IndexedDataRecord dataRecord = getTransformedRecord();
			if (dataRecord == null)
				throw new NullPointerException("No output record is currently selected.");
			else return getTransformedRecordField(dataRecord, field);
		} catch (Throwable error) {
			reportError(error, "Error retrieving value of field \"" + field +
				"\" of current output record");
			return null;
		}
	}

	public String getId(DataRecord dataRecord) {
		return dataRecord.getField(RecordFieldType.PRIMARY_ID).getValueAsString();
	}

	public String getLabel(DataRecord dataRecord) {
		return dataRecord.getField(RecordFieldType.NAME).getValueAsString();
	}

	/*================================================================
	 * Transformed task output record display page property accessor methods
	 *================================================================*/
	public SourceDocument getSourceDocument() {
		// first try the source document stored in the action
		if (sourceDocument != null)
			return sourceDocument;
		// if not found, retrieve it from the workbench
		else try {
			SourceDocumentTransformer transformer = getTransformer();
			IndexedDataRecord dataRecord = getTransformedRecord();
			if (transformer == null)
				throw new NullPointerException("No output transformer is present.");
			else if (dataRecord == null)
				throw new NullPointerException("No output record is currently selected.");
			else {
				sourceDocument = transformer.getTransformedSourceDocument(dataRecord);
				return sourceDocument;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving source document from current output record");
			return null;
		}
	}

	public boolean hasSourceDocument() {
		if (sourceDocument != null)
			return true;
		else try {
			return (getSourceDocument() != null);
		} catch (Throwable error) {
			reportError(error, "Error determining whether current output record " +
				"has a source document");
			return false;
		}
	}

	/*================================================================
	 * Task output download property accessor methods
	 *================================================================*/
	public String getFilename() {
		String outputFile = getTaskOutputFile();
		if (outputFile != null)
			return outputFile;
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

	public long getDataLength() {
		SourceDocument taskOutput = getTaskOutput();
		try {
			if (taskOutput == null)
				return 0;
			else return taskOutput.getDataLength();
		} catch (Throwable error) {
			reportError(error, "Error retrieving size of data " +
				"from SourceDocument");
			return 0;
		}
	}

	public InputStream getInputStream() {
		SourceDocument taskOutput = getTaskOutput();
		if (taskOutput == null)
			return null;
		else try {
			return taskOutput.getDataAsStream();
		} catch (Throwable error) {
			reportError(error, "Error retrieving streamed data " +
				"from SourceDocument");
			return null;
		}
	}

	/*================================================================
	 * Page methods
	 *================================================================*/
	//TODO: should have a proper page implementation for this
	public Integer getPageSize() {
		return (Integer)getSession().get(PAGE_SIZE);
	}

	public String getPageSizeString() {
		return getPageSize().toString();
	}

	@SuppressWarnings("unchecked")
	public void setPageSize(Integer pageSize) {
		getSession().put(PAGE_SIZE, pageSize);
	}

	public Integer getPageNumber() {
		return (Integer)getSession().get(PAGE_NUMBER);
	}

	@SuppressWarnings("unchecked")
	public void setPageNumber(Integer pageNumber) {
		getSession().put(PAGE_NUMBER, pageNumber);
	}

	public boolean isFirstPage() {
		return (getPageNumber() == 0);
	}

	public boolean isLastPage() {
		return (getPageNumber() == getLastPageNumber());
	}

	public boolean hasPreviousPage() {
		return (getPageNumber() > 0);
	}

	public int getPreviousPageNumber() {
		if (hasPreviousPage()) return getPageNumber() - 1;
		else return getPageNumber();
	}

	public boolean hasNextPage() {
		return (getPageNumber() < getLastPageNumber());
	}

	public int getNextPageNumber() {
		if (hasNextPage()) return getPageNumber() + 1;
		else return getPageNumber();
	}

	public int getLastPageNumber() {
		int numberOfPages =
			(int)Math.ceil((double)getResultCount() / getPageSize());
		if (numberOfPages <= 0) return 0;
		else return (numberOfPages - 1);
	}

	public int getThisPageFirstElementNumber() {
		return (getPageNumber() * getPageSize());
	}

	public int getThisPageLastElementNumber() {
		int lastElementNumber =
			getThisPageFirstElementNumber() + getThisPageNumberOfElements() - 1;
		if (lastElementNumber <= 0) return 0;
		else return lastElementNumber;
	}

	public int getThisPageNumberOfElements() {
		if (isLastPage() == false) return getPageSize();
		else return (getResultCount() - getThisPageFirstElementNumber());
	}

	/*================================================================
	 * Internal property accessor methods
	 *================================================================*/
	protected String getTaskOutputKey() {
		return (String)getSessionAttribute(TASK_OUTPUT_KEY);
	}

	protected void setTaskOutputKey(String outputKey) {
		setSessionAttribute(TASK_OUTPUT_KEY, outputKey);
	}

	protected String getTaskOutputFile() {
		return (String)getSessionAttribute(TASK_OUTPUT_FILE);
	}

	protected void setTaskOutputFile(String outputFile) {
		setSessionAttribute(TASK_OUTPUT_FILE, outputFile);
	}

	protected TaskOutputSourceDocument getOutputDocument(List<TaskOutputSourceDocument> output,
		String filename) {
		if (output == null || output.size() < 1 || filename == null)
			return null;
		else {
			for (TaskOutputSourceDocument outputDocument : output) {
				if (filename.equals(outputDocument.getName()))
					return outputDocument;
			}
			return null;
		}
	}

	protected boolean containsOutputDocument(List<TaskOutputSourceDocument> output,
		String filename) {
		TaskOutputSourceDocument outputDocument = getOutputDocument(output, filename);
		if (outputDocument == null)
			return false;
		else return true;
	}

	protected void setSelectedDataItems() {
		String[] selectedOutputs = getSelectedOutputs();
		if (selectedOutputs == null || selectedOutputs.length < 1)
			clearSessionAttribute(SELECTED_DATA_ITEMS);
		else {
			List<TaskOutputSourceDocument> outputs =
				new Vector<TaskOutputSourceDocument>(selectedOutputs.length);
			for (int i=0; i<selectedOutputs.length; i++) {
				String[] identifiers = selectedOutputs[i].split(":");
				if (identifiers == null || identifiers.length != 2) {
					debug("Error retrieving selected task output: \"" +
						selectedOutputs[i] + "\" does not correspond " +
						"to a valid task output document.");
					continue;
				} else {
					TaskOutputSourceDocument output = null;
					Map<String, List<TaskOutputSourceDocument>> outputMap =
						getOutput();
					if (outputMap != null) {
						List<TaskOutputSourceDocument> outputList =
							outputMap.get(identifiers[0]);
						if (outputList != null) {
							Integer index = null;
							try {
								index = Integer.parseInt(identifiers[1]);
							} catch (NumberFormatException error) {}
							if (index != null)
								output = outputList.get(index);
						}
					}
					if (output == null) {
						debug("Error retrieving selected task output: \"" +
							selectedOutputs[i] + "\" was not found in the " +
							"current task's output map.");
					} else outputs.add(output);
				}
			}
			if (outputs.size() > 0)
			{
				// Commented out following as the duplicate filename removal
				// will be done during archive in ArchiveManager.  Don't
				// need to do it here. But if the other place doesn't cover
				// all cases and this does, can be done here too.
				//outputs = removeDuplicateFilenames ( outputs );
				setSessionAttribute(SELECTED_DATA_ITEMS, outputs);
			}
			else clearSessionAttribute(SELECTED_DATA_ITEMS);
		}
	}

	protected SourceDocumentTransformer getTransformer() {
		return (SourceDocumentTransformer)getSession().get(TRANSFORMER);
	}

	@SuppressWarnings("unchecked")
	protected void setTransformer(SourceDocumentTransformer transformer) {
		getSession().put(TRANSFORMER, transformer);
	}

	protected GenericDataRecordCollection<IndexedDataRecord> getTransformedRecords() {
		SourceDocumentTransformer transformer = getTransformer();
		if (transformer == null)
			return null;
		else try {
			return transformer.getDataRecordCollection();
		} catch (Throwable error) {
			reportError(error, "Error retrieving DataRecordCollection from Transformer");
			return null;
		}
	}

	protected IndexedDataRecord getTransformedRecord() {
		return (IndexedDataRecord)getSessionAttribute(TRANSFORMED_RECORD);
	}

	protected void setTransformedRecord(IndexedDataRecord transformedRecord) {
		setSessionAttribute(TRANSFORMED_RECORD, transformedRecord);
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/
	protected UserDataItem saveDataItem() {
		// set data concepts

		// This is the item we're saving the task result too.
		UserDataItem dataItem = getDataItem();

		EntityType entityType = EntityType.valueOf(getEntityType());
		if (entityType == null || entityType.toString().equals(""))
			dataItem.setEntityType(EntityType.valueOf(UNKNOWN));
		else dataItem.setEntityType(entityType);

		DataType dataType = DataType.valueOf(getDataType());
		if (dataType == null || dataType.toString().equals(""))
			dataItem.setDataType(DataType.valueOf(UNKNOWN));
		else dataItem.setDataType(dataType);

		String dataFormat = getDataFormat();
		if (dataFormat == null || dataFormat.equals(""))
			dataItem.setDataFormat(DataFormat.valueOf(UNKNOWN));
		else dataItem.setDataFormat(DataFormat.valueOf(dataFormat));

		// set file source
		InputStream is = null;
		try 
		{
			is = getTaskOutput().getDataAsStream();
			if (is != null)
			{
				dataItem.setData(is); // this closes the stream.
			} else
			{
				addActionError("There was an error saving the output data you selected.");
				return null;
			}
		} 
		catch (Throwable error) 
		{
			reportError(error, "Error retrieving data from SourceDocument or writing it to UserDataItem");
		}
		
		// save data
		try 
		{
			dataItem = getWorkbenchSession().saveUserDataItem(dataItem, getCurrentFolder());
			refreshFolderDataTabs();
			return dataItem;
		} 
		catch (Throwable error) 
		{
			reportError(error, "Error saving selected output data");
			addActionError("There was an error saving the output data you selected.");
			return null;
		}
	}

	protected boolean resolveConcepts() {
		String entityType = getEntityType();
		String dataType = getDataType();
		String dataFormat = getDataFormat();
		if (entityType == null || entityType.equals("")) {
			setEntityType(null);
			setDataType(null);
			setDataFormat(null);
			return false;
		} else {
			Set<DataType> dataTypes = getDataTypeSet(EntityType.valueOf(entityType));
			if (dataType == null || dataType.equals("") || dataTypes == null ||
				dataTypes.contains(DataType.valueOf(dataType)) == false) {
				setDataType(null);
				setDataFormat(null);
				return false;
			} else {
				Set<DataFormat> dataFormats =
					getDataFormatSet(EntityType.valueOf(entityType), DataType.valueOf(dataType));
				if (dataFormat == null || dataFormat.equals("") || dataFormats == null ||
					dataFormats.contains(DataFormat.valueOf(dataFormat)) == false) {
					setDataFormat(null);
					return false;
				} else return true;
			}
		}
	}

	protected boolean validateData() {
		UserDataItem dataItem = getDataItem();
		if (dataItem == null)
			return false;
		else {
			String label = getLabel();
			if (label == null || label.equals("")) {
				addFieldError("label", "Label is required.");
				return false;
			//} else if (resolveConcepts() == false) {
			//	addActionError("You must properly tag your data with valid type values.");
			//return false;
			} else return true;
		}
	}

	protected RecordType getTargetRecordType(SourceDocumentType sourceType) {
		//TODO: properly implement, might need to know about tool, or tool mode
		if (sourceType == null)
			return null;
		else {
			EntityType entityType = sourceType.getEntityType();
			DataType dataType = sourceType.getDataType();
			if (entityType == null || dataType == null)
				return null;
			else if (entityType.equals(EntityType.valueOf(NUCLEIC_ACID))) {
				if (dataType.equals(DataType.valueOf(BLAST_OUTPUT)) ||
					dataType.equals(DataType.valueOf(FASTA_OUTPUT)))
					return RecordType.valueOf(NUCLEIC_ACID_SIMILARITY_SEARCH_HIT);
				else return null;
			} else if (sourceType.getEntityType().equals(EntityType.valueOf(PROTEIN))) {
				if (dataType.equals(DataType.valueOf(BLAST_OUTPUT)) ||
					dataType.equals(DataType.valueOf(FASTA_OUTPUT)))
					return RecordType.valueOf(PROTEIN_SIMILARITY_SEARCH_HIT);
				else return null;
			} else return null;
		}
	}

	protected boolean transformOutput(int index) {
		try {
			Workbench workbench = getWorkbench();
			SourceDocument sourceDocument = getTaskOutput();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (sourceDocument == null)
				throw new NullPointerException("No output is present.");
			else {
				SourceDocumentType sourceType = sourceDocument.getType();
				if (sourceType == null)
					throw new NullPointerException("The selected output has no type.");
				else if (sourceType.isIncomplete())
					throw new RuntimeException(
						"The selected output has an incomplete type.");
				else {
					//TODO: remove this hack and implement a more generic solution
					RecordType targetType = getTargetRecordType(sourceType);
					Set<RecordType> targetTypes =
						workbench.getTransformationTargetRecordTypes(sourceType);
/**/
debug("Examining transformation target RecordTypes for SourceDocumentType \"" +
	sourceType.toString() + "\":");
for (RecordType recordType : targetTypes) {
	debug("\"" + recordType.toString() + "\"");
}
/**/
					if (targetType == null || targetTypes.contains(targetType) == false)
						throw new RuntimeException(
							"The selected output has an unrecognized type.");
					else {
						SourceDocument record =
							workbench.extractSubSourceDocument(sourceDocument, index);
						SourceDocumentTransformer transformer =
							workbench.getTransformer(record, targetType);
						if (transformer == null)
							return false;
						else {
							setTransformer(transformer);
							return true;
						}
					}
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error transforming selected output data record");
			return false;
		}
	}

	protected int saveSelectedDataItems() {
		int saved = 0;
        Long id;
		String[] selectedIds = getSelectedIds();
		List<IndexedDataRecord> results = getTransformedRecordPage();
		if (selectedIds == null || selectedIds.length < 1)
			return saved;
		else for (int i=0; i<selectedIds.length; i++) {
            id = Long.parseLong ( selectedIds[i].split ( "-" )[1].trim() );
			IndexedDataRecord dataRecord = results.get ( id.intValue() );
			if ( dataRecord == null )
            {
				addActionError ( "Error saving output record with index " +
					id + ": item not found in record list." );
            }
			else {
				UserDataItem dataItem = getUserDataItem(dataRecord);
				try {
					dataItem = getWorkbenchSession().saveUserDataItem(dataItem, getCurrentFolder());
					saved++;
				} catch (Throwable error) {
					reportError(error, "Error saving selected output data");
				}
			}
		}
		refreshFolderDataTabs();
		return saved;
	}

	protected UserDataItem getUserDataItem(IndexedDataRecord dataRecord) {
		WorkbenchSession session = getWorkbenchSession();
		UserDataItem dataItem = session.getUserDataItemInstance(getCurrentFolder());
		String label = getLabel(dataRecord);
		if (label == null)
			return null;
		else if (label.length() > 255)
			label = label.substring(0, 255);
		dataItem.setLabel(label);
		SourceDocumentTransformer transformer = getTransformer();
		if (transformer == null)
			return null;
		else {
			SourceDocument document = transformer.getTransformedSourceDocument(dataRecord);
			if (document == null)
				return null;
			else {
				dataItem.setEntityType(document.getEntityType());
				dataItem.setDataType(document.getDataType());
				dataItem.setDataFormat(document.getDataFormat());
				byte[] data = null;
				try {
					data = document.getData();
				} catch (Throwable error) {
					reportError(error, "Error retrieving data from SourceDocument");
				}
				if (data == null)
					return null;
				else dataItem.setData(data);
			}
		}
		return dataItem;
	}
	
	
	/**
	 * Remove duplicate filename documents from list
	 * @author mona
	 * @return null or List of TaskOutputSourceDocument
	 */
	protected List<TaskOutputSourceDocument> removeDuplicateFilenames
		( List<TaskOutputSourceDocument> list )
	{
		if ( list == null || list.size() < 1 )
			return ( list );
		
		int size = list.size();
		List <String> filenames = new ArrayList();
		List <TaskOutputSourceDocument> new_list = new ArrayList <TaskOutputSourceDocument> ();
		String filename = null;
		TaskOutputSourceDocument doc;
		
		for ( int i = 0; i < size; i++ )
		{
			doc = list.get ( i );
			filename = doc.getName();
			
			if ( ! filenames.contains ( filename ) )
			{
				filenames.add ( filename );
				new_list.add ( doc );
			}
		}
		
		/* This code didn't work; it tossed away files that are not duplicate name also
		 * (eg .d-lock1)
		Set<TaskOutputSourceDocument> set = new HashSet<TaskOutputSourceDocument> ( list );
		List<TaskOutputSourceDocument> new_list = new Vector<TaskOutputSourceDocument> ( set );
		*/
		
		return ( new_list );
	}
	
}
