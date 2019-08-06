package org.ngbw.web.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.apache.log4j.Logger;

import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.UsageLimitException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.tool.Tool;
import org.ngbw.sdk.tool.DisabledResourceException;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.tool.ParameterValidator;
import org.ngbw.sdk.core.shared.TaskRunStage;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.util.UserDataItemSortableField;
import org.ngbw.sdk.jobs.UsageLimit;

/**
 * Struts action class to handle creation (create/edit)
 * of tasks in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class CreateTask extends ManageTasks
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(CreateTask.class.getName());

	/*
		User must open tool gui and save parameters, so javascript preconds and ctrls are eval'd and defaults added.
		Gui must be opened for new jobs and and if the tool is changed.
		For cloned jobs gui doesn't need to be opened (unless the tool is changed).

		We communicate between the tool gui (ToolParameters) and the CreateTask gui by putting TOOL_GUI_OPENED
		in task.toolParameters:

		1) ToolParameters.execute inserts TOOL_GUI_OPENED when the parameters are saved, as does CreateTask.clone.
		2) Changing the tool, clears all parameters, including TOOL_GUI_OPENED.
		3) Remove TOOL_GUI_OPENED right before saving the task and we don't want to save this parameter.
	*/
	protected static final String TOOL_GUI_OPENED = "gui_opened__";

	// result constants
	public static final String SET_INPUT = "setInput";
	public static final String PARAMETERS = "parameters";
	public static final String TOOLS = "tools";

	// parameter attribute key constants
	public static final String SELECTED_TOOL = "selectedTool";
	public static final String TASK = "task";

	// session attribute key constants
	public static final String INPUT_DATA = "inputData";

	// task action constants
	public static final String SET_DESCRIPTION = "Set Description";
	public static final String SAVE_TASK = "Save Task";
	public static final String SAVE_AND_RUN_TASK = "Save and Run Task";
	public static final String DISCARD_TASK = "Discard Task";

	// task form tab label constants
	public static final String TASK_SUMMARY = "Task Summary";
	public static final String SELECT_DATA = "Select Data";
	public static final String SELECT_TOOL = "Select Tool";
	public static final String SET_PARAMETERS = "Set Parameters";

	// result constants
	public static final String CREATE_FOLDER = "createFolder";

	// error message constants
	public static final String CANNOT_SUBMIT =
		"Sorry, job submission from your account has been temporarily suspended. Most likely this is due to heavy consumption of community resources from this account. For information on reactivating your account, or if you think you received this message in error, please contact " +
        EMAIL_SERVICE_ADDR;

	/*================================================================
	 * Properties
	 *================================================================*/
	// task form properties
	private String tab;

	// task summary form properties
	private String label = null;

	// task data selection form properties
	//private List<UserDataItem> inputData;
	private List<? extends FolderItem> inputData;
	//private List<UserDataItem> mappedInput;
	private List<FolderItem> mappedInput;
	private Integer unmappedInputCount = null;

	/*================================================================
	 * Action methods
	 *================================================================*/
	@SkipValidation
	public String create() {
        //logger.debug ( "MONA: entered CreateTask.create" );
		setTab(TASK_SUMMARY);
		setInput(null);
		setCurrentTask(null);
		return INPUT;
	}

	public String clone() 
	{
        //logger.debug ( "MONA: entered CreateTask.clone" );
		Task task = getRequestTask(TASK);
		if (task == null) 
		{
			reportUserError("You must select a task to make a clone of it.");
			return LIST;
		} 
		if (!getTools().contains(task.getToolId()))
		{
			reportUserError("The tool, " + task.getToolId() + ", is no longer available.");
			return LIST_ERROR;
		}
		Task clone = cloneTask(task);
		if (clone == null) 
		{
			reportUserError("There was an error cloning " + "the selected task.");
			return LIST_ERROR;
		} 
		logger.debug(getUsernameString() + "CLONE taskId = " + task.getTaskId() + ", new taskId is " + clone.getTaskId()); 
		setCurrentTask(clone);
		setTab(TASK_SUMMARY);

		return INPUT;
	}

	@SkipValidation
	public String edit() 
	{
        //logger.debug ( "MONA: entered CreateTask.edit" );
		setTab(TASK_SUMMARY);
		return INPUT;
	}

    /**
     * This is the main function that handles actions for creating new task.
     * Function will check on the incoming button type and act accordingly.
     **/
	@SkipValidation
	public String execute() 
	{
        //logger.debug ( "MONA: entered CreateTask.execute" );
        //logger.debug ( "MONA: label = " + getLabel() );
		// make sure label gets saved if user has entered one
		if (getLabel() != null && getLabel().length() > 0) 
		{
				getCurrentTask().setLabel(label);
		}

        //logger.debug ( "MONA: getParameters = " + getParameters() );
		String[] button = (String[])getParameters().get("method:execute");

		if (button != null && button.length > 0) 
		{
            //logger.debug ( "MONA: button = " + button[0] );
			if (button[0].equals(SET_DESCRIPTION)) 
			{
				String label = getLabel();
				if (label == null || label.equals(""))
				{
					addFieldError("label", "Description is required.");
				}
				else 
				{
					getCurrentTask().setLabel(label);
					addActionMessage("Description \"" + label + "\" successfully set to current task.");
				}
			}
            else if ( button[0].equals ( SELECT_DATA ) ) 
			{
				String[] selectedIds = getSelectedIds();
                //logger.debug ( "MONA: selectedIds = " + selectedIds );
				if (selectedIds == null || selectedIds.length < 1) 
				{
					addActionError("You must select one or more data items "+ "to set them as input to the current task.");
					setTab(SELECT_DATA);
				} else 
				{
					return setSelectedInputData();
				}
			}
            else if ( button[0].equals ( SAVE_TASK ) ) 
			{
				try
				{
					if (validateTask()) 
					{
						if (saveTask() != null) 
						{
							reportUserMessage("Task \"" + getLabel() + "\" successfully saved.");
							return LIST;
						} else 
						{
							reportUserError("Error saving task \"" + getLabel() + "\"");
							return INPUT;
						}
					} 
					return INPUT;
				}
                catch ( UsageLimitException ule )
                {
                    logger.error(ule.getMessage());
                    reportUserError("You have reached active jobs limit.");
                    return ERROR;
                }
				catch(Throwable error)
				{
					reportUserError(error, "Error saving task \"" + getLabel() + "\"");
					return ERROR;
				}
			} else if (button[0].equals(SAVE_AND_RUN_TASK))
			{
				try
				{
					if (validateTask())
					{
						String disabledMessage = disabledMessage(null);
						if (disabledMessage != null)
						{
							saveTask();
							addActionError("Your task has been saved but can't be run at this time. " +  disabledMessage);
							return LIST_ERROR;
						}
						try
						{
							if (saveAndRunTask() != null)
							{
								reportUserMessage("Task \"" + getLabel() + "\" successfully saved and submitted.");
								return LIST;
							} else
							{
								reportUserError("Error saving task \"" + getLabel() + "\"");
								return INPUT;
							}
						}
                        catch ( UsageLimitException ule )
                        {
                            logger.error(ule.getMessage());

                            setCurrentTask(null);
                            refreshFolderTaskTabs();
                            reportUserError(ule.getMessage());
                            return LIST_ERROR;
                        }
						catch (DisabledResourceException e)
						{
							setCurrentTask(null);
							refreshFolderTaskTabs();
							addActionError("Your task has been saved but can't be run at this time. " +  e.getMessage());
							return LIST_ERROR;
						}
					} else return INPUT;
				} catch (Throwable error)
				{
					reportUserError(error, "Error saving and submitting task \"" + getLabel() + "\"");
					return ERROR;
				}
			}
		}
		return INPUT;
	}

	String disabledMessage(Task task)
	{
        //logger.debug ( "MONA: entered CreateTask.disabledMessage" );
        //logger.debug ( "MONA: task = " + task );
		if (task == null)
		{
			task = getCurrentTask();
		}
		String toolid = task.getToolId();
		Tool tool = null;
		try
		{
			tool = Workbench.getInstance().getTool(toolid);
		}
		catch (NullPointerException ne)
		{
			return toolid + " is not in the list of available tools.";
		}
		return tool.disabled();
	}

	@SkipValidation
	/*
		This is run from taskList.jsp when there's a RUN button next to a saved task.
		Can't use tokenSession to prevent double submit because RUN is a link not a button
		on that form.  
	*/
	public String run()
	{
        //logger.debug ( "MONA: entered CreateTask.run" );
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0)
		{
			Task task = getSelectedTask(Long.parseLong(taskId[0]));
			String disabledMessage = disabledMessage(task);
			if (disabledMessage != null)
			{
				addActionError(disabledMessage);
				return LIST_ERROR;
			}
			logger.debug(getUsernameString() + "RUN taskId = " + taskId + ",task.getToolId() is " + task.getToolId());
			String msg = whyToolNotAllowed(task.getToolId());
			if (msg != null)
			{
				addActionError(msg);
				return LIST_ERROR;
			}
			try
			{
				if (!task.getUser().canSubmit())  
				{
					addActionError(CANNOT_SUBMIT);
					debug(CANNOT_SUBMIT);
					return LIST_ERROR;
				}
				try
				{
					logger.debug(getUsernameString() + "Calling submitTask for taskId  " + taskId);

					getWorkbench().submitTask(task, loggedInViaIPlant());
				}
				catch (DisabledResourceException e)
				{
					addActionError(e.getMessage());
					return LIST_ERROR;
				}
				reportUserMessage("Submitting task \"" + task.getLabel() + "\"");
				refreshFolderTaskTabs();
			} catch (Throwable error)
			{
				reportUserError(error, "Error submitting task \"" + getLabel() + "\"");
			}
			return LIST;
		} else
		{
			addActionError("You must select a task to run it.");
			return LIST;
		}
	}

	@SkipValidation
	public String cancel() {
        //logger.debug ( "MONA: entered CreateTask.cancel" );
		clearErrorsAndMessages();
		String[] button = (String[])getParameters().get("method:cancel");
		if (button != null && button.length > 0) {
			if (button[0].equals(DISCARD_TASK)) {
				setInput(null);
				setCurrentTask(null);
				addActionMessage("Task not saved.");
				return LIST;
			} else {
				addActionMessage("Task input not saved.");
			}
		}
		setTab(TASK_SUMMARY);
		return INPUT;
	}

	@SkipValidation
	public String changeTab() throws Exception {
        //logger.debug ( "MONA: entered CreateTask.changeTab" );
		String[] button = (String[])getParameters().get("label");
        //logger.debug ( "MONA: button = " + button );
		if (button != null && button.length > 0) {
			debug("button is " + button[0]);
		}
		// get selected tab from request param, if present
		String[] tab = (String[])getParameters().get(TAB);
        //logger.debug ( "MONA: tab = " + tab );
        //logger.debug ( "MONA: tab 0 = " + tab[0] );
		if (tab != null && tab.length > 0) {
			setSessionAttribute(START_NEW_TASK, "false");
			setTab(tab[0]);
			if (getTab().equals(SET_PARAMETERS)) {
				String toolAction = getToolAction();
				if (toolAction != null)
					return PARAMETERS;
			}
		} else {
			addActionError("You must select a task category to proceed with task creation.");
		}
		return INPUT;
	}

	@SkipValidation
	public String changeDataTab() throws Exception {
        //logger.debug ( "MONA: entered CreateTask.changeDataTab" );
		// get selected tab from request param, if present
		String[] tab = (String[])getParameters().get(TAB);
		if (tab != null && tab.length > 0) {
			getFolderDataTabs().setCurrentTab(tab[0]);
		}
		setTab(SELECT_DATA);
		return INPUT;
	}

	@SkipValidation
	public String changeToolTab() {
        //logger.debug ( "MONA: entered CreateTask.changeToolTab" );
		super.changeToolTab();
		setTab(SELECT_TOOL);
		return INPUT;
	}

	@SkipValidation
	public String selectTool() throws Exception {
        //logger.debug ( "MONA: entered CreateTask.selectTool" );
		Folder folder = getCurrentFolder();
		if (folder == null) {
			addActionError("You must create a folder first before you can select a tool to create a task");
			return CREATE_FOLDER;
		}
		// if no folder is currently selected, return to the tool list page
		if (getCurrentFolder() == null) {
			addActionError("You must select a folder from the left panel to start a new task.");
			return TOOLS;
		} else {

			// This attribute is set to true or false each time NgbwSupport.changeToolTab() is called.
			// It's set to false in CreateTask.changeTab().

			/*
				When user chooses the Toolkit tab, NgbwSupport.changeToolTab is called w/o a tab
				parameter and we set START_NEW_TASK = true.  If it's called with a parameter (though I'm
				not sure that ever happens) we set START_NEW_TASK = false.  When user is creating a task
				and presses Select Tool button or Tab we call CreateTask.changeTab() and in their
				I set START_NEW_TASK = false.   This is all to make sure we don't end up 
				working on an existing task when the user goes to the Toolkit tab to create a 
				new one.  Users were viewing output of one task, going to Toolkit tab, and
				they'd end up editting the task they just viewed, and trying to run it again!
			*/
			String startNewTask = (String)getSessionAttribute(START_NEW_TASK);
			if (startNewTask != null && startNewTask.equals("true"))
			{
				create();
			} 
			// get selected tool from request param, if present
			String[] tool = (String[])getParameters().get(SELECTED_TOOL);
			if (tool != null && tool.length > 0) {
				setTool(getSelectedTool(tool[0]));
				if (getTool() == null) 
				{
					addActionError("Error selecting tool \"" + tool[0] + "\": tool not found.");

				} else 
				{
					String msg = whyToolNotAllowed(getTool());
					if (msg != null)
					{
						setTool(null);
						addActionMessage(msg);
					}
					else
					{
						addActionMessage("Tool \"" + tool[0] + "\" successfully set to current task.");
						setTab(TASK_SUMMARY);
					}
				}
			} else 
			{
				addActionError("You must select a tool to set it to the current task.");
			}
			return INPUT;
		}
	}

	/*================================================================
	 * Create task form property accessor methods
	 *================================================================*/
	public String getTab() {
        //logger.debug ( "MONA: entered CreateTask.getTab" );
		return tab;
	}

	public void setTab(String tab) {
        //logger.debug ( "MONA: entered CreateTask.setTab" );
        //logger.debug ( "MONA: tab = " + tab );
		if (isValidTab(tab))
			this.tab = tab;
		else throw new RuntimeException("CreateTask.setTab() was called " +
			"with an invalid argument.");
	}

	public Task getCurrentTask() {
        //logger.debug ( "MONA: entered CreateTask.getCurrentTask" );
		Task task = super.getCurrentTask();
        //logger.debug ( "MONA: task = " + task );
		if (task == null) {

			// This creates a new Task() object
			task = getWorkbenchSession().getTaskInstance(getCurrentFolder());

			setTaskFolder(getCurrentFolder());
			setCurrentTask(task);
		}
		return task;
	}

	public void setCurrentTask(Task task) {
        //logger.debug ( "MONA: entered CreateTask.setCurrentTask" );
        //logger.debug ( "MONA: task = " + task );
		super.setCurrentTask(null);
		if (task != null) {
			// get input of task and store it properly
			Map<String, List<TaskInputSourceDocument>> inputMap = null;
			try {
				inputMap = new HashMap<String, List<TaskInputSourceDocument>>();
				for (Map.Entry<String, List<TaskInputSourceDocument>> entry : task.input().entrySet()) {
                    //logger.debug ( "MONA: entry = " + entry );
    				List<TaskInputSourceDocument> newList = new ArrayList<TaskInputSourceDocument>();

    				for (TaskInputSourceDocument doc : entry.getValue())
        				newList.add(new TaskInputSourceDocument(doc));

    				inputMap.put(entry.getKey(), newList);
				}
			} catch (Throwable error) {
				reportError(error, "Error retrieving new task's input map");
			} finally {
                //logger.debug ( "MONA: inputMap = " + inputMap );
				if (inputMap != null && inputMap.size() > 0) {
					// store entire input map in the session
					setInputMap(inputMap);
					// store "main" input separately in the session
					String tool = task.getToolId();
					if (tool != null) {
						String inputParam = getMainInputParameter(tool);
						if (inputParam != null) {
							List<TaskInputSourceDocument> input =
								inputMap.get(inputParam);
							if (input != null && input.size() > 0)
								setSessionAttribute(INPUT_DATA, input);
						}
					}
				}
			}
			setTaskFolder(getCurrentFolder());
			super.setCurrentTask(task);
		}
	}

	/*================================================================
	 * Task summary page property accessor methods
	 *================================================================*/
	public void addTaskInput(String parameter, List<TaskInputSourceDocument> input) {
        //logger.debug ( "MONA: entered CreateTask.addTaskInput" );
        //logger.debug ( "MONA: parameter = " + parameter );
        //logger.debug ( "MONA: input = " + input );
		try {
			Map<String, List<TaskInputSourceDocument>> inputMap = getInputMap();
            //logger.debug ( "MONA: inputMap = " + inputMap );
			if (parameter == null)
				throw new NullPointerException("Parameter key is null.");
			else if (input != null) {
				if (inputMap == null)
					inputMap = new HashMap<String, List<TaskInputSourceDocument>>();
				inputMap.put(parameter, input);
				setInputMap(inputMap);
			} else if (inputMap != null) {
				inputMap.remove(parameter);
				if (inputMap.size() > 0)
					setInputMap(inputMap);
				else setInputMap(null);
			}
		} catch (Throwable error) {
			reportError(error, "Error adding input for task");
		}
	}

	public String getLabel() {
        //logger.debug ( "MONA: entered CreateTask.getLabel" );
		return label;
	}

	public void setLabel(String label) {
        //logger.debug ( "MONA: entered CreateTask.setLabel" );
		this.label = label;
	}

	@SuppressWarnings("unchecked")
	public List<TaskInputSourceDocument> getInput() {
        //logger.debug ( "MONA: entered CreateTask.getInput" );
		return (List<TaskInputSourceDocument>)getSessionAttribute(INPUT_DATA);
	}

	/*
	 * Simultaneously stores the given input both in the HTTP session, and in the actual
	 * task input map (if a tool has already been selected for this task).  This parallel
	 * storage is required to keep track of the selected input even in the case of changing
	 * tool selection, since each tool stores its main input under its own input parameter.
	 */
	@SuppressWarnings("unchecked")
	//public void setInput ( List input ) {
	public void setInput(List<TaskInputSourceDocument> input) {
        //logger.debug ( "MONA: entered CreateTask.setInput" );
        //logger.debug ( "MONA: input = " + input );
		setSessionAttribute(INPUT_DATA, input);
		String tool = getTool();
        //logger.debug ( "MONA: tool = " + tool );
		if (tool != null) {
			String inputParam = getMainInputParameter(tool);
			if (inputParam != null)
				addTaskInput(inputParam, input);
		}
	}

	public boolean hasInput() {
        //logger.debug ( "MONA: entered CreateTask.hasInput" );
		return (getInput() != null);
	}

	public int getInputCount() {
        //logger.debug ( "MONA: entered CreateTask.getInputCount" );
		List<TaskInputSourceDocument> input = getInput();
        //logger.debug ( "MONA: input = " + input );
		if (input == null)
			return 0;
		else return input.size();
	}

	public void setTool(String tool) 
	{
        //logger.debug ( "MONA: entered CreateTask.setTool" );
		// if the tool changes, the parameters must be cleared, but the main input must be preserved
		Task task = getCurrentTask();
		String currentTool = getTool();
		if (currentTool == null || currentTool.equals(tool) == false) 
		{
			task.setToolId(tool);
			if (tool != null) 
			{
				// pre-populate default parameter values
				try 
				{
					ToolParameters parameterAction = (ToolParameters)Class.forName("org.ngbw.web.actions.tool." +
							tool.toLowerCase()).newInstance();
					parameterAction.setSession(getSession());
					parameterAction.reset();

					// set default parameters into task.
					setParameters(task, parameterAction.getUIParameters());
					setInputMap(parameterAction.getInputDataItems());
				} catch (Throwable error) 
				{
					reportError(error, "Error pre-populating default parameter values " + "for tool \"" + tool.toString() + "\"");
					clearParameters(task);
					setInputMap(null);
				}
			} else 
			{
				clearParameters(task);
				setInputMap(null);
			}
			// the main input must be preserved
			List<TaskInputSourceDocument> input = getInput();
			if (input != null)
			{
				setInput(input);
			}
		}
	}

	public boolean hasTool() {
        //logger.debug ( "MONA: entered CreateTask.hasTool" );
		return getTool() != null;
	}

	public boolean hasParameters() {
        //logger.debug ( "MONA: entered CreateTask.hasParameters" );
		Task task = getCurrentTask();
		if (task == null)
			return false;
		else {
			Map<String, String> parameters = null;
			try {
				parameters = task.toolParameters();
			} catch (Throwable error) {
				reportError(error, "Error determining whether or not task " +
					task.getTaskId() + " has parameters");
				return false;
			}
			if (parameters == null || parameters.size() < 1) {
				Map<String, List<TaskInputSourceDocument>> input = getInputMap();
				if (input == null || input.size() < 1)
					return false;
				else if (input.size() == 1){
					String inputParam = getMainInputParameter(task.getToolId());
					if (inputParam != null && input.containsKey(inputParam))
						return false;
					else return true;
				} else return true;
			} else return true;
		}
	}

	public Map<String, List<TaskInputSourceDocument>> getParameterInput() {
        //logger.debug ( "MONA: entered CreateTask.getParameterInput" );
		Map<String, List<TaskInputSourceDocument>> input = getInputMap();
        //logger.debug ( "MONA: input = " + input );
		if (input == null)
			return null;
		else {
			String inputParam = getMainInputParameter(getTool());
			if (inputParam != null && input.containsKey(inputParam)) {
				input = new HashMap<String, List<TaskInputSourceDocument>>(input);
				input.remove(inputParam);
			}
			return input;
		}
	}

	public int getParameterCount() {
        //logger.debug ( "MONA: entered CreateTask.getParameterCount" );
		Task task = getCurrentTask();
		if (task == null)
			return 0;
		else {
			int parameterCount = 0;
			Map<String, String> parameters = null;
			try {
				parameters = task.toolParameters();
			} catch (Throwable error) {
				reportError(error, "Error retrieving parameter count for task " +
					task.getTaskId());
			}
			if (parameters != null)
				parameterCount += parameters.size();
			Map<String, List<TaskInputSourceDocument>> parameterInput = getParameterInput();
			if (parameterInput != null)
				parameterCount += parameterInput.size();
			return parameterCount;
		}
	}

	// updated by lcc: do not validate parameters, and do not report user
	// error till validation of all fields are done
	public boolean validateTask() throws Exception
	{
        //logger.debug ( "MONA: entered CreateTask.validateTask" );
		Task task = getCurrentTask();
        //logger.debug ( "MONA: task = " + task );
		StringBuffer errors = new StringBuffer();
		if (task == null)
		{
			return false;
		}
		String label = task.getLabel();
        //logger.debug ( "MONA: label = " + label );
		if (label == null || label.trim().equals("")) 
		{
			errors.append("Please enter a description for your task. ");
		}
		String tool = task.getToolId();
        //logger.debug ( "MONA: tool = " + tool );
		if (tool == null)
		{
			errors.append("Please choose a tool for your analysis. ");
		}
		String inputParam = getMainInputParameter(tool);
        //logger.debug ( "MONA: inputParam = " + inputParam );

		Map<String, List<TaskInputSourceDocument>> inputMap = getInputMap();
        //logger.debug ( "MONA: inputMap = " + inputMap );
		if (inputParam == null || inputMap == null || inputMap.containsKey(inputParam) == false) 
		{
			errors.append("Please choose one or more data items for the task. ");
		}
		if (!task.toolParameters().keySet().contains(TOOL_GUI_OPENED))
		{
			errors.append("Please review the 'Input Parameters'. ");
		} 
		if (errors.length() <= 0) 
		{
			return true;
		}
		reportUserError(errors.toString());
		return false;
	}

	/*================================================================
	 * Task input data selection page property accessor methods
	 *================================================================*/
	//TODO: filter available input data by tool selection, this will require
	//a more complex treatment of tool mode
	//public List<UserDataItem> getInputData() {
	public List<? extends FolderItem> getInputData() {
        //logger.debug ( "MONA: entered CreateTask.getInputData" );
        //logger.debug ( "MONA: inputData 1 = " + inputData );
		// get the input data list stored in the action
		if (inputData != null) {
			return inputData;
		}
		// if not found, construct the proper input data list
		else try {
			String tool = getTool();
            //logger.debug ( "MONA: tool = " + tool );
            //logger.debug ( "MONA: current folder = " + getCurrentFolder() );
			if (tool != null) {
				String input = getMainInputParameter(tool);
                //logger.debug ( "MONA: input = " + input );
				if (input == null)
                {
					//inputData = getCurrentFolder().findDataItems();
					inputData = getCurrentFolder().findDataAllItems();
                }
				else
                {
                    //inputData = getCurrentFolder().findDataItems();
                    inputData = getCurrentFolder().findDataAllItems();
                }
			} else inputData = getCurrentFolder().findDataAllItems();
            //logger.debug ( "MONA: inputData 2 = " + inputData );
			return inputData;
		} catch (Throwable error) {
			reportError(error, "Error retrieving data for current folder");
			return null;
		}
	}

	public boolean hasInputData() {
        //logger.debug ( "MONA: entered CreateTask.hasInputData" );
		List<? extends FolderItem> inputData = getInputData();
        //logger.debug ( "MONA: inputData = " + inputData );
		return (inputData != null && inputData.size() > 0);
	}

	//TODO: add the proper interface and get rid of this method!
	//public List<UserDataItem> getMappedInput() {
	public List<? extends FolderItem> getMappedInput() {
        //logger.debug ( "MONA: entered CreateTask.getMappedInput" );
        //logger.debug ( "MONA: mappedInput = " + mappedInput );
		// get the mapped input list stored in the action
		if (mappedInput != null) {
			return mappedInput;
		}
		// if not found, construct the proper mapped input list
		else {
			List<TaskInputSourceDocument> input = getInput();
            //logger.debug ( "MONA: input = " + input );
			unmappedInputCount = 0;
			if (input == null) {
				mappedInput = null;
			} else {
                //logger.debug ( "MONA: input.size = " + input.size() );
				WorkbenchSession session = getWorkbenchSession();
				mappedInput = new Vector<FolderItem> ( input.size() );
				for (TaskInputSourceDocument inputDocument : input) {
                    //logger.debug ( "MONA: inputDocument = " + inputDocument );
                    //logger.debug ( "MONA: inputDocument class name = " + inputDocument.getClass().getName() );
					try {
						Long id = Long.parseLong(inputDocument.getName());
                        //logger.debug ( "MONA: id = " + id );
                        DataType type = inputDocument.getDataType();
                        //logger.debug ( "MONA: type = " + type );
						FolderItem dataItem = null;
                        if ( type == DataType.DIRECTORY )
						    dataItem = session.findUserDataDirItem ( id );
                        else
						    dataItem = session.findUserDataItem ( id );
                        //logger.debug ( "MONA: dataItem = " + dataItem );

						if (dataItem != null)
							mappedInput.add(dataItem);
						else unmappedInputCount++;
					} catch (Throwable error) {
						unmappedInputCount++;
						continue;
					}
                    //logger.debug ( "MONA: mappedInput = " + mappedInput );
				}
				if (mappedInput.size() < 1)
					mappedInput = null;
                /* disabling sorting for now since we might have a mixture
                 * of UserDataItem and UserDataDirItem
				else getWorkbenchSession().sortUserDataItems(mappedInput,
					UserDataItemSortableField.ID, false);
                */
			}
			return mappedInput;
		}
	}

	public boolean hasMappedInput() {
        //logger.debug ( "MONA: entered CreateTask.hasMappedInput" );
		return (getMappedInput() != null);
	}

	//TODO: add the proper interface and get rid of this method!
	public int getUnmappedInputCount() {
        //logger.debug ( "MONA: entered CreateTask.getUnmappedInputCount" );
		// get the unmapped input count stored in the action
		if (unmappedInputCount != null) {
			return unmappedInputCount;
		}
		// if not found, construct the proper unmapped input count
		else {
			List<TaskInputSourceDocument> input = getInput();
			unmappedInputCount = 0;
			if (input == null) {
				mappedInput = null;
			} else {
				WorkbenchSession session = getWorkbenchSession();
				//mappedInput = new Vector<UserDataItem>(input.size());
				mappedInput = new Vector<FolderItem>(input.size());
				for (TaskInputSourceDocument inputDocument : input) {
					try {
						Long id = Long.parseLong(inputDocument.getName());
						//UserDataItem dataItem = session.findUserDataItem(id);
                        DataType type = inputDocument.getDataType();
                        //logger.debug ( "MONA: type = " + type );
						FolderItem dataItem = null;
                        if ( type == DataType.DIRECTORY )
						    dataItem = session.findUserDataDirItem ( id );
						if (dataItem != null)
							mappedInput.add(dataItem);
						else unmappedInputCount++;
					} catch (Throwable error) {
						unmappedInputCount++;
						continue;
					}
				}
				if (mappedInput.size() < 1)
					mappedInput = null;
                /* disabling sorting for now since we might have a mixture
                 * of UserDataItem and UserDataDirItem
				else getWorkbenchSession().sortUserDataItems(mappedInput,
					UserDataItemSortableField.ID, false);
                */
			}
			return unmappedInputCount;
		}
	}

	public boolean hasUnmappedInput() {
        //logger.debug ( "MONA: entered CreateTask.hasUnmappedInput" );
		return (getUnmappedInputCount() > 0);
	}

	/*================================================================
	 * Task tool selection page property accessor methods
	 *================================================================*/
	public boolean isCurrentTool(String tool) {
        //logger.debug ( "MONA: entered CreateTask.isCurrentTool" );
		String currentTool = getTool();
		if (tool == null || currentTool == null)
			return false;
		else return tool.equals(currentTool);
	}

	/*================================================================
	 * Internal property accessor methods
	 *================================================================*/
	protected Task getRequestTask(String parameter) {
        //logger.debug ( "MONA: entered CreateTask.getRequestTask" );
		String taskId = getRequestParameter(parameter);
		if (taskId == null)
			return null;
		else try {
			return getSelectedTask(Long.parseLong(taskId));
		} catch (NumberFormatException error) {
			return null;
		}
	}

	protected Task cloneTask(Task task) 
	{
        //logger.debug ( "MONA: entered CreateTask.cloneTask" );
		if (task == null)
		{
			return null;
		}
		try 
		{
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
			{
				throw new NullPointerException("No session is present.");
			} else 
			{
				Task newtask = session.cloneTask(task);
				if (newtask != null)
				{
					// Say that tool gui was opened, since in a clone the parameters went thru the gui's javascript once already.
					// If user changes the tool, the tool parameters will all be cleared.
					logger.debug("Adding TOOL_GUI_OPENED");
					newtask.toolParameters().put(TOOL_GUI_OPENED, "1");
				}
				return newtask;
			}
		} catch (Throwable error) 
		{
			reportError(error, "Error cloning selected task");
			return null;
		}
	}

	/* Set parameters into task */
	protected void setParameters(Task task, Map<String, String> parameters)  throws Exception
	{
        //logger.debug ( "MONA: entered CreateTask.setParameters" );
		if (task == null)
		{
			return;
		}
		clearParameters(task);
		if (parameters != null && parameters.size() > 0) 
		try 
		{
			task.toolParameters().putAll(parameters);
		} catch (Throwable error) 
		{
			debug("Error setting parameter map of size " + parameters.size() + " to task " + task.getTaskId());
			error("", error);
			throw error;
		}
	}

	protected void clearParameters(Task task) 
	{
        //logger.debug ( "MONA: entered CreateTask.clearParameters" );
		if (task == null)
		{
			return;
		}
		try 
		{
			task.toolParameters().clear();
		} catch (Throwable error) 
		{
			debug("Error clearing parameter map for task " + task.getTaskId());
		}
	}

	protected void setInput(Task task, Map<String, List<TaskInputSourceDocument>> input) {
        //logger.debug ( "MONA: entered CreateTask.setInput" );
		if (task == null)
			return;
		else {
			clearInput(task);
			if (input != null && input.size() > 0) try {
				task.input().putAll(input);
			} catch (Throwable error) {
				debug("Error setting input map of size " + input.size() +
					" to task " + task.getTaskId());
			}
		}
	}

	protected void clearInput(Task task) {
        //logger.debug ( "MONA: entered CreateTask.clearInput" );
		if (task == null)
			return;
		else try {
			task.input().clear();
		} catch (Throwable error) {
			debug("Error clearing input map for task " + task.getTaskId());
		}
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/
	protected ParameterValidator getParameterValidator(String tool) {
        //logger.debug ( "MONA: entered CreateTask.getParameterValidator" );
		try {
			if (tool == null)
				throw new NullPointerException("Selected tool is null.");
			else return (ParameterValidator)Class.forName("org.ngbw.web.model.impl.tool." +
				tool.toLowerCase() + "Validator").newInstance();
		} catch (Throwable error) {
			reportError(error, "Error instantiating validator class for tool");
			return null;
		}
	}

	/*
	protected boolean validateParameters() {
		//TODO: update ParameterValidator to accommodate a global view of parameter validation
		try {
			Task task = getCurrentTask();
			;
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else {
				String tool = task.getToolId();
				if (tool == null)
					return false;
				ParameterValidator validator = getParameterValidator(tool);
				Map<String, String> errors = validator.validateParameters(task.toolParameters());
				if (errors != null && errors.size() > 0)
					return false;
				errors = validator.validateInput(getInputMap());
				if (errors != null && errors.size() > 0)
					return false;
				return true;
			}
		} catch (Throwable error) {
			reportError(error, "Error validating current task's parameters");
			return false;
		}
	}
	*/

	private Task saveTask() 
	{
        //logger.debug ( "MONA: entered CreateTask.saveTask()" );
		try 
		{
			WorkbenchSession session = getWorkbenchSession();
            //logger.debug ( "MONA: session = " + session );
			Folder folder = getCurrentFolder();
            //logger.debug ( "MONA: folder = " + folder );
			Task task = getCurrentTask();
            //logger.debug ( "MONA: task = " + task );
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (folder == null)
				throw new NullPointerException("No folder is currently selected.");
			else if (task == null)
				throw new NullPointerException("No task is currently selected.");
            
			setInput(task, getInputMap());
			task.setStage(TaskRunStage.READY);

			// Here's where task actually gets saved.
			if (task.toolParameters().keySet().contains(TOOL_GUI_OPENED))
			{
				logger.debug("removing TOOL_GUI_OPENED");
				task.toolParameters().remove(TOOL_GUI_OPENED);
			}
			task = session.saveTask(task, folder);

			logger.debug(getUsernameString() + "SAVE TASK taskId = " + task.getTaskId() + ", task.getToolId() is " + task.getToolId()); 
			setCurrentTask(null);
			refreshFolderTaskTabs();
			return task;
		} catch (Throwable error) 
		{
			reportError(error, "Error saving current task");
			return null;
		}
	}

	private Task saveAndRunTask() throws DisabledResourceException, UsageLimitException
	{
        //logger.debug ( "MONA: entered CreateTask.saveAndRunTask()" );
		try 
		{
			Workbench workbench = getWorkbench();
			Folder folder = getCurrentFolder();
			Task task = getCurrentTask();
			if (workbench == null)
				throw new NullPointerException("No workbench is present.");
			else if (folder == null)
				throw new NullPointerException("No folder is currently selected.");
			else if (task == null)
				throw new NullPointerException("No task is currently selected.");
			setInput(task, getInputMap());
			String msg = whyToolNotAllowed(task.getToolId());
			if (msg != null)
			{
				addActionError(msg);
				return null;
			}
			if (!task.getUser().canSubmit())  
			{
				addActionError(CANNOT_SUBMIT);
				debug(CANNOT_SUBMIT);
				return null;
			}
			task.setStage(TaskRunStage.READY);
			logger.debug(getUsernameString() + "Calling saveAndsubmitTask" );

			// Here's where it actually gets saved.
			if (task.toolParameters().keySet().contains(TOOL_GUI_OPENED))
			{
				logger.debug("removing TOOL_GUI_OPENED");
				task.toolParameters().remove(TOOL_GUI_OPENED);
			}

			User user = super.getWorkbenchSession().getUser();

			String toolId = super.getTool();
			// Verify active jobs count.
			UsageLimit.getInstance().verifyActiveJobCount(user, null);
          
			// Verify SU usages.
			UsageLimit.getInstance().verifySULimit(user, null, toolId, loggedInViaIPlant());
            
			// ***** User has not reached the limit, continue with the process. //
			workbench.saveAndSubmitTask(task, folder, loggedInViaIPlant());

			logger.debug(getUsernameString() + "SAVE AND RUN taskId = " + task.getTaskId() + ", task.getToolId() is " + task.getToolId()); 

			setCurrentTask(null);
			refreshFolderTaskTabs();
			return task;
		} 
		catch( UsageLimitException | DisabledResourceException e)
		{
			throw e;
		}
		catch (Throwable error) {
			reportError(error, "Error saving and running current task");
			return null;
		}
	}

	protected boolean isValidTab(String tab) {
        //logger.debug ( "MONA: entered CreateTask.isValidTab()" );
		if (tab == null) return false;
		else if (tab.equals(TASK_SUMMARY) ||
			tab.equals(SELECT_DATA) ||
			tab.equals(SELECT_TOOL) ||
			tab.equals(SET_PARAMETERS))
			return true;
		else return false;
	}

	protected String getSelectedTool(String tool) {
        //logger.debug ( "MONA: entered CreateTask.getSelectedTool()" );
		Collection<String> tools = getTools();
		if (tool == null || tools == null || tools.size() < 1)
			return null;
		else for (String currentTool : tools) {
			if (currentTool.equalsIgnoreCase(tool))
				return currentTool;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected String setSelectedInputData() {
        //logger.debug ( "MONA: entered CreateTask.setSelectedInputData" );
		String[] selectedIds = getSelectedIds();
        //logger.debug ( "MONA: selectedIds = " + selectedIds );
		List<TaskInputSourceDocument> input =
			new Vector<TaskInputSourceDocument>(selectedIds.length);
        //logger.debug ( "MONA: selectedIds.length = " + selectedIds.length );
		if (selectedIds.length == 1) {
            String[] parts = selectedIds[0].split ( "-" );
            String className = parts[0].trim();
            //logger.debug ( "MONA: className = " + className );
            Long id = Long.parseLong ( parts[1].trim() );
            //logger.debug ( "MONA: id = " + id );
                                               
            if ( className.equals ( "UserDataItem" ) )
            {
			    UserDataItem dataItem = getSelectedData ( id );
			    if ( dataItem == null )
                {
				    reportUserError ( "Error selecting data item with ID " +
					    id + ": item not found in current folder." );
				    setTab ( SELECT_DATA );
			    }
                else
                {
				    SourceDocument sourceDocument =
					    getWorkbenchSession().getSourceDocument ( dataItem );
				    TaskInputSourceDocument inputDocument = null;
				    try
                    {
					    inputDocument = new TaskInputSourceDocument
                            ( sourceDocument );
				    }
                    catch ( Throwable error )
                    {
					    reportError ( error,
                            "Error creating new TaskInputSourceDocument" );
					    addActionError (
                            "There was an error adding your selected " +
						    "input data to your task." );
					    return INPUT;
				    }
				    inputDocument.setName ( Long.toString (
                        dataItem.getUserDataId() ) );
				    input.add ( inputDocument );
				    setInput ( input );
				    reportUserMessage ( "Data item \"" +
                        truncateText ( dataItem.getLabel() ) +
					    "\" successfully set as input to current task." );
			    }
            }
            else if ( className.equals ( "UserDataDirItem" ) )
            {
			    UserDataDirItem dataItem = getSelectedDirData ( id );
                //logger.debug ( "MONA: dataItem = " + dataItem );
			    if ( dataItem == null )
                {
				    reportUserError ( "Error selecting data item with ID " +
					    id + ": item not found in current folder." );
				    setTab ( SELECT_DATA );
			    }
                else
                {
                    SourceDocument sourceDocument =
                        ( SourceDocument ) dataItem;
                    //logger.debug ( "MONA: sourceDocument = " + sourceDocument );
				    TaskInputSourceDocument inputDocument = null;
				    try
                    {
					    inputDocument = new TaskInputSourceDocument
                            ( sourceDocument );
                        //logger.debug ( "MONA: inputDocument = " + inputDocument );
				    }
                    catch ( Throwable error )
                    {
					    reportError ( error,
                            "Error creating new TaskInputSourceDocument" );
					    addActionError (
                            "There was an error adding your selected " +
						    "input data to your task." );
					    return INPUT;
				    }

				    inputDocument.setName ( Long.toString (
                        dataItem.getUserDataId() ) );
				    input.add ( inputDocument );
				    setInput ( input );
				    reportUserMessage ( "Data item \"" +
                        truncateText ( dataItem.getLabel() ) +
					    "\" successfully set as input to current task." );
			    }
            }
		} else {
			for (int i=0; i<selectedIds.length; i++) {
                /* Original
				UserDataItem dataItem = getSelectedData(selectedIds[i]);
				if (dataItem == null) {
					reportUserError("Error selecting data item with ID " +
						selectedIds[i] + ": item not found in current folder.");
					setTab(SELECT_DATA);
					return INPUT;
				} else {
					SourceDocument sourceDocument =
						getWorkbenchSession().getSourceDocument(dataItem);
					TaskInputSourceDocument inputDocument = null;
					try {
						inputDocument = new TaskInputSourceDocument(sourceDocument);
					} catch (Throwable error) {
						reportError(error, "Error creating new TaskInputSourceDocument");
						addActionError("There was an error adding your selected " +
							"input data to your task.");
						return INPUT;
					}
					inputDocument.setName(Long.toString(dataItem.getUserDataId()));
					input.add(inputDocument);
				}
                */

                String[] parts = selectedIds[0].split ( "-" );
                String className = parts[0].trim();
                Long id = Long.parseLong ( parts[1].trim() );
                                               
                if ( className.equals ( "UserDataItem" ) )
                {
				    UserDataItem dataItem = getSelectedData ( id );
				    if ( dataItem == null )
                    {
					    reportUserError (
                            "Error selecting data item with ID " + id +
                            ": item not found in current folder." );
					    setTab ( SELECT_DATA );
					    return INPUT;
				    }
                    else
                    {
					    SourceDocument sourceDocument =
						    getWorkbenchSession().getSourceDocument
                            ( dataItem );
					    TaskInputSourceDocument inputDocument = null;
					    try
                        {
						    inputDocument = new TaskInputSourceDocument
                                ( sourceDocument );
					    }
                        catch ( Throwable error )
                        {
						    reportError ( error,
                                "Error creating new TaskInputSourceDocument" );
						    addActionError (
                                "There was an error adding your selected " +
							    "input data to your task." );
						    return INPUT;
					    }
					    inputDocument.setName ( Long.toString (
                            dataItem.getUserDataId() ) );
					    input.add ( inputDocument );
				    }
                }
                else if ( className.equals ( "UserDataDirItem" ) )
                {
				    UserDataDirItem dataItem = getSelectedDirData ( id );
				    if ( dataItem == null )
                    {
					    reportUserError (
                            "Error selecting data item with ID " + id +
                            ": item not found in current folder." );
					    setTab ( SELECT_DATA );
					    return INPUT;
				    }
                    else
                    {
                        /*
					    SourceDocument sourceDocument =
						    getWorkbenchSession().getSourceDocument
                            ( dataItem );
                        */
                        SourceDocument sourceDocument =
                            ( SourceDocument ) dataItem;
					    TaskInputSourceDocument inputDocument = null;
					    try
                        {
						    inputDocument = new TaskInputSourceDocument
                                ( sourceDocument );
					    }
                        catch ( Throwable error )
                        {
						    reportError ( error,
                                "Error creating new TaskInputSourceDocument" );
						    addActionError (
                                "There was an error adding your selected " +
							    "input data to your task." );
						    return INPUT;
					    }
					    inputDocument.setName ( Long.toString (
                            dataItem.getUserDataId() ) );
					    input.add ( inputDocument );
				    }
                }
			}

			setInput(input);
			String message = selectedIds.length + " data item";
			if (selectedIds.length != 1)
				message += "s";
			message += " successfully set as input to current task.";
			reportUserMessage(message);
		}
		return INPUT;
	}

	protected void dumpToolParameters(Task task) {
        //logger.debug ( "MONA: entered CreateTask.dumpToolParameters()" );
		if (task == null)
			return;
		int i = 1;
		// primitive parameters
		debug("Examining parameters for task \"" + task.getLabel() +
			"\", running tool \"" + task.getToolId() + "\":");
		Map<String, String> parameters = null;
		try {
			parameters = task.toolParameters();
		} catch (Throwable error) {
			reportError(error, "Error retrieving parameters for task " +
				task.getTaskId());
		}
		if (parameters == null || parameters.keySet().size() < 1)
			debug("  No basic parameters.");
		else {
			debug("  Basic parameters:");
			for (String param : parameters.keySet()) {
				if (parameters.get(param) == null)
					debug("    Parameter " + i + " = \"" + param +
						"\" : exists, but has a null value.");
				else debug("    Parameter " + i + " = \"" + param +
					"\" : \"" + parameters.get(param) + "\"");
				i++;
			}
		}
		// file-typed parameters
		Map<String, List<TaskInputSourceDocument>> inputDataItems = null;
		try {
			inputDataItems = task.input();
		} catch (Throwable error) {
			reportError(error, "Error retrieving input for task " +
				task.getTaskId());
		}
		if (inputDataItems == null || inputDataItems.keySet().size() < 1)
			debug("  No input data items.");
		else {
			debug("  Input data items:");
			for (String param : inputDataItems.keySet()) {
				if (inputDataItems.get(param) == null)
					debug("    Parameter " + i + " = \"" + param + "\" : exists, but has a null value.");
				else {
					List<TaskInputSourceDocument> input = inputDataItems.get(param);
					debug("    Parameter " + i + " = \"" + param + "\" : " + input.size() + " items.");
					for (int j=0; j<input.size(); j++) 
					{
						TaskInputSourceDocument inputDocument = input.get(j);
						String message = "      Input document " + j;
						if (inputDocument == null)
							debug(message + " exists, but has a null value.");
						else {
							message += " = TaskInputSourceDocument with name \"" +
								inputDocument.getName() + "\", type \"" +
								inputDocument.getType() + "\"";
							try 
							{
								//message += " and with data of size " + inputDocument.getData().length + " bytes.";
								message += " and with data of size " + inputDocument.getDataLength() + " bytes.";

							} catch (Throwable error) {
								reportError(error, "Error retrieving source data for " + "input document " + j);
								message += " and data of unknown size.";
							}
							debug(message);
						}
					}
				}
				i++;
			}
		}
	}

	protected void dumpToolParameters() {
        //logger.debug ( "MONA: entered CreateTask.dumpToolParameters(2)" );
		dumpToolParameters(getCurrentTask());
	}
}
