package org.ngbw.web.actions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ngbw.sdk.UserAuthenticationException;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.database.Folder;
import org.ngbw.web.controllers.FolderController;
import org.apache.log4j.Logger;


/**
 * Struts action class to process all folder-related requests in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class FolderManager extends SessionManager
{
    /*
     * ================================================================ Constants
	 *================================================================
     */
    private static final Logger logger = Logger.getLogger(FolderManager.class.getName());
    // parameter attribute key constants
    public static final String ID = "id";

    // session attribute key constants
    public static final String WORKING_FOLDER = "workingFolder";
    public static final String PARENT_FOLDER = "parentFolder";

    /*
     * ================================================================ Properties
	 *================================================================
     */
    // cached current folder
    private Folder currentFolder;

    // folder create/edit form bean
    private String label;
    private String description;


    /*
     * ================================================================ Action methods
	 *================================================================
     */
    public String list ()
    {
        clearCurrentFolder();
        clearExpandedFolderIds();
        return LIST;
    }


    public String display ()
    {
        Folder folder = getRequestFolder(ID);
        if (folder != null)
        {
            setCurrentFolder(folder);
        }
        else
        {
            folder = getCurrentFolder();
        }
        if (folder == null)
        {
            reportUserError("You must select a folder to view its details.");
            return LIST;
        }
        else
        {
            return DISPLAY;
        }
    }


    public String create ()
    {
        if (isRegistered())
        {
            clearWorkingFolder();
            Folder folder = getRequestFolder(PARENT_FOLDER);
            if (folder != null)
            {
                setParentFolder(folder);
            }
            else
            {
                clearParentFolder();
            }
            return INPUT;
        }
        else
        {
            reportUserError("You must register to create a folder.");
            return back();
        }
    }


    public String edit ()
    {
        if (isRegistered())
        {
            Folder folder = getRequestFolder(ID);
            if (folder == null)
            {
                folder = getCurrentFolder();
            }
            if (folder == null)
            {
                reportUserError("You must select a folder to edit it.");
                return LIST;
            }
            else
            {
                clearParentFolder();
                setWorkingFolder(folder);
                return INPUT;
            }
        }
        else
        {
            reportUserError("You must register to edit a folder.");
            return back();
        }
    }


    public String save ()
    {
        if (validateFolder())
        {
            Folder folder = getWorkingFolder();
            FolderController controller = getFolderController();
            // if there is no working folder, we are trying to create a new folder
            if (folder == null)
            {
                try
                {
					// https://howtodoinjava.com/java/regex/regex-alphanumeric-characters/
                    String regex = "^[-_a-zA-Z0-9 ]+$";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(getLabel());
                    if (matcher.matches()) {
                        folder = controller.createFolder(getLabel(), getDescription(),
                                                     getParentFolder());
                    } else {
                        reportUserError("Folder \"" + getLabel() + "\" contains bad characters.");
                        return "error";
                    }
                    if (folder != null)
                    {
                        reportUserMessage("Folder \"" + folder.getLabel() + "\" successfully created.");
                        setCurrentFolder(folder);
                        refreshFolders();
                    }
                    else
                    {
                        reportUserError("Folder \"" + getLabel() + "\" could not be created.");
                    }
                }
                catch ( UserAuthenticationException error )
                {
                    reportUserError(error.getMessage());
                }
            }
            // otherwise, we are trying to edit an existing folder
            else
            {
                try
                {
                    String oldLabel = folder.getLabel();
                    folder = controller.editFolder(folder, getLabel(), getDescription());
                    if (folder != null)
                    {
                        reportUserMessage("Folder \"" + folder.getLabel() + "\" successfully edited.");
                        setCurrentFolder(folder);
                        refreshFolders();
                    }
                    else
                    {
                        reportUserError("Folder \"" + oldLabel + "\" could not be edited.");
                    }
                }
                catch ( UserAuthenticationException error )
                {
                    reportUserError(error.getMessage());
                }
            }
            clearWorkingFolder();
            return back();
        }
        else
        {
            return INPUT;
        }
    }


    public String cancel ()
    {
        // discard input and return
        reportUserMessage("Folder not saved.");
        clearWorkingFolder();
        return back();
    }


    public String delete ()
    {
        Folder folder = getRequestFolder(ID);
        if (folder == null)
        {
            folder = getCurrentFolder();
        }
        if (folder == null)
        {
            reportUserError("You must select a folder to delete it.");
            return LIST;
        }
        else
        {
            String folderLabel = folder.getLabel();
            boolean isCurrent = isCurrentFolder(folder);
            try
            {
                if (getFolderController().deleteFolder(folder))
                {
                    if (isCurrent)
                    {
                        clearCurrentFolder();
                    }
                    reportUserMessage("Folder \"" + folderLabel + "\" successfully deleted.");
                    refreshFolders();
                    if (isCurrent)
                    {
                        return LIST;
                    }
                    else
                    {
                        return back();
                    }
                }
                else
                {
                    reportUserError("Folder \"" + folderLabel + "\" could not be deleted.");
                    return back();
                }
            }
            catch ( UserAuthenticationException error )
            {
                reportUserError(error.getMessage());
                return back();
            }
        }
    }


    public String importBwbData ()
    {
        if (validateLoginInputs())
        {
            try
            {
                int imported = getFolderController().importBwbData(getUsername(), getCurrentPassword());
                refreshFolders();
                String count;
                if (imported > 0)
                {
                    count = imported + " data item";
                    if (imported != 1)
                    {
                        count += "s were successfully ";
                    }
                    else
                    {
                        count += " was successfully ";
                    }
                }
                else
                {
                    count = "No data items were ";
                }
                addActionMessage(count + "imported from Biology Workbench account \"" + getUsername() + "\".");
                return SUCCESS;
            }
            catch ( WorkbenchException error )
            {
                reportUserError(error.getMessage());
                return SUCCESS;
            }
            catch ( UserAuthenticationException error )
            {
                reportUserError(error.getMessage());
                return INPUT;
            }
        }
        else
        {
            return INPUT;
        }
    }


    /*
     * ================================================================ Folder display page property
     * accessor methods
	 *================================================================
     */
    @Override
    public Folder getCurrentFolder ()
    {
        // first try the folder stored in the action
        if (currentFolder != null)
        {
            return currentFolder;
        }
        // if not found, retrieve it from the session
        else
        {
            currentFolder = super.getCurrentFolder();
            return currentFolder;
        }
    }


    @Override
    public void setCurrentFolder ( Folder folder )
    {
        if (folder == null)
        {
            clearCurrentFolder();
        }
        else
        {
            super.setCurrentFolder(folder);
            currentFolder = folder;
        }
    }


    @Override
    public void clearCurrentFolder ()
    {
        super.clearCurrentFolder();
        currentFolder = null;
    }


    /*
     * ================================================================ Form property accessor
     * methods
	 *================================================================
     */
    public String getLabel ()
    {
        return label;
    }


    public void setLabel ( String label )
    {
        this.label = label;
    }


    public String getDescription ()
    {
        return description;
    }


    public void setDescription ( String description )
    {
        this.description = description;
    }


    /*
     * ================================================================ Internal property accessor
     * methods
	 *================================================================
     */
    @Override
    protected FolderController getFolderController ()
    {
        FolderController controller = super.getFolderController();
        if (controller == null)
        {
            throw new RuntimeException("A valid WorkbenchSession should "
                    + "always be present throughout the lifespan of this action.");
        }
        else
        {
            return controller;
        }
    }


    protected Folder getWorkingFolder ()
    {
        return (Folder) getSessionAttribute(WORKING_FOLDER);
    }


    protected void setWorkingFolder ( Folder folder )
    {
        if (folder == null)
        {
            clearWorkingFolder();
        }
        else
        {
            setSessionAttribute(WORKING_FOLDER, folder);
            setLabel(folder.getLabel());
            setDescription(getFolderController().getDescription(folder));
        }
    }


    protected void clearWorkingFolder ()
    {
        clearSessionAttribute(WORKING_FOLDER);
        setLabel(null);
        setDescription(null);
    }


    protected Folder getParentFolder ()
    {
        return (Folder) getSessionAttribute(PARENT_FOLDER);
    }


    protected void setParentFolder ( Folder folder )
    {
        if (folder == null)
        {
            clearParentFolder();
        }
        else
        {
            setSessionAttribute(PARENT_FOLDER, folder);
        }
    }


    protected void clearParentFolder ()
    {
        clearSessionAttribute(PARENT_FOLDER);
    }


    /*
     * ================================================================ Convenience methods
	 *================================================================
     */
    protected Folder getRequestFolder ( String parameter )
    {
        String folderId = getRequestParameter(parameter);
        if (folderId == null)
        {
            return null;
        }
        else
        {
            try
            {
                return getFolderController().getFolder(Long.parseLong(folderId));
            }
            catch ( NumberFormatException error )
            {
                return null;
            }
        }
    }


    protected boolean validateFolder ()
    {
        String label = getLabel();
        if (label == null || label.equals(""))
        {
            addFieldError("label", "Label is required.");
        }
        String description = getDescription();
        if (description != null && description.length() > 100)
        {
            addFieldError("description", "Description cannot be more than 100 characters in length.");
        }
        if (hasFieldErrors())
        {
            return false;
        }
        else
        {
            return true;
        }
    }


    private String back ()
    {
        return (getCurrentFolder() != null)? DISPLAY : LIST;
    }

}
