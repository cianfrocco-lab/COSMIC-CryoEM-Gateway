/*
 * WorkbenchSession.java
 */
package org.ngbw.sdk;


import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.tool.TaskInitiate;
import org.ngbw.sdk.tool.BaseProcessWorker;
import org.ngbw.sdk.tool.TaskMonitor;
import org.ngbw.sdk.tool.WorkingDirectory;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.core.shared.UserRole;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.Group;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.util.FolderComparator;
import org.ngbw.sdk.database.util.FolderSortableField;
import org.ngbw.sdk.database.util.TaskComparator;
import org.ngbw.sdk.database.util.TaskSortableField;
import org.ngbw.sdk.database.util.UserDataItemComparator;
import org.ngbw.sdk.database.util.UserDataItemSortableField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 *
 * @author Roland H. Niedner
 * @author Paul Hoover
 *
 */
public class WorkbenchSession {
	private static final Log log = LogFactory.getLog(WorkbenchSession.class);

	private final Workbench workbench;
	private final long userId;
	private User user;


	WorkbenchSession(User user, Workbench workbench)
	{
		this.userId = user.getUserId();
		this.workbench = workbench;
	}

	/**
	 * Method returns the Workbench instance that spawned the session.
	 *
	 * @return workbench
	 */
	public Workbench getWorkbench() {
		return workbench;
	}

	/**
	 * Method return the username of the user who owns this session.
	 *
	 * @return username
	 * @throws SQLException
	 * @throws IOException
	 */
	public String getUsername() throws IOException, SQLException {
		return getUser().getUsername();
	}

	public String getEmail() throws Exception
	{
		return getUser().getEmail();
	}

	/**
	* Returns client's ip address as supplied by the web application when creating the workbench session.
	* TODO: implement this.
	*/
	public String getIP() throws Exception
	{
		return "127.0.0.0";
	}

	/**
	 * Method return the role of the user who owns this session.
	 *
	 * @return userRole
	 * @throws SQLException
	 * @throws IOException
	 */
	public UserRole getUserRole() throws IOException, SQLException {
		return getUser().getRole();
	}

	/**
	 * Method returns the map of preferences for the user who owns this session.
	 *
	 * @return preferences
	 * @throws IOException
	 * @throws SQLException
	 */
	public Map<String, String> getPreferences() throws SQLException, IOException {
		return getUser().preferences();
	}

	/* UserService Methods */

	/**
	 * Method allows the user to reset his/her/its password. To succeed the
	 * existing password and the submitted oldPassword need to match.
	 *
	 * @param oldPassword
	 * @param newPassword
	 * @throws UserAuthenticationException
	 * @throws SQLException
	 * @throws IOException
	 */
	public void resetPassword(String oldPassword, String newPassword) throws UserAuthenticationException, IOException, SQLException
	{
		if (oldPassword == null)
			throw new NullPointerException("oldPassword");

		if (newPassword == null)
			throw new NullPointerException("newPassword");

		User user = getUser();
		String oldHash = StringUtils.getMD5HexString(oldPassword);

		if (!user.getPassword().equals(oldHash))
			throw new UserAuthenticationException("Passwords don't match!");

		user.setPassword(newPassword);

		user.save();
	}

	/**
	 * Methods retrieves the User object.
	 *
	 * @return user
	 * @throws SQLException
	 * @throws IOException
	 */
	public User getUser() throws IOException, SQLException {
		return new User(userId);
	}

	/**
	 * Methods updates the user object.
	 *
	 * @param user
	 * @throws SQLException
	 * @throws IOException
	 */
	public void updateUser(User user) throws IOException, SQLException {
		if (user.getUserId() != userId)
			throw new RuntimeException("This method only updates the owner of this session!");

		user.save();
	}

	/**
	 * Method returns a Set of all groups that the user is a member of.
	 *
	 * @return groups
	 * @throws IOException
	 * @throws SQLException
	 */
	public Set<Group> getGroups() throws SQLException, IOException {
		return getUser().memberships();
	}

	/**
	 * Method adds the user to the submitted group.
	 *
	 * @param group
	 * @throws SQLException
	 * @throws IOException
	 */
	public void addToGroup(Group group) throws IOException, SQLException {
		User user = getUser();

		user.memberships().add(group);

		user.save();
	}

	public void setTeragridChargeNumber(String chargeNumber) throws IOException, SQLException
	{
		User user = getUser();
		user.setAccount(User.TERAGRID, chargeNumber);
		user.save();
	}

	/**
	 * Method deletes the user from the submitted group.
	 *
	 * @param group
	 * @throws SQLException
	 * @throws IOException
	 */
	public void deleteFromGroup(Group group) throws IOException, SQLException {
		User user = getUser();

		user.memberships().remove(group);

		user.save();
	}

	/**
	 * Returns a <code>WorkbenchSession</code> object for the indicated user. Only users with
	 * the <code>ADMIN</code> user role can open sessions as other users.
	 *
	 * @param username
	 * @return
	 * @throws UserAuthenticationException
	 * @throws IOException
	 * @throws SQLException
	 */
	public WorkbenchSession impersonateUser(String username) throws UserAuthenticationException, IOException, SQLException
	{
		if (getUserRole() != UserRole.ADMIN)
			throw new UserAuthenticationException("Only ADMIN users can open sessions as other users");

		User otherUser = User.findUserByUsername(username);

		if (otherUser == null)
			throw new UserAuthenticationException("User does not exist!");

		return new WorkbenchSession(otherUser, workbench);
	}

	/* UserDataService Methods */

	/**
	 * Method sorts the submitted collection by the submitted field. The boolean
	 * attribute allows you the specify reverse sorting.
	 *
	 * @param dataItems
	 * @param field
	 * @param reverse
	 */
	public void sortUserDataItems(List<UserDataItem> dataItems,
			UserDataItemSortableField field, boolean reverse) {
		UserDataItemComparator.sort(dataItems, field, reverse);
	}

	/**
	 * Method sorts the submitted collection by the submitted field. The boolean
	 * attribute allows you the specify reverse sorting.
	 *
	 * @param tasks
	 * @param field
	 * @param reverse
	 */
	public void sortTasks(List<Task> tasks, TaskSortableField field,
			boolean reverse) {
		TaskComparator.sort(tasks, field, reverse);
	}

	/**
	 * Method sorts the submitted collection by the submitted field. The boolean
	 * attribute allows you the specify reverse sorting.
	 *
	 * @param folders
	 * @param field
	 * @param reverse
	 */
	public void sortFolders(List<Folder> folders, FolderSortableField field,
			boolean reverse) {
		FolderComparator.sort(folders, field, reverse);
	}

	/* FolderItem methods */

	/**
	 * Method deletes all folders and anything in it.
	 * @throws SQLException
	 * @throws IOException
	 *
	 */
	public void deleteAllUserData() throws IOException, SQLException {
		User user = getUser();

		user.deleteData();
	}

	/**
	 * Method moves the submitted UserDataItem to the submitted (new) enclosing
	 * Folder.
	 *
	 * @param dataItem
	 * @param enclosingFolder
	 * @throws SQLException
	 * @throws IOException
	 */
	public void move(UserDataItem dataItem, Folder enclosingFolder) throws IOException, SQLException {
		dataItem.setEnclosingFolder(enclosingFolder);

		dataItem.save();
	}

	public void move ( UserDataDirItem dataItem, Folder enclosingFolder )
        throws IOException, SQLException
    {
		dataItem.setEnclosingFolder ( enclosingFolder );
		dataItem.save();
	}

	/**
	 * Method copies the submitted UserDataItem to the submitted (new) enclosing
	 * Folder. This will create a new UserDataItem instance that is persisted in
	 * the new enclosing folder. The client is encouraged to reload the folder
	 * and UserDataItem display to reflect the change.
	 *
	 * @param item
	 * @param enclosingFolder
	 * @throws SQLException
	 * @throws IOException
	 */
	public void copy(UserDataItem item, Folder enclosingFolder) throws IOException, SQLException {
		UserDataItem copiedItem = new UserDataItem(item, enclosingFolder);

		copiedItem.save();
	}

	public void copy ( UserDataDirItem item, Folder enclosingFolder )
        throws IOException, SQLException
    {
		UserDataDirItem copiedItem = new UserDataDirItem ( item,
            enclosingFolder );
		copiedItem.save();
	}

	/**
	 * Method moves the submitted Task to the submitted (new) enclosing Folder.
	 *
	 * @param task
	 * @param enclosingFolder
	 * @throws SQLException
	 * @throws IOException
	 */
	public void move(Task task, Folder enclosingFolder) throws IOException, SQLException {
		task.setEnclosingFolder(enclosingFolder);

		task.save();
	}

	/**
	 * Method moves the submitted Folder to the submitted (new) enclosing
	 * Folder.
	 *
	 * @param folder
	 * @param enclosingFolder
	 * @throws SQLException
	 * @throws IOException
	 */
	public void move(Folder folder, Folder enclosingFolder) throws IOException, SQLException {
		folder.setEnclosingFolder(enclosingFolder);

		folder.save();
	}

	/* Folder methods */

	/**
	 * Method saves and updates the submitted folder instance and returns the
	 * persisted folder instance which is equal but may or may not be identical
	 * to the submitted instance. if the submitted folder instance was transient
	 * the persisted folder instance will be a top-level folder.
	 *
	 * @param folder
	 * @return folder
	 * @throws SQLException
	 * @throws IOException
	 */
	public Folder saveFolder(Folder folder) throws IOException, SQLException {
		folder.save();

		return folder;
	}

	/**
	 * Method saves and updates the submitted folder instance and returns the
	 * persisted folder instance which is equal but may or may not be identical
	 * to the submitted instance. If the submitted folder instance was transient
	 * and the enclosing Folder argument was null the persisted folder instance
	 * will be a top-level folder. In every other case the folder will be a
	 * subfolder of the submitted enclosing Folder. In case the submitted folder
	 * (first argument) was persistent the method is practically identical to
	 * the moveFolder method.
	 *
	 * @param folder
	 * @param enclosingFolder
	 * @return folder
	 * @throws SQLException
	 * @throws IOException
	 */
	public Folder saveFolder(Folder folder, Folder enclosingFolder) throws IOException, SQLException {
		folder.setEnclosingFolder(enclosingFolder);

		folder.save();

		return folder;
	}

	/**
	 * Method deletes the submitted folder from the UserData database.
	 *
	 * @param folder
	 * @throws SQLException
	 * @throws IOException
	 */
	public void deleteFolder(Folder folder) throws IOException, SQLException {
		folder.delete();
	}

	/**
	 * Method returns the persistent folder instance with the submitted id or
	 * null if such an instance does not exist.
	 *
	 * @param id
	 * @return folder
	 * @throws SQLException
	 * @throws IOException
	 */
	public Folder findFolder(Long id) throws IOException, SQLException {
		try {
			return new Folder(id);
		}
		catch (WorkbenchException wbErr) {
			return null;
		}
	}

	/**
	 * Method returns all folders of the user.
	 *
	 * @return folders
	 * @throws SQLException
	 * @throws IOException
	 */
	public List<Folder> findAllUserFolders() throws IOException, SQLException {
		return getUser().findFolders();
	}

	/* UserDataItem methods */

	/**
	 * Method returns the persistent UserDataItem instance with the submitted id
	 * or null if such an instance does not exist.
	 *
	 * @param id
	 * @return userDataItem
	 * @throws SQLException
	 * @throws IOException
	 */
	public UserDataItem findUserDataItem(Long id) throws IOException, SQLException {
		try {
			return new UserDataItem(id);
		}
		catch (WorkbenchException wbErr) {
			return null;
		}
	}

	/**
	 * Method saves the submitted UserDataItem into the submitted Folder and
	 * returns the persisted UserDataItem instance.
	 *
	 * @param dataItem
	 * @param folder
	 * @return userDataItem
	 * @throws SQLException
	 * @throws IOException
	 */
	public UserDataItem saveUserDataItem(UserDataItem dataItem, Folder folder) throws IOException, SQLException {
		dataItem.setEnclosingFolder(folder);

		dataItem.save();

		return dataItem;
	}

	/**
	 * Method deletes the submitted userDataItem.
	 *
	 * @param dataItem
	 * @throws SQLException
	 * @throws IOException
	 */
	public void deleteUserDataItem(UserDataItem dataItem) throws IOException, SQLException {
		dataItem.delete();
	}

    /**
     * Method returns the persistent UserDataDirItem instance of the given
     * id or null if instance is not found
     * @author Mona Wong
     **/
	public UserDataDirItem findUserDataDirItem ( Long id )
        throws IOException, SQLException
    {
		try
        {
			return new UserDataDirItem ( id );
		}
		catch ( WorkbenchException wbErr )
        {
			return null;
		}
	}

	public void deleteUserDataDirItem ( UserDataDirItem dataItem )
        throws IOException, SQLException
    {
		dataItem.delete();
	}

	/* SourceDocument to be sent of to the conversion services */

	/**
	 * Method converts a UserDataItem into a SourceDocument.
	 *
	 * @param dataItem
	 * @return SourceDocument
	 */
	public SourceDocument getSourceDocument(UserDataItem dataItem) {
		return dataItem;
	}

	/* Task related services */

	/**
	 * Method returns the persistent task instance with the submitted id or null
	 * if such an instance does not exist.
	 *
	 * @param id
	 * @return task
	 * @throws SQLException
	 * @throws IOException
	 */
	public Task findTask(Long id) throws IOException, SQLException {
		try {
			return new Task(id);
		}
		catch (WorkbenchException wbErr) {
			return null;
		}
	}

	/**
	 * Method returns all persistent task instances for the user.
	 *
	 * @return task
	 * @throws SQLException
	 * @throws IOException
	 */
	public List<Task> findAllUserTasks() throws IOException, SQLException {
		return getUser().findTasks();
	}

	/**
	 * Method saves a transient (new) task instance into the submitted folder ad
	 * returns the persisted instance.
	 *
	 * @param task
	 * @param folder
	 * @return task
	 * @throws SQLException
	 * @throws IOException
	 */
	public Task saveTask(Task task, Folder folder) throws IOException, SQLException {
		task.setEnclosingFolder(folder);

		task.save();

		return task;
	}

	/**
	 * Method updates a task and returns the persisted updated task instance.
	 *
	 * @param task
	 * @return task
	 * @throws SQLException
	 * @throws IOException
	 */
	public Task updateTask(Task task) throws IOException, SQLException {
		task.save();

		return task;
	}

	/**
	 * Method deletes the submitted task instance form the user data database.
	 *
	 * @param task
	 * @throws SQLException
	 * @throws IOException
	 */
	public void deleteTask(Task task) throws IOException, SQLException, Exception {

		// This talks to the remote queue'ing system and does "qdel" or similar.
		TaskMonitor.cancelJob(task);

		// Deletes the task from our database. This is what prevents us from trying to
		// transfer files back to our db when we see that the job has stopped running.
		task.delete();
	}

	/**
	 * Method creates a copy of the given <code>Task</code> object
	 *
	 * @param task
	 * @return a copy of the <code>Task</code> object
	 * @throws IOException
	 * @throws SQLException
	 */
	public Task cloneTask(Task task) throws IOException, SQLException
	{
		if (!workbench.getActiveToolIds().contains(task.getToolId()))
		{
			throw new WorkbenchException("The tool, " + task.getToolId() + " is no longer available on COSMIC2.");
		}
		return new Task(task);
	}

	/**
	 * Method returns the all input data staged for a submitted task.
	 *
	 * @param task
	 * @return input
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<String, List<TaskInputSourceDocument>> getInput(Task task) throws IOException, SQLException {
		return task.input();
	}

	/**
	 * Method returns the all output data staged for a submitted task.
	 *
	 * @param task
	 * @return output
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<String, List<TaskOutputSourceDocument>> getOutput(Task task) throws IOException, SQLException {
		return task.output();
	}

	/**
	 * Returns a <code>TaskInputSourceDocument</code> object that corresponds
	 * to the given primary key. If the key doesn't identify any record, <code>null</null>
	 * is returned.
	 *
	 * @param id
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public TaskInputSourceDocument getInputSourceDocument(Long id) throws IOException, SQLException
	{
		return new TaskInputSourceDocument(id);
	}

	/**
	 * Returns a <code>TaskOutputSourceDocument</code> object that corresponds
	 * to the given primary key. If the key doesn't identify any record, <code>null</null>
	 * is returned.
	 *
	 * @param id
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public TaskOutputSourceDocument getOutputSourceDocument(Long id) throws IOException, SQLException
	{
		return new TaskOutputSourceDocument(id);
	}

	/* Factory methods */

	/**
	 * Method generates and returns a new Folder instance.
	 *
	 * @return folder
	 * @throws SQLException
	 * @throws IOException
	 */
	public Folder getFolderInstance() throws IOException, SQLException {
		return new Folder(getUser().getHomeFolder());
	}

	/**
	 * Method generates and returns a new UserDataItem instance.
	 *
	 * @return userDataItem
	 */
	public UserDataItem getUserDataItemInstance(Folder folder) {
		return new UserDataItem(folder);
	}


	/**
	 * Method generates and returns a new Task instance.
	 *
	 * @return task
	 */
	public Task getTaskInstance(Folder folder) {
		return new Task(folder);
	}

	/**
	 * Imports data from the Biology Workbench to the Next Generation Biology Workbench.
	 *
	 * @param username a BWB user name
	 * @param password a plaintext password
	 * @param parentFolder a <code>Folder</code> object that will contain the imported data items
	 * @return the number of data items imported
	 * @throws Exception
	 */
	public int importBwbData(String username, String password, Folder parentFolder) throws Exception
	{
		return (new BwbImporter(workbench)).importBwbData(username, password, parentFolder);
	}

	public TaskInitiate getTaskInitate()
	{
		return new TaskInitiate(workbench.getServiceFactory());
	}

	public WorkingDirectory getWorkingDirectory(Task task)
	{
		return new WorkingDirectory(workbench.getServiceFactory(), task);
	}

	public boolean workingDirectoryExists(Task task) throws Exception
	{
		try
		{
			return getWorkingDirectory(task).workingDirectoryExists();
		}
		catch(Exception e)
		{
			log.error("", e);
			throw e;
		}
	}

	public List<FileHandler.FileAttributes> listWorkingDirectory(Task task) throws Exception
	{
		return getWorkingDirectory(task).listWorkingDirectory();
	}

	public InputStream getFileFromWorkingDirectory(Task task, String filename) throws Exception
	{
		return getWorkingDirectory(task).getFileFromWorkingDirectory( filename);
	}
}
