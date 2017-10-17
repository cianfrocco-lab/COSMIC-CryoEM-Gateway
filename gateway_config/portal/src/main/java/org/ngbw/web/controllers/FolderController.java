package org.ngbw.web.controllers;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.ngbw.sdk.UserAuthenticationException;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.util.FolderSortableField;

/**
 * Controller class to handle NGBW web application folder functionality.
 *
 * @author Jeremy Carver
 */
public class FolderController extends SessionController
{
	/*================================================================
	 * Constants
	 *================================================================*/
	// session preference keys
	public static final String CURRENT_FOLDER = "currentFolder";

	// folder preference keys
	public static final String EXPANDED = "expanded";

	/*================================================================
	 * Constructor
	 *================================================================*/
	/**
	 * Default constructor.  Requires a valid WorkbenchSession argument in
	 * order to establish the proper connection with the model layer that
	 * is needed to interact with the workbench.
	 *
	 * @param workbenchSession	The WorkbenchSession object representing the
	 * 							current authenticated user's workbench session.
	 *
	 * @throws IllegalArgumentException	if the provided WorkbenchSession
	 * 									object is null.
	 */
	public FolderController(WorkbenchSession workbenchSession)
	throws IllegalArgumentException {
		super(workbenchSession);
	}

	/*================================================================
	 * Accessor Methods
	 *================================================================*/
	/**
	 * Retrieves the specified folder from the database.
	 *
	 * @param folderId	The Long identifier of the folder to be retrieved.
	 *
	 * @return	The specified Folder retrieved from the database.
	 * 			Returns null if the argument ID is null, or if
	 * 			an error occurs.
	 */
	public Folder getFolder(Long folderId) {
		if (folderId == null) {
			debug("The selected folder could not be retrieved " +
				"because the provided folder ID is null.");
			return null;
		} else try {
			return getWorkbenchSession().findFolder(folderId);
		} catch (Throwable error) {
			reportError(error, "Error retrieving folder with ID " + folderId);
			return null;
		}
	}

	/**
	 * Retrieves the current authenticated user's complete list of folders.
	 * This list is "flattened", in the sense that the entire subfolder tree
	 * is included at the top level of the list, in depth-first order.
	 *
	 * @return	The List of Folders owned by the current authenticated user.
	 * 			Returns null if the user has no folders, or if an error occurs.
	 */
	public List<Folder> getAllFolders() {
		try {
			Folder homeFolder = getWorkbenchSession().getUser().getHomeFolder();
			List<Folder> allFolders = getWorkbenchSession().findAllUserFolders();
			Iterator<Folder> folderIter = allFolders.iterator();

			while (folderIter.hasNext()) {
				if (homeFolder.equals(folderIter.next())) {
					folderIter.remove();

					break;
				}
			}

			return allFolders;
		} catch (Throwable error) {
			reportError(error, "Error retrieving folder list");
			return null;
		}
	}

	/**
	 * Retrieves the current authenticated user's list of top-level folders.
	 * No subfolders are included in this list.
	 *
	 * @return	The List of top-level Folders owned by the current authenticated user.
	 * 			Returns null if the user has no folders.
	 */
	public List<Folder> getFolders() {
		List<Folder> allFolders = getAllFolders();
		if (allFolders == null || allFolders.size() < 1)
			return null;
		else {
			List<Folder> folders = new Vector<Folder>();
			for (Folder folder : allFolders) {
				// only add top-level folders, that have no parent
				if (getParentFolder(folder) == null)
					folders.add(folder);
			}
			return sortFolders(folders, FolderSortableField.ID, true);
		}
	}

	/**
	 * Retrieves the total number of folders owned by the
	 * current authenticated user, including subfolders.
	 *
	 * @return	The current authenticated user's total folder count.
	 */
	public int getFolderCount() {
		List<Folder> folders = getAllFolders();
		if (folders == null)
			return 0;
		else return folders.size();
	}

	/**
	 * Determines whether or not the current authenticated user has any folders.
	 *
	 * @return	true if the current user has any folders,
	 * 			false otherwise.
	 */
	public boolean hasFolders() {
		return (getFolderCount() > 0);
	}

	/**
	 * Retrieves a fresh, transient Folder instance.
	 *
	 * @return	The transient Folder instance.
	 */
	public Folder getNewFolder() {
		try {
			return getWorkbenchSession().getFolderInstance();
		} catch (Throwable error) {
			reportError(error, "Error retrieving new folder instance");
			return null;
		}
	}

	/**
	 * Determines whether or not the specified folder is transient,
	 * i.e. has not been saved to the database.
	 *
	 * @param folder	The Folder being checked for transience.
	 *
	 * @return	true if the specified folder is transient (or null),
	 * 			false otherwise.
	 */
	public boolean isTransient(Folder folder) {
		if (folder == null)
			return true;
		else return folder.isNew();
	}

	/**
	 * Determines whether or not the specified folder
	 * has been saved to the database.
	 *
	 * @param folder	The Folder being checked for persistence.
	 *
	 * @return	true if the specified folder is persisted,
	 * 			false otherwise.
	 */
	public boolean isPersisted(Folder folder) {
		return !isTransient(folder);
	}

	/**
	 * Retrieves the number of data items stored in the specified folder.
	 *
	 * @param folder	The Folder whose data count is to be retrieved.
	 *
	 * @return	The number of data items currently stored in the specified folder.
	 * 			Returns 0 if the argument Folder is null,
	 * 			or if an error occurs.
	 */
	public int getDataItemCount(Folder folder) {
		if (folder == null) {
			debug("The provided folder's data item count could not be retrieved " +
				"because the folder is null.");
			return 0;
		} else try {
			return folder.findDataAllItems().size();
		} catch (Throwable error) {
			reportError(error, "Error retrieving data item count for folder " +
				getFolderText(folder));
			return 0;
		}
	}

	/**
	 * Retrieves the number of tasks stored in the specified folder.
	 *
	 * @param folder	The Folder whose task count is to be retrieved.
	 *
	 * @return	The number of tasks currently stored in the specified folder.
	 * 			Returns 0 if the argument Folder is null,
	 * 			or if an error occurs.
	 */
	public int getTaskCount(Folder folder) {
		if (folder == null) {
			debug("The provided folder's task count could not be retrieved " +
				"because the folder is null.");
			return 0;
		} else try {
			return folder.findTasks().size();
		} catch (Throwable error) {
			reportError(error, "Error retrieving task count for folder " +
				getFolderText(folder));
			return 0;
		}
	}

	/**
	 * Retrieves the parent folder of the specified folder.
	 *
	 * @param folder	The Folder whose parent folder is to be retrieved.
	 *
	 * @return	The parent Folder of the specified folder.
	 * 			Returns null if the argument Folder is null,
	 * 			if it has no parent folder, or if an error occurs.
	 */
	public Folder getParentFolder(Folder folder) {
		if (folder == null) {
			debug("The provided folder's parent folder could not be retrieved " +
				"because the folder is null.");
			return null;
		} else try {
			Folder enclosingFolder = folder.getEnclosingFolder();
			Folder homeFolder = getWorkbenchSession().getUser().getHomeFolder();

			if (enclosingFolder == null || enclosingFolder.equals(homeFolder))
				return null;

			return enclosingFolder;

		} catch (Throwable error) {
			reportError(error, "Error retrieving parent folder of folder " +
				getFolderText(folder));
			return null;
		}
	}

	/**
	 * Retrieves the list of subfolders stored under the specified folder.
	 * This list is sorted by folder ID, by default.
	 *
	 * @param folder	The Folder whose subfolders are to be retrieved.
	 *
	 * @return	The List of subfolders stored under the specified folder.
	 * 			Returns null if the argument Folder is null,
	 * 			if no subfolders are present, or if an error occurs.
	 */
	public List<Folder> getSubfolders(Folder folder) {
		if (folder == null) {
			debug("The provided folder's subfolders could not be retrieved " +
				"because the folder is null.");
			return null;
		} else try {
			return sortFolders(folder.findSubFolders(), FolderSortableField.ID, true);
		} catch (Throwable error) {
			reportError(error, "Error retrieving subfolders of folder " + getFolderText(folder));
			return null;
		}
	}

	/**
	 * Retrieves the currently selected folder.
	 *
	 * @return	The Folder that is currently selected in the user interface.
	 * 			Returns null if no folder is currently selected.
	 */
	public Folder getCurrentFolder() {
		String selectedId = getUserPreference(CURRENT_FOLDER);
		if (selectedId == null)
			return null;
		else try {
			return getFolder(Long.parseLong(selectedId));
		} catch (NumberFormatException error) {
			return null;
		}
	}

	/**
	 * Saves the selected folder's ID in the workbench session,
	 * to indicate that it is the user's currently selected folder.
	 *
	 * @param folder	The Folder to be selected.  If this argument is null,
	 * 					then the current folder selection will be cleared.
	 *
	 * @return	true if the folder has been successfully selected,
	 * 			false otherwise.
	 */
	public boolean setCurrentFolder(Folder folder) {
		if (folder == null)
			return clearCurrentFolder();
		String folderText = getFolderText(folder);
		try {
			if (setUserPreference(CURRENT_FOLDER, Long.toString(folder.getFolderId()))) {
				debug("Folder " + folderText + " successfully selected as current folder.");
				return true;
			} else {
				debug("Folder " + folderText + " could not be selected as current folder.");
				return false;
			}
		} catch (Throwable error) {
			reportError(error, "Error selecting folder " + folderText);
			return false;
		}
	}

	/**
	 * Clears the currently selected folder from the workbench session.
	 *
	 * @return	true if the currently selected folder was successfully cleared,
	 * 			false otherwise.
	 */
	public boolean clearCurrentFolder() {
		if (clearUserPreference(CURRENT_FOLDER)) {
			debug("Currently selected folder successfully cleared from the session.");
			return true;
		} else {
			debug("Currently selected folder could not be cleared from the session.");
			return false;
		}
	}

	/**
	 * Determines whether or not the specified folder is the
	 * currently selected folder in the user interface.
	 *
	 * @param folder	The Folder being checked for selection.
	 *
	 * @return	true if the specified folder is currently selected,
	 * 			false otherwise.
	 */
	public boolean isCurrentFolder(Folder folder) {
		if (folder == null)
			return false;
		else {
			Folder currentFolder = getCurrentFolder();
			if (currentFolder == null)
				return false;
			else return (folder.getFolderId() == currentFolder.getFolderId());
		}
	}

	/**
	 * Retrieves the specified preference for the specified folder.
	 *
	 * @param folder		The Folder from which to retrieve the preference.
	 * @param preference	The key or label of the preference to be retrieved.
	 *
	 * @return	The value of the specified folder preference.
	 * 			Returns null if the argument Folder is null,
	 * 			if the argument preference key is null,
	 * 			or an error occurs.
	 */
	public String getFolderPreference(Folder folder, String preference) {
		String folderText = getFolderText(folder);
		if (folder == null) {
			debug("Preference could not be retrieved from the provided folder " +
				"because the folder is null.");
			return null;
		} else if (preference == null) {
			debug("Preference could not be retrieved from folder " + folderText +
				" because the provided preference key is null.");
			return null;
		} else try {
			return folder.preferences().get(preference);
		} catch (Throwable error) {
			reportError(error, "Error retrieving preference \"" + preference +
				"\" from folder " + folderText);
			return null;
		}
	}

	/**
	 * Saves the specified preference value to the specified folder's
	 * stored map of preferences.
	 *
	 * @param folder		The Folder to which the preference is to be saved.
	 * @param preference	The key or label of the preference to be saved.
	 * @param value			The value of the preference to be saved.
	 *
	 * @return	true if the folder preference was successfully saved,
	 * 			false otherwise
	 */
	public boolean setFolderPreference(Folder folder, String preference, String value) {
		if (value == null)
			return clearFolderPreference(folder, preference);
		String folderText = getFolderText(folder);
		if (preference == null) {
			debug("Preference could not be written to folder " + folderText +
				" because the provided preference key is null.");
			return false;
		} else try {
			folder.preferences().put(preference, value);
			if (isPersisted(folder))
				folder.save();
			debug("Preference \"" + preference + "\" -> \"" + value +
				"\" successfully written to folder " + getFolderText(folder) + ".");
			return true;
		} catch (Throwable error) {
			reportError(error, "Error writing preference \"" + preference +
				"\" -> \"" + value + "\" to folder " + folderText);
			return false;
		}
	}

	/**
	 * Removes the specified preference value from the specified folder's
	 * stored map of preferences.
	 *
	 * @param folder		The Folder from which the preference is to be cleared.
	 * @param preference	The key or label of the preference to be cleared.
	 * @param value			The value of the preference to be cleared.
	 *
	 * @return	true if the folder preference was successfully cleared,
	 * 			false otherwise
	 */
	public boolean clearFolderPreference(Folder folder, String preference) {
		String folderText = getFolderText(folder);
		if (preference == null) {
			debug("Preference could not be removed from folder " + folderText +
				" because the provided preference key is null.");
			return false;
		} else try {
			folder.preferences().remove(preference);
			if (isPersisted(folder))
				folder.save();
			debug("Preference \"" + preference +
				"\" successfully removed from folder " + getFolderText(folder) + ".");
			return true;
		} catch (Throwable error) {
			reportError(error, "Error removing preference \"" + preference +
				"\" from folder " + folderText);
			return false;
		}
	}

	/**
	 * Retrieves the specified folder's description.
	 *
	 * @param folder	The Folder whose description is to be retrieved.
	 *
	 * @return	The folder's description.
	 */
	public String getDescription(Folder folder) {
		return getFolderPreference(folder, DESCRIPTION);
	}

	/**
	 * Sets the specified folder's description.
	 *
	 * @param folder		The Folder whose description is to be set.
	 * @param description	The description to set.
	 *
	 * @return	true if the folder description was successfully set,
	 * 			false otherwise.
	 */
	public boolean setDescription(Folder folder, String description) {
		if (description == null)
			return clearDescription(folder);
		else return setFolderPreference(folder, DESCRIPTION, description);
	}

	/**
	 * Clears the specified folder's description.
	 *
	 * @param folder	The Folder whose description is to be cleared.
	 *
	 * @return	true if the folder description was successfully cleared,
	 * 			false otherwise.
	 */
	public boolean clearDescription(Folder folder) {
		return clearFolderPreference(folder, DESCRIPTION);
	}

	/**
	 * Determines whether or not the specified folder is currently expanded
	 * in the user interface.
	 *
	 * @param folder	The Folder being checked for expansion.
	 *
	 * @return	true if the specified folder is currently expanded,
	 * 			false otherwise.
	 */
	public boolean isExpanded(Folder folder) {
		return Boolean.parseBoolean(getFolderPreference(folder, EXPANDED));
	}

	/**
	 * Sets whether or not the specified folder is currently expanded
	 * in the user interface.
	 *
	 * @param folder		The Folder whose expansion is to be set.
	 * @param expanded		true if the folder is expanded, false otherwise.
	 *
	 * @return	true if the folder expansion was successfully set,
	 * 			false otherwise.
	 */
	public boolean setExpanded(Folder folder, boolean expanded) {
		return setFolderPreference(folder, EXPANDED, Boolean.toString(expanded));
	}

	/**
	 * Toggles the specified folder's current state of expansion.
	 *
	 * @param folder		The Folder whose expansion is to be toggled.
	 *
	 * @return	true if the folder expansion was successfully toggled,
	 * 			false otherwise.
	 */
	public boolean toggleExpanded(Folder folder) {
		boolean expanded = isExpanded(folder);
		return setExpanded(folder, !expanded);
	}

	/*================================================================
	 * Functionality Methods
	 *================================================================*/
	/**
	 * Refreshes the specified folder with the latest contents of the
	 * associated row in the database.  If the folder is transient,
	 * nothing will happen and false will be returned.
	 *
	 * @param folder	The Folder to be refreshed.
	 *
	 * @return	returns true if the folder was successfully refreshed,
	 * 			false otherwise.
	 */
	public boolean refreshFolder(Folder folder) {
		try {
			folder.load();
			return true;
		} catch (Throwable error) {
			reportError(error, "Error refreshing folder " + getFolderText(folder));
			return false;
		}
	}

	/**
	 * Saves the current contents of the specified folder to the
	 * associated row in the database.  If the folder is transient,
	 * nothing will happen and false will be returned.
	 *
	 * @param folder	The Folder to be saved.
	 *
	 * @return	returns true if the folder was successfully saved,
	 * 			false otherwise.
	 */
	public boolean saveFolder(Folder folder) {
		try {
			folder.save();
			return true;
		} catch (Throwable error) {
			reportError(error, "Error saving folder " + getFolderText(folder));
			return false;
		}
	}

	/**
	 * Creates a new folder with the specified properties, and saves it to the database
	 * under the ownership of the currently authenticated user.
	 *
	 * @param label			The label of the folder to be created.
	 * @param description	The description of the folder to be created.
	 * @param parentFolder	The Folder under which the newly created folder is to be saved,
	 * 						or null if the new folder is to be saved at the top level of
	 * 						the user's folder structure.
	 *
	 * @return	The persistent Folder after being saved to the database.
	 * 			Returns null if the specified folder properties are not valid,
	 * 			or if an error occurs.
	 *
	 * @throws	UserAuthenticationException if the user is not properly registered,
	 * 			because guest users are not permitted to create folders.
	 */
	public Folder createFolder(String label, String description, Folder parentFolder)
		throws UserAuthenticationException {
		if (isRegistered() == false) {
			debug("Folder could not be created because " +
				"the current user is not registered.");
			throw new UserAuthenticationException("You must register to create a folder.");
		} else if (label == null) {
			debug("Folder could not be created because " +
				"the provided label is null.");
			return null;
		} else try {
			WorkbenchSession session = getWorkbenchSession();
			Folder folder = session.getFolderInstance();
			//TODO: add proper support for groups
			folder.setLabel(label);
			if (description != null && description.trim().equals("") == false)
				folder.preferences().put(DESCRIPTION, description);
			if (parentFolder ==  null) {
				folder = session.saveFolder(folder);
				debug("Folder " + getFolderText(folder) + " successfully created.");
			} else {
				folder = session.saveFolder(folder, parentFolder);
				debug("Subfolder " + getFolderText(folder) + " successfully created " +
					"under parent folder " + getFolderText(parentFolder) + ".");
			}
			return folder;
		} catch (Throwable error) {
			reportError(error, "Error creating folder \"" + label + "\"");
			return null;
		}
	}

	/**
	 * Updates an existing folder with the specified properties,
	 * and saves the changes to the database.
	 *
	 * @param folder		The existing Folder to be updated.
	 * @param label			The new label of the folder to be updated.
	 * @param description	The new description of the folder to be updated.
	 *
	 * @return	The persistent Folder after being saved to the database.
	 * 			Returns null if the specified folder is not present in the database,
	 * 			if the specified folder properties are not valid,
	 * 			or if an error occurs.
	 *
	 * @throws	UserAuthenticationException if the user is not properly registered,
	 * 			because guest users are not permitted to edit folders.
	 */
	public Folder editFolder(Folder folder, String label, String description)
		throws UserAuthenticationException {
		String folderText = getFolderText(folder);
		if (isRegistered() == false) {
			debug("Folder " + folderText + " could not be edited because " +
				"the current user is not registered.");
			throw new UserAuthenticationException("You must register to edit a folder.");
		} else if (label == null) {
			debug("Folder " + folderText + " could not be edited because " +
				"the provided label is null.");
			return null;
		} else try {
			folder.load();
			folder.setLabel(label);
			if (description == null || description.trim().equals(""))
				folder.preferences().remove(DESCRIPTION);
			else folder.preferences().put(DESCRIPTION, description);
			folder = getWorkbenchSession().saveFolder(folder);
			debug("Folder " + getFolderText(folder) + " successfully edited.");
			return folder;
		} catch (Throwable error) {
			reportError(error, "Error editing folder " + folderText);
			return null;
		}
	}

	/**
	 * Deletes an existing folder from the database.
	 *
	 * @param folder	The existing Folder to be deleted.
	 *
	 * @return	true if the folder was successfully deleted,
	 * 			false otherwise.
	 *
	 * @throws	UserAuthenticationException if the user is not properly registered,
	 * 			because guest users are not permitted to delete folders.
	 */
	public boolean deleteFolder(Folder folder) throws UserAuthenticationException {
		String folderText = getFolderText(folder);
		if (isRegistered() == false) {
			debug("Folder " + folderText + " could not be deleted because " +
				"the current user is not registered.");
			throw new UserAuthenticationException("You must register to delete a folder.");
		} else try {
			folder.load();
			if (isCurrentFolder(folder))
				clearCurrentFolder();
			getWorkbenchSession().deleteFolder(folder);
			debug("Folder " + folderText + " successfully deleted.");
			return true;
		} catch (Throwable error) {
			reportError(error, "Error deleting folder " + folderText);
			return false;
		}
	}

	public int importBwbData(String username, String password)
		throws WorkbenchException, UserAuthenticationException {
		Folder folder = getCurrentFolder();
		String folderText = getFolderText(folder);
		if (folder == null) {
			debug("BWB data could not be imported because no folder is currently selected.");
			throw new WorkbenchException("You must select a folder to import your "+
				"Biology Workbench data into.");
		} else if (username == null) {
			debug("BWB data could not be imported because the provided username is null.");
			return 0;
		} else if (password == null) {
			debug("BWB data could not be imported because the provided password is null.");
			return 0;
		} else try {
			int imported = getWorkbenchSession().importBwbData(username, password, folder);
			String count;
			if (imported > 0) {
				count = imported + " data item";
				if (imported != 1)
					count += "s were successfully ";
				else count += " was successfully ";
			} else count = "No data items were ";
			debug(count + "imported from BWB account \"" + username +
				"\" to folder " + folderText);
			return imported;
		} catch (UserAuthenticationException error) {
			reportCaughtError(error, "Error authenticating original BWB user \"" + username + "\"");
			throw new UserAuthenticationException("Sorry, the information you entered " +
				"did not match our records.  Please try again.", error);
		} catch (Throwable error) {
			reportError(error, "Error importing BWB Data into folder " + folderText);
			return 0;
		}
	}

	public List<Folder> sortFolders(List<Folder> folders,
		FolderSortableField field, boolean reverse) {
		if (folders == null || folders.size() < 1 || field == null)
			return null;
		else try {
			getWorkbenchSession().sortFolders(folders, field, reverse);
			return folders;
		} catch (Throwable error) {
			reportError(error, "Error sorting folder list of size " +
				folders.size() + " by field \"" + field.toString() + "\"");
			return null;
		}
	}

	public String getFolderText(Folder folder) {
		String text = "[";
		if (folder == null)
			text += "null";
		else {
			// print folder class and ID
			text += folder.getClass().getSimpleName() + '@' +
				Long.toString(folder.getFolderId()) + ": ";
			// print folder label
			String label = folder.getLabel();
			if (label == null)
				text += "Label null";
			else text += "Label \"" + label + "\"";
		}
		text += "]";
		return text;
	}
}
