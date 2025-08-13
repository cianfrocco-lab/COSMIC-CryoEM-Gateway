package org.ngbw.web.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.ParameterAware;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.apache.struts2.interceptor.SessionAware;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.tool.Tool;
import org.ngbw.sdk.jobs.UsageLimit;
import org.ngbw.web.controllers.FolderController;
import org.ngbw.web.model.impl.ToolComparator;
import org.ngbw.sdk.core.shared.UserRole;
import com.opensymphony.xwork2.ActionSupport;

/**
 * Base Action class for the NGBW web application.  Implements common
 * information and functionality for all NGBW actions.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class NgbwSupport extends ActionSupport
implements ParameterAware, SessionAware, ServletRequestAware, ServletResponseAware
{
	/*================================================================
	 * Constants
	 *================================================================*/
	// session attribute key constants
	public static final String WORKBENCH_SESSION = "workbenchSession";
	public static final String TARGET = "target";
	public static final String TAB = "tab";
	public static final String TOOL_TAB = "toolTab";
	public static final String DISPLAYED_TOOL = "displayedTool";
	public static final String FOLDERS = "folders";
	public static final String CURRENT_FOLDER = "currentFolder";
	public static final String EXPANDED_FOLDERS = "expandedFolders";
	public static final String CPU_HOURS = "cpu_hours";
	public static final String IPLANT_USER = "iplant_user";
	public static final String SESSION_ID = "sessionId";
	public static final String SESSION_ERROR_MESSAGE = "sessionErrorMsg";
	public static final String USER_LOGGED = "userLogged";
	public static final String START_NEW_TASK = "startNewTask";
	public static final String UPLOAD_APPLET_ERROR = "uploadAppletError";
	public static final String MONTHLY = "monthly";
    public static final String USER_AGENT = "User-Agent";
	// result constants
	public static final String HOME = "home";
	public static final String LIST = "list";
	public static final String LIST_ERROR = "list_error";
	public static final String DISPLAY = "display";
	public static final String IPLANT_REGISTER = "iplant_register";
	public static final String IPLANT_LOGIN = "iplant_login";
	public static final String IPLANT_LOGOUT = "iplant_logout";

	// maximum length of displayed text items
	public static final int TEXT_LENGTH = 400;

	// tools we don't want to see
	public static final String POVRAY = "POVRAY";
	public static final String TMALIGN = "TMALIGN";

	// number of max BOM tyes in a file
	public static final int MAX_BOM_SIZE=4;

	public static boolean iplant = false;
	public static boolean isConfigured = false;

	// all properties in workbench.properties.
	public static Properties wbProperties = Workbench.getInstance().getProperties();

	// automatic email properties
	public static final String EMAIL_HOST = wbProperties.getProperty("email.smtpServer");
	public static final String EMAIL_SENDER = wbProperties.getProperty("email.adminAddr");
	public static final String EMAIL_SERVICE_ADDR = wbProperties.getProperty("email.serviceAddr");

	public static final long DATA_SIZE_MAX = Long.parseLong
		( wbProperties.getProperty ( "user.data.size.max" ) );
	public static final long DATA_SIZE_WARN = Long.parseLong
			( wbProperties.getProperty ( "user.data.size.warn" ) );
	public String staticSite = wbProperties.getProperty("build.portal.staticUrl");
	public String trackerUrl = wbProperties.getProperty("build.portal.trackerUrl");
	public String portalAppName = wbProperties.getProperty("build.portal.appName");
	private String suResetFrequency = wbProperties.getProperty("accounting.period.frequency");



	/*================================================================
	 * Properties
	 *================================================================*/
	// logger
	private static final Logger logger = Logger.getLogger(NgbwSupport.class.getName());

	// scope-specific attribute maps
	@SuppressWarnings("rawtypes")
	private Map parameters;
	@SuppressWarnings("rawtypes")
	private Map session;

	// controller
	private FolderController controller;

	protected HttpServletRequest request;
	protected HttpServletResponse response;

	// data size conversions
	private static final long KB = 1024L;
	private static final long MB = 1048576L;
	private static final long GB = 1073741824L;


	synchronized private static void init()
	{
		if (!isConfigured)
		{
			String tmp = getInitParameter("iplant.enabled");
			if (tmp != null && tmp.trim().equals("1"))
			{
				iplant = true;
			}
			isConfigured = true;
			logger.debug("iplant.enabled = " + iplant);
		}
	}

	public boolean allowGuests()
	{
		String tmp = wbProperties.getProperty("allowGuests");
		if (tmp == null || (tmp.compareToIgnoreCase("true") == 0))
		{
			logger.debug("guests are allowed");
			return true;
		}
		logger.debug("guests are not allowed");
		return false;
	}

	public boolean iplantEnabled()
	{
		//logger.debug("iplantEnabled() called");
		init();
		return iplant;
	}

	public Boolean loggedInViaIPlant()
	{
		if (iplantEnabled())
		{
			String iplantUser= (String)getSessionAttribute(IPLANT_USER);
			if (iplantUser != null && iplantUser.equals("1"))
			{
				return true;
				/*
				String number = (String)Workbench.getInstance().getProperties().get("iplant.charge.number");
				//logger.debug("iplant user logged in, returning charge number " + number);
				return number;
				*/
			}
		}
		return false;
	}



	/*================================================================
	 * Action methods
	 *================================================================*/



	public String input() {
		return INPUT;
	}

	/*
		We either get here when the user is on the task creation form and presses the select
		tool button or tab OR we get here when the user chooses the Toolkit tab.  In the latter
		case we don't have a parameter in the url and if there is a current task we want to
		clear it so the user can start creating  on a new one.
	*/

	public String changeToolTab()
	{
		// get selected tab from request param, if present
		String tab = getRequestParameter(TAB);
		if (tab != null) {
			setToolTab(tab);
			setSessionAttribute(START_NEW_TASK, "false");
		} else
		{
			setSessionAttribute(START_NEW_TASK, "true");
		}
		return LIST;
	}

	/*================================================================
	 * Public property accessor methods
	 *================================================================*/
	//----------------------------------------------------------------
	// Parameter and Session properties --
	// These are methods implementing the ParameterAware
	// and SessionAware interfaces.
	//----------------------------------------------------------------
	@SuppressWarnings("rawtypes")
	public Map getParameters() {
		return parameters;
	}

	@SuppressWarnings("rawtypes")
	public void setParameters(Map parameters) {
		this.parameters = parameters;
	}

	@SuppressWarnings("rawtypes")
	public Map getSession() {
		return session;
	}

	@SuppressWarnings("rawtypes")
	public void setSession(Map session) {
		this.session = session;
	}

	public HttpServletRequest getServletRequest() {
		return request;
	}

	public void setServletRequest(HttpServletRequest req){
		this.request = req;
	}

	public HttpServletResponse getServletResponse() {
		return response;
	}

	public void setServletResponse(HttpServletResponse resp) {
		this.response = resp;
	}

	public String getClientIp()
	{
		return getServletRequest().getRemoteAddr();
	}

	public String getClientUserAgent()
	{
		return getServletRequest().getHeader(USER_AGENT);
	}

    public boolean isAdministrator ()
    {
        boolean isAdmin = false;

        WorkbenchSession wbSession = getWorkbenchSession();

        try
        {
            User user = (null == wbSession)? null : wbSession.getUser();
            UserRole role = (null == user)? null : user.getRole();
            isAdmin = role != null && role.equals(UserRole.ADMIN);
        }
        catch ( Throwable t )
        {
            isAdmin = false;
            logger.error(t.getMessage(), t);
        }

        return isAdmin;
    }

	//----------------------------------------------------------------
	// View properties --
	// These are methods to retrieve information that is displayed on
	// many different pages of the application's user interface.
	//----------------------------------------------------------------
	public User getAuthenticatedUser() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else return controller.getAuthenticatedUser();
	}

	public String getAuthenticatedUsername() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else return controller.getAuthenticatedUsername();
	}

	public String getAuthenticatedPassword() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else return controller.getAuthenticatedPassword();
	}

	public boolean isAuthenticated() {
		FolderController controller = getFolderController();
		if (controller == null)
			return false;
		else return controller.isAuthenticated();
	}

	public boolean isRegistered() {
		FolderController controller = getFolderController();
		if (controller == null)
			return false;
		else return controller.isRegistered();
	}

	@SuppressWarnings("unchecked")
	public List<Folder> getFolders() {
		List<Folder> folders = (List<Folder>)getSessionAttribute(FOLDERS);
		if (folders == null)
			folders = refreshFolders();
		return folders;
	}

	public boolean needProfileInfo() {
		FolderController controller = getFolderController();
		if (controller == null)
			return false;
		else return controller.needProfileInfo();
	}

	public void setFolders(List<Folder> folders) {
		if (folders == null)
			clearFolders();
		else setSessionAttribute(FOLDERS, folders);
	}

	public void clearFolders() {
		clearSessionAttribute(FOLDERS);
	}

	public boolean hasFolders() {
		List<Folder> folders = getFolders();
		return (folders != null && folders.size() > 0);
	}

	public List<Folder> getAllFolders() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else return controller.getAllFolders();
	}

	public List<Folder> getSubfolders(Folder folder) {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else return controller.getSubfolders(folder);
	}

	public Folder getCurrentFolder() {
		Folder folder = (Folder)getSessionAttribute(CURRENT_FOLDER);
		if (folder == null)
			folder = refreshCurrentFolder();
		return folder;
	}

	public void setCurrentFolder(Folder folder) {
		if (folder == null)
			clearCurrentFolder();
		else {
			setSessionAttribute(CURRENT_FOLDER, folder);
			setExpanded(folder, true);
			FolderController controller = getFolderController();
			if (controller != null)
				controller.setCurrentFolder(folder);
		}
	}

	public void clearCurrentFolder() {
		clearSessionAttribute(CURRENT_FOLDER);
		FolderController controller = getFolderController();
		if (controller != null)
			controller.clearCurrentFolder();
	}

	public boolean hasCurrentFolder() {
		return (getCurrentFolder() != null);
	}

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

	public String getCurrentFolderDescription() {
		Folder folder = getCurrentFolder();
		if (folder == null)
			return null;
		else {
			FolderController controller = getFolderController();
			if (controller != null)
				return controller.getDescription(folder);
			else return null;
		}
	}

	public boolean currentFolderHasDescription() {
		String description = getCurrentFolderDescription();
		return (description != null && description.trim().equals("") == false);
	}

	public boolean currentFolderHasParent() {
		Folder folder = getCurrentFolder();
		if (folder == null)
			return false;
		else return (getFolderController().getParentFolder(folder) != null);
	}

	@SuppressWarnings("unchecked")
	public List<Long> getExpandedFolderIds() {
		return (List<Long>)getSessionAttribute(EXPANDED_FOLDERS);
	}

	public void setExpandedFolderIds(List<Long> expandedFolderIds) {
		if (expandedFolderIds == null)
			clearExpandedFolderIds();
		else setSessionAttribute(EXPANDED_FOLDERS, expandedFolderIds);
	}

	public void clearExpandedFolderIds() {
		clearSessionAttribute(EXPANDED_FOLDERS);
	}

	public boolean isExpanded(Folder folder) {
		if (folder == null || folder.isNew())
			return false;
		else {
			Long folderId = folder.getFolderId();
			List<Long> expandedIds = getExpandedFolderIds();
			if (expandedIds == null)
				return false;
			else return expandedIds.contains(folderId);
		}
	}

	public void setExpanded(Folder folder, boolean expanded) {
		if (folder == null || folder.isNew())
			return;
		else {
			Long folderId = folder.getFolderId();
			List<Long> expandedIds = getExpandedFolderIds();
			if (expanded) {
				if (expandedIds == null)
					expandedIds = new Vector<Long>(1);
				expandedIds.add(folderId);
				setExpandedFolderIds(expandedIds);
			} else if (expandedIds != null) {
				expandedIds.remove(folderId);
				setExpandedFolderIds(expandedIds);
			}
		}
	}

	public void toggleExpanded(Folder folder) {
		boolean expanded = isExpanded(folder);
		setExpanded(folder, !expanded);
	}

	public int getDataItemCount(Folder folder) {
		FolderController controller = getFolderController();
		if (controller == null)
			return 0;
		else return controller.getDataItemCount(folder);
	}

	public int getTaskCount(Folder folder) {
		FolderController controller = getFolderController();
		if (controller == null)
			return 0;
		else return controller.getTaskCount(folder);
	}

	public boolean hasMessages() {
		return (hasActionMessages() || hasActionErrors() || hasFieldErrors());
	}

	public String getCurrentDate() {
		return formatDate(new Date());
	}

	//----------------------------------------------------------------
	// Toolkit interface properties
	//----------------------------------------------------------------
	public String getToolTab() {
		return (String)getSessionAttribute(TOOL_TAB);
	}

	public void setToolTab(String toolTab) {
		setSessionAttribute(TOOL_TAB, toolTab);
	}

	public String getDisplayedTool() {
		return (String)getSessionAttribute(DISPLAYED_TOOL);
	}

	public String whyToolNotAllowed(String tool)
	{
		if (tool.equals("RAXML_LIGHT2") && !loggedInViaIPlant())
		{
			return "Only iPlant users can run " + getToolLabel(tool) + ".  Sign up for an iPlant account!";
		}
		return null;
	}

	public void setDisplayedTool(String tool) {
		setSessionAttribute(DISPLAYED_TOOL, tool);
	}

	public List<String> getTools() {
		//TODO: implement pluggable sorting and/or collection type of returned tool collection
		Set<String> tools = getWorkbench().getActiveToolIds();

		// Terri: I just added the processing of an "inactive=true" flag to tool entries in the registry
		// so we should be able to use that to keep these tools from showing up.
		//TODO: come up with a more generic way to exclude bad tools
		if (tools.contains(POVRAY))
			tools.remove(POVRAY);
		if (tools.contains(TMALIGN))
			tools.remove(TMALIGN);
		return listTools(tools);
	}


    public List<String> getToolTypes()
    {
		Set<String> tools = getWorkbench().getActiveToolIds();
        List<String> types = new Vector<String>();

        for ( String tool : tools )
        {
			String type = getConceptLabel ( "ToolType", tool );
            if ( ! types.contains ( type ) )
                types.add ( type );
        }

        return ( types );
    }


	public List<String> getToolsOfType(String toolType) {
		List<String> tools = getTools();
		if (toolType == null || toolType.trim().equals("") || tools == null)
			return tools;
		else {
			List<String> targetTools = new Vector<String>();
			for (String tool: tools) {
				String type = getConceptLabel("ToolType", tool);
				if (type == null || type.equals(tool))
					continue;
				else if (type.contains(toolType))
					targetTools.add(tool);
			}
			if (targetTools.size() < 1)
				return null;
			else return targetTools;
		}
	}

	public List<String> splitFirstColumn(List<String> tools) {
		if (tools == null || tools.size() < 1)
			return null;
		else if (tools.size() == 1)
			return tools;
		else {
			int columnSize = (int)Math.ceil(tools.size() / 2);
			return tools.subList(0, columnSize);
		}
	}

	public List<String> splitSecondColumn(List<String> tools) {
		if (tools == null || tools.size() < 2)
			return null;
		else {
			int columnSize = (int)Math.ceil(tools.size() / 2);
			return tools.subList(columnSize, tools.size());
		}
	}

	public String getToolLabel(String tool) {
		if (tool == null)
			return null;
		else return getConceptLabel("ToolLabel", tool);
	}

	public String getToolVersion(String tool) {
		if (tool == null)
			return null;
		else {
			String version = getConceptLabel("ToolVersion", tool);
			if (version == null || version.equals(tool))
				return null;
			else return version;
		}
	}

	public boolean hasToolVersion(String tool) {
		return (getToolVersion(tool) != null);
	}

	public String getToolDescription(String tool) {
		if (tool == null)
			return null;
		else {
			String description = getConceptLabel("ToolDescription", tool);
			if (description == null || description.equals(tool))
				return null;
			else return description;
		}
	}

	/*================================================================
	 * Internal property accessor methods
	 *================================================================*/
	//----------------------------------------------------------------
	// Workbench properties
	//----------------------------------------------------------------
	protected WorkbenchSession getWorkbenchSession() {
		return (WorkbenchSession)getSessionAttribute(WORKBENCH_SESSION);
	}

	// Can return 0, won't return null.
	public long getCPUHours() {
		try
		{
			long userid = getWorkbenchSession().getUser().getUserId();
			return Statistics.susPerUserThisPeriod(userid);
		}
		catch(Exception e)
		{
			reportError(e, "Error retrieving cpu hours for user" );
			return 0;
		}
	}

	public long getXSEDELimit() {
		try
		{
           return UsageLimit.getInstance().getXSEDELimit(getWorkbenchSession().getUser(), null);
		}
		catch(Exception e)
		{
			reportError(e, "Error retrieving SU limit for user" );
			return 0;
		}
	}

	public String getSuResetFrequency() {
       if (suResetFrequency != null)
           return suResetFrequency;
       else
           return "";
	}

	public String getSuAllocationExpireTime() {
       if (suResetFrequency != null && suResetFrequency.equalsIgnoreCase(MONTHLY))
       {
           Calendar calendar = Calendar.getInstance();
           calendar.setTime(new Date());
           int day = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
           calendar.set(Calendar.DAY_OF_MONTH, day);
           calendar.set(Calendar.MINUTE, 59);
           calendar.set(Calendar.HOUR_OF_DAY, 23);
	       SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yy HH:mm");
           return dateFormat.format(calendar.getTime());
	   }
       else
           return "";
    }
	/**
	 * Return the max user data size allowed in different units.  Note: kb =
	 * 1024, not 1000.
	 * @param unit - "b" = return in bytes; "kb" = kilobytes;
	 * 		"mb" = megabytes; "gb" = gigabytes.
	 * @author - Mona Wong
	 */
	public long getMaxDataSize ( String unit )
	{
		Long size = 0L;

		switch ( unit )
		{
			case "b":
				size = DATA_SIZE_MAX;
				break;

			case "kb":
				size = DATA_SIZE_MAX / KB;
				break;

			case "mb":
				size = DATA_SIZE_MAX / MB;
				break;

			case "gb":
				size = DATA_SIZE_MAX / GB;
				break;
		}

		return ( size );
	}


	/*
	 * Get the user's filesystem data size.  Can return 0, won't return null.
	 * Note: kb = 1024, not 1000.
	 * @param mode - "b" = return in bytes; "kb" = kilobytes;
	 * 		"mb" = megabytes; "gb" = gigabytes
	 * @author - Mona Wong
	 */
	public Long getUserDataSize ( String mode )
	{
		long size = 0L;

		try
		{
			//size = getWorkbenchSession().getUser().getDataSize();
			//size = getWorkbenchSession().getUser().getDataSizeDU();
			size = getWorkbenchSession().getUser().queryDataSizeDU();

			switch ( mode )
			{
				case "b":
					break;

				case "kb":
					size = size / KB;
					break;

				case "mb":
					size = size / MB;
					break;

				case "gb":
					size = size / GB;
					break;
			}
		}
		catch ( Exception e )
		{
			reportError(e, "Error retrieving data size for user" );
		}

		return ( size );
	}


	public void setWorkbenchSession(WorkbenchSession session) {
		setSessionAttribute(WORKBENCH_SESSION, session);
	}

	protected Workbench getWorkbench() {
		WorkbenchSession session = getWorkbenchSession();
		try {
			if (session == null)
				return Workbench.getInstance();
			else return session.getWorkbench();
		} catch (Throwable error) {
			reportError(error, "Error retrieving Workbench");
			return null;
		}
	}

	//----------------------------------------------------------------
	// Internal Parameter and Session properties
	//----------------------------------------------------------------
	@SuppressWarnings("rawtypes")
	protected Object getParameter(Object parameter) {
		if (parameter == null)
			return null;
		else {
			Map parameters = getParameters();
			if (parameters == null)
				return null;
			else return parameters.get(parameter);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected void setParameter(Object parameter, Object value) {
		if (parameter == null)
			return;
		else {
			Map parameters = getParameters();
			if (parameters == null)
				return;
			else if (value == null)
				parameters.remove(parameter);
			else parameters.put(parameter, value);
		}
	}

	public String getRequestParameter(String parameter) {
		String[] value = null;
		try {
			value = (String[])getParameter(parameter);
		} catch (ClassCastException error) {
			return null;
		}
		if (value == null || value.length < 1)
			return null;
		else return value[0];
	}

	@SuppressWarnings("rawtypes")
	public Object getSessionAttribute(Object attribute) {
		if (attribute == null)
			return null;
		else {
			Map session = getSession();
			if (session == null)
				return null;
			else return session.get(attribute);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected void setSessionAttribute(Object attribute, Object value) {
		if (value == null)
			clearSessionAttribute(attribute);
		else if (attribute == null)
			return;
		else {
			Map session = getSession();
			if (session == null)
				return;
			else session.put(attribute, value);
		}
	}

	@SuppressWarnings("rawtypes")
	public void clearSessionAttribute(Object attribute) {
		if (attribute == null)
			return;
		else {
			Map session = getSession();
			if (session == null)
				return;
			else session.remove(attribute);
		}
	}

	//----------------------------------------------------------------
	// Internal folder properties
	//----------------------------------------------------------------
	protected FolderController getFolderController() {
		WorkbenchSession session = getWorkbenchSession();
		if (session == null) {
			//debug("workbench session is null");
			controller = null;
		} else if (controller == null) {
			//debug("workbench session is NOT null");
			controller = new FolderController(session);
		}
		if (controller == null)
		{
			//debug("but new FolderController is null");
		}
		return controller;
	}

	protected List<Folder> refreshFolders() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else {
			List<Folder> folders = controller.getFolders();
			setSessionAttribute(FOLDERS, folders);
			return folders;
		}
	}

	public Folder refreshCurrentFolder() {
		FolderController controller = getFolderController();
		if (controller == null)
			return null;
		else {
			Folder folder = controller.getCurrentFolder();
			setSessionAttribute(CURRENT_FOLDER, folder);
			setExpanded(folder, true);
			Folder parentFolder = controller.getParentFolder(folder);
			if (parentFolder != null)
				setExpanded(parentFolder, true);
			return folder;
		}
	}

	//----------------------------------------------------------------
	// Internal tool list properties
	//----------------------------------------------------------------
	//TODO: make this generic
	protected List<String> listTools(Set<String> toolSet) {
		if (toolSet == null)
			return null;
		else {
			String[] tools = toolSet.toArray(new String[toolSet.size()]);
			Arrays.sort(tools, new ToolComparator());
			return Arrays.asList(tools);
		}
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/
	//----------------------------------------------------------------
	// Utility methods --
	// These are methods that might be used by many different actions.
	//----------------------------------------------------------------
	protected boolean sendEmail(String recipient, String subject, String body) {
		Properties properties = new Properties();
		properties.put("mail.smtp.host", EMAIL_HOST);
		Session session = Session.getDefaultInstance(properties, null);
	    Message message = new MimeMessage(session);
	    try {
		    message.setFrom(new InternetAddress(EMAIL_SENDER));
		    message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
		    message.setSubject(subject);
		    message.setText(body);
		    message.setSentDate(new Date());
		    Transport.send(message);
		    return true;
	    } catch (MessagingException error) {
			reportError(error, "Error sending email to " + recipient);
			return false;
	    }
	}

	/*
		Terri: We are seeing a problem where struts treats the conceptType
		as an OGNL expression and tries to evaluate it, so for example, if conceptType is
		"ToolLabel" and we have an object on the ognl value stack with a getToolLabel()
		method, getText() tires to call it, and it will undoubtedly call getConceptLabel()
		and we end up with such a deep stack that we get a StackOverflowError or a jvm
		crash.  It seems to mostly happen when we don't have a mapping for the
		conceptType in a resource file, but really it shouldn't happen at all, the concept
		type should be treated as a literal since we're always passing in a quoted string.

		To work around this, I'm changing all the concept types that we pass to getText(),
		by inserting "_CL".  So we'll have things like "ToolType_CL.BEAST" instead of
		"ToolType.BEAST".  Since we don't have methods like getToolType_CL() this should
		solve the problem.  I'm making the corresponding changes in the resource bundles:
		src/main/resources/tools.properties and src/main/resources/org/ngbw/web/actions/package.properties.
		Since tools.properties is generated, it's the freemarker template I actually have
		to change: cipres-portal/src/main/codeGeneration/FreemarkerTemplate/pise2property.ftl
	*/
	protected String getConceptLabel(String conceptType, String concept) {
		String text = getText(conceptType + "_CL" + "." + concept);
		if (text.startsWith(conceptType)) {
			//debug(conceptType + " \"" + concept + "\" has no webapp resource mapping.");
			return concept;
		} else return text;
	}


	/**
	 * Convenent function to convert given value to the best display (largest)
	 * size along with appropriate unit.  Note: kb = 1024, not 1000.
	 * @param value - value to convert
	 * @return - eg 600 bytes, 15 KB, 82 MB, 700 GB
	 */
	public String getDataSize4Display ( Long value )
	{
		long result = value / GB;
		String answer = null;

		if ( result > 0 )
			answer = String.valueOf ( result ) + " GB";
		else
		{
			result = value / MB;

			if ( result > 0 )
				answer = String.valueOf ( result ) + " MB";

			else
			{
				result = value / KB;

				if ( result > 0 )
					answer = String.valueOf ( result ) + " KB";

				else
					answer = String.valueOf ( result ) + " bytes";
			}
		}
		return ( answer );
	}


	protected String truncateText(String text) {
		if (text == null)
			return null;
		else if (text.length() > TEXT_LENGTH)
			text = text.substring(0, (TEXT_LENGTH - 3)) + "...";
		return text;
	}

	protected String formatDate(Date date) {
		//TODO: add support for locales, possibly external date format configuration
		SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yy, HH:mm");
		return dateFormat.format(date);
	}

	protected String pluralize(String singular, int count) {
		if (singular == null)
			return null;
		else if (count != 1) {
			if (singular.charAt(singular.length()-1) == 's')
				return singular + "es";
			else return singular + "s";
		} else return singular;
	}

	// method handle line terminators on different system
	// Windows: CRLF pair (\r\n or \0D\0A or Control-M Control-J)
	// Unix:    LF character only (\n)
	// MAC: 	CR character only (\r or \0D or Control-M)
	// Solution: replace with (\n or \0A)
	protected String stripCarriageReturns(String input) {
		String data = input;
		if (input == null)
			return null;
		// if any of the 4 pattern matches, replace with Unix LF
		Pattern linebreak = Pattern.compile("(\r\n|\r|\n|\n\r)");
		Matcher m = linebreak.matcher(input);
		if (m.find())
		{
			data = m.replaceAll("\n");
		}
		return data;
	}

	protected void invalidateSession() {
		if (session != null)
			session.clear();
	}

	protected void print(String message) {
		System.out.println("\t" + message);
	}

	protected void info(String message) {
		if (logger.isInfoEnabled()) {
			logger.info(getUsernameString() + message);
		}
	}

	public void debug(String message) {
		if (logger.isDebugEnabled()) {
			logger.debug(getUsernameString() + message);
		}
	}

	protected void error(String message, Throwable t)
	{
		logger.error(message, t);
	}


	protected void debug(String message, Throwable error) {
		if (logger.isDebugEnabled()) {
			logger.debug(getUsernameString() + message, error);
		}
	}

	protected void reportUserMessage(String message) {
		addActionMessage(message);
		info(message);
	}

	public void reportUserError(String message) {
		addActionError(message);
		setSessionAttribute ( SESSION_ERROR_MESSAGE, message );
		info(message);
	}

	protected void reportUserError(Throwable error, String message) {
		addActionError(message);
		reportError(error, message);
	}

	protected void reportCaughtError(Throwable error, String message) {
		debug(message + ": " + error.toString());
	}

	protected void reportError(Throwable error, String message) {
		debug(message + ": " + error.toString(), error);
	}

	//----------------------------------------------------------------
	// Private methods
	//----------------------------------------------------------------
	String getUsernameString() {
		WorkbenchSession session = getWorkbenchSession();
		String username = null;
		if (session != null) try {
			username = session.getUsername();
		} catch (Throwable error) {}
		if (username != null)
			username += " - ";
		else username = "";
		return username;
	}

	//----------------------------------------------------------------
	// Tree draw methods
	//----------------------------------------------------------------
	public static String getHTTPSessionId()
	{
		return ServletActionContext.getRequest().getSession().getId();
	}

	public static String getInitParameter(String p)
	{
		return ServletActionContext.getServletContext().getInitParameter(p);
	}

	//----------------------------------------------------------------
	// method to determine the file encoding type: needed for non-ascii files
	//----------------------------------------------------------------
	public String detectEncoding(InputStream in) throws IOException {
        byte[] bom = new byte[MAX_BOM_SIZE]; // byte order mark
        final int n = in.read(bom, 0, bom.length);
        int unread = 0;
        String encoding = null;

        if ((bom[0] == (byte)0xEF) && (bom[1] == (byte)0xBB) && (bom[2] == (byte)0xBF)) {
            encoding = "UTF-8";
            unread = n - 3;
        }
        else if ((bom[0] == (byte)0xFE) && (bom[1] == (byte)0xFF)) {
            encoding = "UTF-16BE";
            unread = n - 2;
        }
        else if ((bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE)) {
            encoding = "UTF-16LE";
            unread = n - 2;
        }
        else if ((bom[0] == (byte)0x00) && (bom[1] == (byte)0x00) && (bom[2] == (byte)0xFE) && (bom[3] == (byte)0xFF)) {
            encoding = "UTF-32BE";
            unread = n - 4;
        }
        else if ((bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE) && (bom[2] == (byte)0x00) && (bom[3] == (byte)0x00)) {
            encoding = "UTF-32LE";
            unread = n - 4;
        }
        else {
            //unicode BOM mark not found, unread all bytes
            encoding = "ASCII";
            unread = n;
        }

        if (unread > 0 && in instanceof PushbackInputStream) {
            PushbackInputStream pb = (PushbackInputStream) in;
            pb.unread(bom, (n - unread), unread);
        }
        return encoding;
    }
    //-------------------------------------------------------------------------
    // this method reads a File object based on its encoding type, and return
    // the content in String object
    //------------------------------------------------------------------------
    public String getEncodedData(File file)
	{
		StringBuffer data = new StringBuffer();
		try
		{
    	   	FileInputStream fstream = new FileInputStream(file);
    		PushbackInputStream pb = new PushbackInputStream(fstream,MAX_BOM_SIZE);
		    String encoding = detectEncoding(pb);
    		InputStreamReader is = new InputStreamReader(pb,encoding);
    		BufferedReader br = new BufferedReader(is);
    		//read file line by line
    		String strLine = null;
    		while ((strLine = br.readLine()) != null)
			{
				data.append(strLine);
				data.append("\n");
    		}
    		//Close the input stream
    		is.close();
    		br.close();
    	}
		catch (Exception error)
		{
			reportError(error, "Error in converting data using encoding");
    	}
		return data.toString();
   }

	public boolean isToolDisabled(String id)
	{
		Workbench wb = Workbench.getInstance();
		Tool tool = wb.getTool(id);
		return tool.disabled() != null;
	}

	public String toolDisabledMessage(String id)
	{
		Workbench wb = Workbench.getInstance();
		Tool tool = wb.getTool(id);
		return tool.disabled();
	}

	@SuppressWarnings("rawtypes")
	public void dumpRequestHeaders(String caller)
	{
		Enumeration e = request.getHeaderNames();
		String header;
		debug(caller + ":  request headers");
		while (e.hasMoreElements())
		{
			header = (String)e.nextElement();
			if (header != null)
			{
				debug(header + " = " + request.getHeader(header));
			}
		}
	}

	public String getRequestHeader(String header)
	{
		return request.getHeader(header);
	}

	public void dumpHeapSize(String text)
	{
		long heapSize = Runtime.getRuntime().totalMemory() / MB;
		long heapMaxSize = Runtime.getRuntime().maxMemory() / MB;
		long heapFreeSize = Runtime.getRuntime().freeMemory() / MB;
		logger.debug(text + ": heapSize = " + heapSize + " Meg, heapMaxSize = " + heapMaxSize +
		" Meg, heapFreeSize = " + heapFreeSize + "Meg, Used = " + (heapSize - heapFreeSize) + " Meg");
	}
}
