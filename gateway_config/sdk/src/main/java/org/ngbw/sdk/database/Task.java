/*
 * Task.java
 */
package org.ngbw.sdk.database;


import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.core.shared.TaskRunStage;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.tool.Tool;


/**
 *
 * @author Paul Hoover
 *
 */
public class Task extends FolderItem implements Comparable<Task> {

	// nested classes


	/**
	 *
	 */
	private class PropertyMap extends MonitoredMap<String, String> {

		// constructors


		protected PropertyMap(Map<String, String> propMap)
		{
			super(propMap);
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, String value)
		{
			Column<String> propName = new StringColumn("PROPERTY", false, 100, key);
			Column<String> propValue = new StringColumn("VALUE", true, 4095, value);
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(propName);
			cols.add(propValue);

			m_opQueue.add(new InsertOp("task_properties", cols));
		}

		@Override
		protected void addMapSetOp(String key, String oldValue, String newValue)
		{
			Column<String> propName = new StringColumn("PROPERTY", false, 100, key);
			Column<String> propValue = new StringColumn("VALUE", true, 4095, newValue);
			CompositeKey propKey = new CompositeKey(m_key, propName);

			m_opQueue.add(new UpdateOp("task_properties", propKey, propValue));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			Column<String> propName = new StringColumn("PROPERTY", false, 100, key);
			CompositeKey propKey = new CompositeKey(m_key, propName);

			m_opQueue.add(new DeleteOp("task_properties", propKey));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new DeleteOp("task_properties", getKey()));
		}
	}

	/**
	 *
	 */
	private class ParameterMap extends MonitoredMap<String, String> {

		// constructors


		protected ParameterMap(Map<String, String> paramMap)
		{
			super(paramMap);
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, String value)
		{
			Column<String> paramName = new StringColumn("PARAMETER", false, 100, key);
			Column<String> paramValue = new StringColumn("VALUE", true, 1024, value);
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(paramName);
			cols.add(paramValue);

			m_opQueue.add(new InsertOp("tool_parameters", cols));
		}

		@Override
		protected void addMapSetOp(String key, String oldValue, String newValue)
		{
			Column<String> paramName = new StringColumn("PARAMETER", false, 100, key);
			Column<String> paramValue = new StringColumn("VALUE", true, 1024, newValue);
			CompositeKey paramKey = new CompositeKey(m_key, paramName);

			m_opQueue.add(new UpdateOp("tool_parameters", paramKey, paramValue));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			Column<String> paramName = new StringColumn("PARAMETER", false, 100, key);
			CompositeKey paramKey = new CompositeKey(m_key, paramName);

			m_opQueue.add(new DeleteOp("tool_parameters", paramKey));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new DeleteOp("tool_parameters", getKey()));
		}
	}

	/**
	 *
	 */
	private class AddInputOp implements RowOperation {

		// data fields


		private final String m_parameter;
		private final List<TaskInputSourceDocument> m_value;


		// constructors


		protected AddInputOp(String parameter, List<TaskInputSourceDocument> value)
		{
			if (parameter == null)
				throw new NullPointerException("parameter");

			m_parameter = parameter;
			m_value = value;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
            //log.debug ( "MONA: entered Task.execute()" );
            //log.debug ( "MONA: m_key value = " + m_key.getValue() );
            //log.debug ( "MONA: m_parameter = " + m_parameter );
			TaskInputParameter inputParam = new TaskInputParameter(m_key.getValue(), m_parameter);
            //log.debug ( "MONA: inputParam = " + inputParam );

			inputParam.save(dbConn);

			long inputId = inputParam.getInputId();

			for (TaskInputSourceDocument doc : m_value) {
				doc.setInputId(inputId);
				doc.save(dbConn);
			}
		}
	}

	/**
	 *
	 */
	private class RemoveInputOp implements RowOperation {

		private final String m_parameter;


		// constructors


		protected RemoveInputOp(String parameter)
		{
			if (parameter == null)
				throw new NullPointerException("parameter");

			m_parameter = parameter;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws SQLException, IOException
		{
			PreparedStatement selectStmt = dbConn.prepareStatement("SELECT INPUT_ID FROM task_input_parameters WHERE PARAMETER = ? AND TASK_ID = ?");
			ResultSet inputRow = null;

			try {
				selectStmt.setString(1, m_parameter);
				m_key.setParameter(selectStmt, 2);

				inputRow = selectStmt.executeQuery();

				while (inputRow.next())
					TaskInputParameter.delete(dbConn, inputRow.getLong(1));
			}
			finally {
				if (inputRow != null)
					inputRow.close();

				selectStmt.close();
			}
		}
	}

	/**
	 *
	 */
	private class RemoveAllInputOp implements RowOperation {

		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			deleteInputParams(dbConn, getKey());
		}
	}

	/**
	 *
	 */
	private class InputMap extends MonitoredMap<String, List<TaskInputSourceDocument>> {

		// constructors


		protected InputMap(Map<String, List<TaskInputSourceDocument>> inputMap)
		{
			super(inputMap);
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, List<TaskInputSourceDocument> value)
		{
			m_opQueue.add(new AddInputOp(key, value));
		}

		@Override
		protected void addMapSetOp(String key, List<TaskInputSourceDocument> oldValue, List<TaskInputSourceDocument> newValue)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			m_opQueue.add(new RemoveInputOp(key));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new RemoveAllInputOp());
		}
	}

	/**
	 *
	 */
	private class AddOutputOp implements RowOperation {

		private final boolean m_intermediate;
		private final String m_parameter;
		private final List<TaskOutputSourceDocument> m_value;


		// constructors


		protected AddOutputOp(String parameter, List<TaskOutputSourceDocument> value, boolean intermediate)
		{
			if (parameter == null)
				throw new NullPointerException("parameter");

			m_intermediate = intermediate;
			m_parameter = parameter;
			m_value = value;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			TaskOutputParameter outputParam = new TaskOutputParameter(m_key.getValue(), m_parameter, m_intermediate);

			outputParam.save(dbConn);

			long outputId = outputParam.getOutputId();

			for (TaskOutputSourceDocument doc : m_value) {
				doc.setOutputId(outputId);
				doc.save(dbConn);
			}
		}
	}

	/**
	 *
	 */
	private class RemoveOutputOp implements RowOperation {

		private final boolean m_intermediate;
		private final String m_parameter;


		// constructors


		protected RemoveOutputOp(String parameter, boolean intermediate)
		{
			if (parameter == null)
				throw new NullPointerException("parameter");

			m_intermediate = intermediate;
			m_parameter = parameter;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws SQLException, IOException
		{
			PreparedStatement selectStmt = dbConn.prepareStatement("SELECT OUTPUT_ID FROM task_output_parameters WHERE TASK_ID = ? AND PARAMETER = ? AND INTERMEDIATE = ?");
			ResultSet outputRow = null;

			try {
				m_key.setParameter(selectStmt, 1);
				selectStmt.setString(2, m_parameter);
				selectStmt.setBoolean(3, m_intermediate);

				outputRow = selectStmt.executeQuery();

				while (outputRow.next())
					TaskOutputParameter.delete(dbConn, outputRow.getLong(1));
			}
			finally {
				if (outputRow != null)
					outputRow.close();

				selectStmt.close();
			}
		}
	}

	/**
	 *
	 */
	private class RemoveAllOutputOp implements RowOperation {

		private final boolean m_intermediate;


		// constructors


		public RemoveAllOutputOp(boolean intermediate)
		{
			m_intermediate = intermediate;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws SQLException, IOException
		{
			PreparedStatement selectStmt = dbConn.prepareStatement("SELECT OUTPUT_ID FROM task_output_parameters WHERE TASK_ID = ? AND INTERMEDIATE = ?");
			ResultSet paramRows = null;

			try {
				m_key.setParameter(selectStmt, 1);
				selectStmt.setBoolean(2, m_intermediate);

				paramRows = selectStmt.executeQuery();

				while (paramRows.next())
					TaskOutputParameter.delete(dbConn, paramRows.getLong(1));
			}
			finally {
				if (paramRows != null)
					paramRows.close();

				selectStmt.close();
			}
		}
	}

	/**
	 *
	 */
	private class OutputMap extends MonitoredMap<String, List<TaskOutputSourceDocument>> {

		private final boolean m_intermediate;


		// constructors


		protected OutputMap(Map<String, List<TaskOutputSourceDocument>> outputMap, boolean intermediate)
		{
			super(outputMap);

			m_intermediate = intermediate;
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, List<TaskOutputSourceDocument> value)
		{
			m_opQueue.add(new AddOutputOp(key, value, m_intermediate));
		}

		@Override
		protected void addMapSetOp(String key, List<TaskOutputSourceDocument> oldValue, List<TaskOutputSourceDocument> newValue)
		{
			m_opQueue.add(new RemoveOutputOp(key, m_intermediate));
			m_opQueue.add(new AddOutputOp(key, newValue, m_intermediate));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			m_opQueue.add(new RemoveOutputOp(key, m_intermediate));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new RemoveAllOutputOp(m_intermediate));
		}
	}

	/**
	 *
	 */
	private class AddLogMessageOp implements RowOperation {

		private final TaskLogMessage m_message;


		// constructors


		protected AddLogMessageOp(TaskLogMessage message)
		{
			m_message = message;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			m_message.setTaskId(getTaskId());
			m_message.setJobHandle(getJobHandle());

			m_message.save(dbConn);
		}
	}

	/**
	 *
	 */
	private class RemoveLogMessageOp implements RowOperation {

		private final TaskLogMessage m_message;


		// constructors


		protected RemoveLogMessageOp(TaskLogMessage message)
		{
			m_message = message;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			m_message.delete(dbConn);
		}
	}

	/**
	 *
	 */
	private class LogMessageList extends MonitoredList<TaskLogMessage> {

		// constructors


		protected LogMessageList(List<TaskLogMessage> records)
		{
			super(records);
		}


		// protected methods


		@Override
		protected void addListAddOp(TaskLogMessage message)
		{
			m_opQueue.add(new AddLogMessageOp(message));
		}

		@Override
		protected void addListSetOp(TaskLogMessage oldMessage, TaskLogMessage newMessage)
		{
			m_opQueue.add(new RemoveLogMessageOp(oldMessage));
			m_opQueue.add(new AddLogMessageOp(newMessage));
		}

		@Override
		protected void addListRemoveOp(TaskLogMessage message)
		{
			m_opQueue.add(new RemoveLogMessageOp(message));
		}

		@Override
		protected MonitoredList<TaskLogMessage> newListInstance(List<TaskLogMessage> list)
		{
			return new LogMessageList(list);
		}
	}


	// data fields

    private static final Log log = LogFactory.getLog(Task.class.getName());

	private static final String TABLE_NAME = "tasks";
	private static final String KEY_NAME = "TASK_ID";
	private final Column<String> m_comment = new StringColumn("COMMENT", true, 255);
	private final Column<String> m_jobHandle = new StringColumn("JOBHANDLE", true, 255);
	private final Column<Boolean> m_ok = new BooleanColumn("OK", false);
	private final Column<String> m_stage = new StringColumn("STAGE", false, 20);
	private final Column<String> m_toolId = new StringColumn("TOOL_ID", true, 100);
	private final Column<Boolean> m_isTerminal = new BooleanColumn("IS_TERMINAL", false);
	private final Column<String> m_appname = new StringColumn("APPNAME", false, 30);
	private PropertyMap m_properties;
	private ParameterMap m_toolParameters;
	private InputMap m_taskInput;
	private OutputMap m_taskOutput;
	private OutputMap m_intermediateTaskOutput;
	private LogMessageList m_logMessages;


	// constructors


	public Task(Folder enclosingFolder)
	{
		this();

		if (enclosingFolder.isNew())
			throw new WorkbenchException("Can't create a task in an unpersisted folder");

		setOk(true);
		setTerminal(false);
		setStage(TaskRunStage.NEW);
		setUserId(enclosingFolder.getUserId());
		setGroupId(enclosingFolder.getGroupId());
		setEnclosingFolderId(enclosingFolder.getFolderId());
		setAppname("");
		setCreationDate(Calendar.getInstance().getTime());
	}

	public Task(long taskId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(taskId);

		load();
	}

	public Task(Task otherTask) throws IOException, SQLException
	{
		this();
        //log.debug ( "MONA: entered Task 1" );

		setComment(otherTask.getComment());
		setOk(true);
		setTerminal(false);
		setStage(TaskRunStage.NEW);
		setToolId(otherTask.getToolId());
		setUserId(otherTask.getUserId());
		setGroupId(otherTask.getGroupId());
		setLabel(otherTask.getLabel());
        //log.debug ( "MONA: label = " + otherTask.getLabel() );
		setEnclosingFolderId(otherTask.getEnclosingFolderId());
		setAppname(otherTask.getAppname());
		setCreationDate(Calendar.getInstance().getTime());

		m_properties = new PropertyMap(new TreeMap<String, String>());

		m_properties.putAll(otherTask.properties());

		m_toolParameters = new ParameterMap(new TreeMap<String, String>());

		m_toolParameters.putAll(otherTask.toolParameters());

		m_taskInput = new InputMap(new TreeMap<String, List<TaskInputSourceDocument>>());

		for (Iterator<Map.Entry<String, List<TaskInputSourceDocument>>> entries = otherTask.input().entrySet().iterator() ; entries.hasNext() ; ) {
			Map.Entry<String, List<TaskInputSourceDocument>> entry = entries.next();
            //log.debug ( "MONA: entry = " + entry );
			List<TaskInputSourceDocument> inputDocs =
                new ArrayList<TaskInputSourceDocument>();

			for ( Iterator<TaskInputSourceDocument> docs =
                entry.getValue().iterator() ; docs.hasNext() ; )
            {
				inputDocs.add ( new TaskInputSourceDocument ( docs.next() ) );

                // Following all for debugging...
                //log.debug ( "MONA: docs = " + docs );
                //TaskInputSourceDocument doc = docs.next();
                //log.debug ( "MONA: doc = " + doc );
                //DataType type = doc.getDataType();
                //log.debug ( "MONA: type = " + type );
                //log.debug ( "MONA: name = " + doc.getName() );

				//inputDocs.add(new TaskInputSourceDocument(docs.next()));

                //TaskInputSourceDocument tmp = new TaskInputSourceDocument ( doc );
                //log.debug ( "MONA: tmp = " + tmp );
                //log.debug ( "MONA: type = " + doc.getDataType() );
                //log.debug ( "MONA: name = " + doc.getName() );
				//inputDocs.add ( new TaskInputSourceDocument ( doc ) );

                /*
                if ( type == DataType.DIRECTORY )
                {
				    inputDocs.add(new TaskInputSourceDocument(docs.next()));
                }
                else
                {
				    inputDocs.add(new TaskInputSourceDocument(docs.next()));
				    //inputDocs.add(new TaskInputSourceDocument(doc));
                }
                */
            }
            //log.debug ( "MONA: inputDocs size = " + inputDocs.size() );

			m_taskInput.put(entry.getKey(), inputDocs);
		}
	}

	Task(Connection dbConn, long taskId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(taskId);

		load(dbConn);
	}

	private Task()
	{
		super(TABLE_NAME, KEY_NAME, 255);

		construct(m_comment, m_jobHandle, m_ok, m_stage, m_toolId, m_isTerminal, m_appname);
	}


	// public methods


	public long getTaskId()
	{
		return m_key.getValue();
	}

	public String getComment()
	{
		return m_comment.getValue();
	}

	public void setComment(String comment)
	{
		m_comment.setValue(comment);
	}

	public String getAppname()
	{
		return m_appname.getValue();
	}

	public void setAppname(String appname)
	{
		m_appname.setValue(appname);
	}

	public String getJobHandle()
	{
		return m_jobHandle.getValue();
	}

	public void setJobHandle(String jobHandle)
	{
		m_jobHandle.setValue(jobHandle);
	}

	public boolean isOk()
	{
		return m_ok.getValue();
	}

	public void setOk(Boolean ok)
	{
		m_ok.setValue(ok);
	}

	public boolean isTerminal()
	{
		return m_isTerminal.getValue();
	}

	public void setTerminal(Boolean b)
	{
		m_isTerminal.setValue(b);
	}


	public TaskRunStage getStage()
	{
		return TaskRunStage.valueOf(m_stage.getValue());
	}

	public void setStage(TaskRunStage stage)
	{
		m_stage.setValue(stage.toString());
	}

	public String getToolId()
	{
		return m_toolId.getValue();
	}

	public void setToolId(String toolId)
	{
		m_toolId.setValue(toolId);
	}

	public void setTool(Tool tool)
	{
		setToolId(tool.getToolId());
	}

	@Override
	public String getUUID()
	{
		return getJobHandle();
	}

	@Override
	public void setUUID(String uuid)
	{
		setJobHandle(uuid);
	}

	/*
		Returns true if task has been placed in cipres run queue, or is being
		processed.

		False if task hasn't been queued to run, or has finished/failed.

		You can also just check isTerminal() if you know the job was started
		and want to see if it has finished.  On completion isOK = true if
		job finished ok, isOK = false if not.
	*/
	public boolean isRunning()
	{
		//return ((getStage() >= TaskRunStage.QUEUE) && !isTerminal());
		return (getStage().compareTo(TaskRunStage.QUEUE) >= 0) && !isTerminal();
	}

	public Map<String, String> properties() throws SQLException, IOException
	{
		if (m_properties == null) {
			Map<String, String> newProperties = new TreeMap<String, String>();

			if (!isNew()) {
				Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
				PreparedStatement selectStmt = null;
				ResultSet propRows = null;

				try {
					selectStmt = dbConn.prepareStatement("SELECT PROPERTY, VALUE FROM task_properties WHERE TASK_ID = ?");

					m_key.setParameter(selectStmt, 1);

					propRows = selectStmt.executeQuery();

					while (propRows.next())
						newProperties.put(propRows.getString(1), propRows.getString(2));
				}
				finally {
					if (propRows != null)
						propRows.close();

					if (selectStmt != null)
						selectStmt.close();

					dbConn.close();
				}
			}

			m_properties = new PropertyMap(newProperties);
		}

		return m_properties;
	}

	public Map<String, String> toolParameters() throws SQLException, IOException
	{
		if (m_toolParameters == null) {
			Map<String, String> newParameters = new TreeMap<String, String>();

			if (!isNew()) {
				Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
				PreparedStatement selectStmt = null;
				ResultSet paramRows = null;

				try {
					selectStmt = dbConn.prepareStatement("SELECT PARAMETER, VALUE FROM tool_parameters WHERE TASK_ID = ?");

					m_key.setParameter(selectStmt, 1);

					paramRows = selectStmt.executeQuery();

					while (paramRows.next())
						newParameters.put(paramRows.getString(1), paramRows.getString(2));
				}
				finally {
					if (paramRows != null)
						paramRows.close();

					if (selectStmt != null)
						selectStmt.close();

					dbConn.close();
				}
			}

			m_toolParameters = new ParameterMap(newParameters);
		}

		return m_toolParameters;
	}

	public Map<String, List<TaskInputSourceDocument>> input() throws IOException, SQLException
	{
		if (m_taskInput == null) {
			Map<String, List<TaskInputSourceDocument>> newInput;

			if (!isNew())
				newInput = TaskInputParameter.findTaskInput(m_key.getValue());
			else
				newInput = new TreeMap<String, List<TaskInputSourceDocument>>();

			m_taskInput = new InputMap(newInput);
		}

		return m_taskInput;
	}

	public Map<String, List<TaskOutputSourceDocument>> output() throws IOException, SQLException
	{
		if (m_taskOutput == null) {
			Map<String, List<TaskOutputSourceDocument>> newOutput;

			if (!isNew())
				newOutput = TaskOutputParameter.findTaskOutput(m_key.getValue());
			else
				newOutput = new TreeMap<String, List<TaskOutputSourceDocument>>();

			m_taskOutput = new OutputMap(newOutput, false);
		}

		return m_taskOutput;
	}

	public Map<String, List<TaskOutputSourceDocument>> intermediateOutput() throws IOException, SQLException
	{
		if (m_intermediateTaskOutput == null) {
			Map<String, List<TaskOutputSourceDocument>> newOutput;

			if (!isNew())
				newOutput = TaskOutputParameter.findIntermediateTaskOutput(m_key.getValue());
			else
				newOutput = new TreeMap<String, List<TaskOutputSourceDocument>>();

			m_intermediateTaskOutput = new OutputMap(newOutput, true);
		}

		return m_intermediateTaskOutput;
	}

	public List<TaskLogMessage> logMessages() throws SQLException, IOException
	{
		if (m_logMessages == null) {
			List<TaskLogMessage> newMessages;

			if (!isNew())
				newMessages = TaskLogMessage.findLogMessages(m_key.getValue());
			else
				newMessages = new ArrayList<TaskLogMessage>();

			m_logMessages = new LogMessageList(newMessages);
		}

		return m_logMessages;
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof Task == false)
			return false;

		Task otherTask = (Task) other;

		if (isNew() || otherTask.isNew())
			return false;

		return getTaskId() == otherTask.getTaskId();
	}

	@Override
	public int hashCode()
	{
		return (new Long(getTaskId())).hashCode();
	}

	@Override
	public int compareTo(Task other)
	{
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getTaskId() - other.getTaskId());
	}

	public static Task findTask(String path) throws IOException, SQLException
	{
		if (!path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be absolute");

		int offset = path.lastIndexOf(SEPARATOR);
		Long enclosingFolderId;

		if (offset > 1) {
			String folderName = path.substring(0, offset);
			Folder enclosingFolder = Folder.findFolder(folderName);

			if (enclosingFolder == null)
				return null;

			enclosingFolderId = enclosingFolder.getFolderId();
		}
		else
			enclosingFolderId = null;

		String fileName = path.substring(offset + 1);
		List<Task> tasks = findTasks(new LongCriterion("ENCLOSING_FOLDER_ID", enclosingFolderId), new StringCriterion("LABEL", fileName));

		if (tasks.isEmpty())
				return null;

		return tasks.get(0);
	}

	public static List<Task> findTasksByAge(int age) throws Exception
	{
		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet taskRows = null;

		try {
			selectStmt = dbConn.prepareStatement("SELECT TASK_ID FROM " + TABLE_NAME + " WHERE DATEDIFF(NOW(), CREATION_DATE) > ? ");
			selectStmt.setInt(1, age);
			taskRows = selectStmt.executeQuery();

			List<Task> tasks = new ArrayList<Task>();
			while (taskRows.next())
				tasks.add(new Task(taskRows.getLong(1)));
			return tasks;
		}
		finally {
			if (taskRows != null)
				taskRows.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}

	}

	/*
		Returns task with specified job handle.  Null if no tasks found
	*/
	public static Task findTaskByJobHandle(String jobHandle) throws IOException, SQLException
	{
		List<Task> tasks;
		tasks = Task.findTasks(new StringCriterion("JOBHANDLE", jobHandle));

		assert tasks.size() <= 1 : "Found multiple tasks with jobHandle: " + jobHandle;

		if (tasks.size() == 0)
		{
			return null;
		}

		return tasks.get(0);
	}


	// package methods


	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
		super.load(dbConn);

		m_properties = null;
		m_toolParameters = null;
		m_taskInput = null;
		m_taskOutput = null;
		m_logMessages = null;
	}

	@Override
	void delete(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

		delete(dbConn, getKey());

		m_key.reset();
	}

	static List<Task> findTasks(Criterion... keys) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder("SELECT TASK_ID FROM " + TABLE_NAME + " WHERE ");

		stmtBuilder.append(keys[0].getPhrase());

		for (int i = 1 ; i < keys.length ; i += 1) {
			stmtBuilder.append(" AND ");
			stmtBuilder.append(keys[i].getPhrase());
		}

		stmtBuilder.append(" ORDER BY CREATION_DATE ");

		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet taskRows = null;

		try {
			selectStmt = dbConn.prepareStatement(stmtBuilder.toString());

			int index = 1;

			for (int i = 0 ; i < keys.length ; i += 1)
				index = keys[i].setParameter(selectStmt, index);

			taskRows = selectStmt.executeQuery();

			List<Task> folders = new ArrayList<Task>();

			while (taskRows.next())
				folders.add(new Task(dbConn, taskRows.getLong(1)));

			return folders;
		}
		finally {
			if (taskRows != null)
				taskRows.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}
	}

	/**
	 *
	 * @param dbConn
	 * @param taskId
	 * @throws IOException
	 * @throws SQLException
	 */
	static void delete(Connection dbConn, long taskId) throws IOException, SQLException
	{
		Criterion key = new LongCriterion(KEY_NAME, taskId);

		delete(dbConn, key);
	}


	// private methods


	private static void delete(Connection dbConn, Criterion taskKey) throws IOException, SQLException
	{
		(new DeleteOp("task_properties", taskKey)).execute(dbConn);
		(new DeleteOp("tool_parameters", taskKey)).execute(dbConn);
		(new DeleteOp("task_log_messages", taskKey)).execute(dbConn);

		deleteInputParams(dbConn, taskKey);
		deleteOutputParams(dbConn, taskKey);

		(new DeleteOp(TABLE_NAME, taskKey)).execute(dbConn);
	}

	private static void deleteInputParams(Connection dbConn, Criterion taskKey) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder();

		stmtBuilder.append("SELECT INPUT_ID FROM task_input_parameters WHERE ");
		stmtBuilder.append(taskKey.getPhrase());

		PreparedStatement selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
		ResultSet paramRows = null;

		try {
			taskKey.setParameter(selectStmt, 1);

			paramRows = selectStmt.executeQuery();

			while (paramRows.next())
				TaskInputParameter.delete(dbConn, paramRows.getLong(1));
		}
		finally {
			if (paramRows != null)
				paramRows.close();

			selectStmt.close();
		}
	}

	private static void deleteOutputParams(Connection dbConn, Criterion taskKey) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder();

		stmtBuilder.append("SELECT OUTPUT_ID FROM task_output_parameters WHERE ");
		stmtBuilder.append(taskKey.getPhrase());

		PreparedStatement selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
		ResultSet paramRows = null;

		try {
			taskKey.setParameter(selectStmt, 1);

			paramRows = selectStmt.executeQuery();

			while (paramRows.next())
				TaskOutputParameter.delete(dbConn, paramRows.getLong(1));
		}
		finally {
			if (paramRows != null)
				paramRows.close();

			selectStmt.close();
		}
	}
}
