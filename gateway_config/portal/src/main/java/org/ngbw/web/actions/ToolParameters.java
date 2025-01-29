package org.ngbw.web.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.tool.ParameterValidator;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.actions.CreateTask;

@SuppressWarnings("serial")
public abstract class ToolParameters extends CreateTask
{
	private static final Logger logger = Logger.getLogger(ToolParameters.class.getName());
	/*================================================================
	 * Properties
	 *================================================================*/
	// all parameters will be stored in a single generic map.  
	private Map<String, String> toolParameters;
	private Map<String, String> uiParameters;

	private Map<String, List<TaskInputSourceDocument>> inputDataItems;

	private String disabledFields;
	public String getDisabledFields__()
	{
		return disabledFields;
	}
	public void setDisabledFields__(String s)
	{
		disabledFields = s;
	}
	
	/*================================================================
	 * Constructors
	 *================================================================*/
	public ToolParameters() {
		super();
		setTab(SET_PARAMETERS);
	}

	//modifies params.
	private void removeDisabledFields(Map<String, String> params, String disabled)
	{
		if (disabled != null)
		{
			String[] disabledKeys = disabled.split(",");
			for (String dkey : disabledKeys)
			{
				if (params.containsKey(dkey.trim()))
				{
					params.remove(dkey.trim());
				}
			}
		}
	}
	
	/*================================================================
	 * Action methods
	 *================================================================*/
	@SuppressWarnings("unchecked")
	public String execute() 
	{
		//logger.debug ( "MONA: entered ToolParameters.execute()" );
		try
		{
			logger.debug("disabled field list is " + getDisabledFields__());
			removeDisabledFields(toolParameters, getDisabledFields__());

			// validate parameters
			boolean error = false;
			Task task = getCurrentTask();
		    //logger.debug ( "MONA: task = " + task );
			ParameterValidator validator = getParameterValidator(task.getToolId());
		    //logger.debug ( "MONA: validator = " + validator );

			/*
			logger.debug("After we remove disabled params, we have:");
			for (String name : toolParameters.keySet())
			{
				logger.debug(name + "=" + toolParameters.get(name));
			}
			*/

			/*
			logger.debug("These are ALL the request parameters (with string keys) that we received.");
			Map pmap = getParameters();
			for (Object obj : pmap.keySet())
			{
				if (!(obj instanceof String))
				{
					continue;
				}
				String value = getRequestParameter((String)obj);
				logger.debug((String)obj + "='" + value + "'");
			}
			*/



			Map<String, String> errors = validator.validateParameters(toolParameters);
			if (errors != null && errors.size() > 0) {
				for (String parameter : errors.keySet())
					addActionError(errors.get(parameter));
				error = true;
			}
			errors = validator.validateInput(getInputDataItems());
			if (errors != null && errors.size() > 0) {
				for (String parameter : errors.keySet())
					addActionError(errors.get(parameter));
				error = true;
			}
		    //logger.debug ( "MONA: error = " + error );
		
			// return to input page if validation failed
			if (error) {
				setTab(SET_PARAMETERS);
				return INPUT;
			}
		
			// set toolParameters into task
			setParameters(task, cleanParameters());

			setInputMap(cleanInput());
			List<TaskInputSourceDocument> input = getInput();
		    //logger.debug ( "MONA: input = " + input );
			if (input != null)
				setInput(input);
			
			// report number of parameters successfully set
			int parameterCount = getParameterCount();
		    //logger.debug ( "MONA: parameterCount = " + parameterCount );
			String message = parameterCount + " parameter";
		    //logger.debug ( "MONA: message = " + message );
			if (parameterCount != 1)
				message += "s";
			message += " successfully set to current task.";
			reportUserMessage(message);
			setTab(TASK_SUMMARY);

			// Let CreateTask know that the tool gui was opened and parameters were saved.
			logger.debug("Adding TOOL_GUI_OPENED");
			task.toolParameters().put(TOOL_GUI_OPENED, "1");

			return SUCCESS;
		}
		catch(Exception e)
		{
			error("", e);
			reportUserError("Internal Error.");
			return INPUT;
		}
	}
	
	/*
		I think this is called anytime we display the tool gui page. 
		It puts initial values in uiParameters map.  The generated action classes
		have getter methods that return the values from uiParameters so they'll be
		displayed in the form.

		generated action classes inherit from ToolParameters and are:
		pise2JavaSimple.ftl, pise2JavaAdvanced.ftl -> build/portal/src/main/java/org/ngbw/web/actions/tool/<tool>.java
	*/
	@SkipValidation
	public String input() {


		// If the task has parameters, put them in uiParameters to be displayed as initial values. 
		if (hasParameters()) {
			try {
				setUIParameters(getCurrentTask().toolParameters());
			} catch (Throwable error) {
				reportError(error, "Error retrieving parameters from current task");
			}
			setInputDataItems(getInputMap());
			Map<String, List<TaskInputSourceDocument>> inputDataItems = getInputDataItems();
			if (inputDataItems != null && inputDataItems.size() > 0)
				for (String parameter : inputDataItems.keySet())
					verifyInputDataItem(parameter);
		} else
		{
			// If task doesn't already have parameters, this puts an entry in uiParameters for each parameter that has a vdef.
			reset();
		}
		return INPUT;
	}
	
	@SkipValidation
	public String cancel() {
		clearErrorsAndMessages();
		addActionMessage("Task parameters not saved.");
		setTab(TASK_SUMMARY);
		return INPUT;
	}

	@SkipValidation
	public String resetPage() {
		reset();
		setTab(SET_PARAMETERS);
		return INPUT;
    }
	
	/*================================================================
	 * Tool parameter property accessor methods
	 *================================================================*/
	protected  void setToolParameter(String name, String value) 
	{
		getToolParameters().put(name, value);
	}

	private  Map<String, String> getToolParameters() 
	{
		if (toolParameters == null)
		{
			resetParameters();
		}
		return toolParameters;
	}

	protected Map<String, String> getUIParameters() 
	{
		if (uiParameters == null)
		{
			resetParameters();
		}
		return uiParameters;
	}
	
	private void setUIParameters(Map<String, String> parameters) {
		if (parameters != null)
		{
			this.uiParameters = parameters;
		}
		else resetParameters();
	}
	
	public Map<String, List<TaskInputSourceDocument>> getInputDataItems() {
		if (inputDataItems == null)
			resetInput();
		return inputDataItems;
	}
	
	public void setInputDataItems(Map<String, List<TaskInputSourceDocument>> inputDataItems) {
		if (inputDataItems != null)
			this.inputDataItems = inputDataItems;
		else resetInput();
	}
	
	/*
	 * This implementation assumes that each "non-main" input parameter has only one
	 * SourceDocument in its map, and this value is keyed by the database ID of the
	 * single UserDataItem that spawned it.
	 * 
	 * If this UserDataItem no longer exists in the database for whatever reason,
	 * then searching for it by the ID map key will return null, and this will indicate
	 * that it is no longer attached to a persisted data item.
	 */
	public Long getInputDataItemId(String parameter) {
		try {
			Map<String, List<TaskInputSourceDocument>> inputDataItems = getInputDataItems();
			if (parameter == null)
				throw new NullPointerException("Parameter key is null.");
			else if (inputDataItems == null)
				throw new NullPointerException("No input parameter map is present.");
			else {
				List<TaskInputSourceDocument> parameterData = inputDataItems.get(parameter);
				if (parameterData == null || parameterData.size() < 1)
					return null;
				else {
					String id = parameterData.get(0).getName();
					return Long.parseLong(id);
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving user selection for input parameter");
			return null;
		}
	}
	
	public void setInputDataItem(String parameter, Long dataId) {
		try {
			Map<String, List<TaskInputSourceDocument>> inputDataItems = getInputDataItems();
			if (parameter == null)
				throw new NullPointerException("Parameter key is null.");
			else if (inputDataItems == null)
				throw new NullPointerException("No input parameter map is present.");
			else if (dataId == null) {
				inputDataItems.remove(parameter);
			} else {
				WorkbenchSession session = getWorkbenchSession();
				if (session == null)
					throw new NullPointerException("No session is present.");
				else 
				{
					UserDataItem dataItem = session.findUserDataItem(dataId);
					if (dataItem == null || dataItem.isNew())
					{
						throw new NullPointerException("Selected data item is " +
							"not present in the database.");
					}
					else 
					{
						// We have a dataItem = a userDataItem
						SourceDocument sourceDocument = session.getSourceDocument(dataItem);
						if (sourceDocument == null)
						{
							throw new NullPointerException("Selected data item has " + "no source document.");
						}
						else 
						{
							List<TaskInputSourceDocument> input = new Vector<TaskInputSourceDocument>(1);
							TaskInputSourceDocument inputDocument = new TaskInputSourceDocument(sourceDocument);
							inputDocument.setName(Long.toString(dataItem.getUserDataId()));
							input.add(inputDocument);
							inputDataItems.put(parameter, input);
							setInputDataItems(inputDataItems);
						}
					}
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error setting data as input parameter");
		}
	}

	// This is called by generated tool form actions and CreateTask.java.
	public void reset() {
		resetParameters();
		resetInput();
    }
	
	/*================================================================
	 * Parameter selection page property accessor methods
	 *================================================================*/
	public Map<Long, String> getDataForParameter(String parameter) {
		String tool = getTool();
		if (tool == null || parameter == null)
			return null;
		else {
			//TODO: need getDataForParameter() business method
			List<UserDataItem> dataList = null;
			try {
				dataList = getCurrentFolder().findDataItems();
			} catch (Throwable error) {
				reportError(error, "Error retrieving current folder's data list");
				return null;
			}
			Map<Long, String> dataMap = new HashMap<Long, String>(dataList.size());
			for (UserDataItem dataItem : dataList) {
				dataMap.put(dataItem.getUserDataId(), truncateText(dataItem.getLabel()));
			}
			return dataMap;
		}
	}

	public List<String> getDataForParameterAsList(String parameter) {
		String tool = getTool();
		if (tool == null || parameter == null)
			return null;
		else {
			//TODO: need getDataForParameter() business method
			List<UserDataItem> dataList = null;
			try {
				dataList = getCurrentFolder().findDataItems();
			} catch (Throwable error) {
				reportError(error, "Error retrieving current folder's data list");
				return null;
			}
		    List<String> dataMap = new ArrayList<String>(dataList.size());
			for (UserDataItem dataItem : dataList) {
				dataMap.add("<option value=\"" + dataItem.getUserDataId() + "\">" +  truncateText(dataItem.getLabel()) + "</option>");
			}
			return dataMap;
		}
	}

	public List<String> getValueForParameterAsList(String parameter) {
		String tool = getTool();
		if (tool == null || parameter == null)
			return null;
		else {
			//TODO: need getDataForParameter() business method
			List<UserDataItem> dataList = null;
			try {
				dataList = getCurrentFolder().findDataItems();
			} catch (Throwable error) {
				reportError(error, "Error retrieving current folder's data list");
				return null;
			}
		    List<String> dataMap = new ArrayList<String>(dataList.size());
			for (UserDataItem dataItem : dataList) {
				dataMap.add(Long.toString(dataItem.getUserDataId()));
			}
			return dataMap;
		}
	}

	public List<String> getLabelForParameterAsList(String parameter) {
		String tool = getTool();
		if (tool == null || parameter == null)
			return null;
		else {
			//TODO: need getDataForParameter() business method
			List<UserDataItem> dataList = null;
			try {
				dataList = getCurrentFolder().findDataItems();
			} catch (Throwable error) {
				reportError(error, "Error retrieving current folder's data list");
				return null;
			}
		    List<String> dataMap = new ArrayList<String>(dataList.size());
			for (UserDataItem dataItem : dataList) {
				dataMap.add(truncateText(dataItem.getLabel()));
			}
			return dataMap;
		}
	}
	
	/*================================================================
	 * Convenience methods
	 *================================================================*/
	protected void resetParameters() {
		if (toolParameters == null)
		{
			toolParameters = new HashMap<String, String>();
		}
		if (uiParameters == null)
		{
			uiParameters = new HashMap<String, String>();
		}
		toolParameters.clear();
		uiParameters.clear();
    }
	
	protected void resetInput() {
		if (inputDataItems == null)
			inputDataItems = new HashMap<String, List<TaskInputSourceDocument>>();
		inputDataItems.clear();
    }
	
	protected Map<String, String> cleanParameters() {
		if (toolParameters == null)
			return null;
		else {
			List<String> emptyParameters = new Vector<String>();
			//logger.debug("Form submitted with these parameters:");
			for (String parameter : toolParameters.keySet()) {
				String value = toolParameters.get(parameter);
				if (value == null || value.trim().equals(""))
				{
					emptyParameters.add(parameter);
				} else
				{
					//logger.debug(parameter + "='" + value + "'");
				}
			}
			//logger.debug("Removing these empty parameters:");
			for (String emptyParameter : emptyParameters) 
			{
				toolParameters.remove(emptyParameter);
				//logger.debug("Remove " + emptyParameter);
			}
		}
		return toolParameters;
	}
	
	protected Map<String, List<TaskInputSourceDocument>> cleanInput() {
		if (inputDataItems == null)
			return null;
		else {
			List<String> emptyParameters = new Vector<String>();
			for (String parameter : inputDataItems.keySet()) {
				List<TaskInputSourceDocument> value = inputDataItems.get(parameter);
				if (value == null || value.size() < 1)
					emptyParameters.add(parameter);
			}
			for (String emptyParameter : emptyParameters) {
				toolParameters.remove(emptyParameter);
			}
		}
		return inputDataItems;
	}
	
	protected void verifyInputDataItem(String parameter) {
		//logger.debug ( "MONA: entered ToolParameters.verifyInputDataItem()" );
		//logger.debug ( "MONA: parameter = " + parameter );
		try {
			Map<String, List<TaskInputSourceDocument>> inputDataItems =
				getInputDataItems();
		    //logger.debug ( "MONA: inputDataItems = " + inputDataItems );
			if (parameter == null)
				throw new NullPointerException("Parameter key is null.");
			else if (inputDataItems == null)
				throw new NullPointerException("No input parameter map " +
					"is present.");
			else {
				List<TaskInputSourceDocument> parameterData =
					inputDataItems.get(parameter);
		        //logger.debug ( "MONA: parameterData = " + parameterData );
				if (parameterData == null || parameterData.size() < 1)
					throw new NullPointerException("Parameter \"" +
						parameter + "\" is not present in the input " +
						"parameter map.");
				else {
					String id = parameterData.get(0).getName();
		            //logger.debug ( "MONA: id = " + id );
					Long dataId = Long.parseLong(id);
		            logger.debug ( "MONA: dataId = " + dataId );
					boolean found = false;
					if (dataId != null)
                    {
                        List<? extends FolderItem> allItems = 
							getCurrentFolder().findDataAllItems();
		                //logger.debug ( "MONA: allItems = " + allItems );
						for ( FolderItem dataItem : allItems )
                        {
		                    //logger.debug ( "MONA: dataItem = " + dataItem );
		                    //logger.debug ( "MONA: dataItem.class = " + dataItem.class );
                            Long x = 0L;

                            if ( dataItem.getClass().getSimpleName().equals (
                                "UserDataItem" ) )
                                x = ( ( UserDataItem )
                                    dataItem ).getUserDataId();
                            else if (
                                dataItem.getClass().getSimpleName().equals (
                                "UserDataDirItem" ) )
                                x = ( ( UserDataDirItem )
                                    dataItem ).getUserDataId();
		                    //logger.debug ( "MONA: x = " + x );
                            if ( dataId.equals ( x ) )
                            {
                                found = true;
                                break;
                            }
                        }
                    }
					if (found == false)
						reportUserError("Warning: Parameter \"" +
							parameter + "\" must be re-selected, because " +
							"the data item previously assigned to it " +
							"is no longer present in the current folder.");
				}
			}
		} catch (NullPointerException error) {
			reportCaughtError(error, "Error verifying persistence of input " +
				"parameter");
		} catch (Throwable error) {
			reportError(error, "Error verifying persistence of input " +
				"parameter");
		}
	}
}
