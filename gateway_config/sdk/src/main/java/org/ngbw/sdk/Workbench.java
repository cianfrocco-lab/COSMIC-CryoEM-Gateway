/*
 * Workbench.java
 */
package org.ngbw.sdk;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.api.conversion.ConversionService;
import org.ngbw.sdk.api.conversion.RecordFilter;
import org.ngbw.sdk.api.core.CoreRegistry;
import org.ngbw.sdk.api.core.GenericDataRecordCollection;
import org.ngbw.sdk.api.core.SourceDocumentTransformer;
import org.ngbw.sdk.api.data.SimpleSearchMetaQuery;
import org.ngbw.sdk.common.util.Resource;
import org.ngbw.sdk.common.util.ResourceNotFoundException;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.common.util.ValidationResult;
import org.ngbw.sdk.core.configuration.Configuration;
import org.ngbw.sdk.core.configuration.ServiceFactory;
import org.ngbw.sdk.core.shared.IndexedDataRecord;
import org.ngbw.sdk.core.shared.SourceDocumentBean;
import org.ngbw.sdk.core.shared.SourceDocumentType;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.Dataset;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.core.types.RecordFieldType;
import org.ngbw.sdk.core.types.RecordType;
import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.ConnectionSource;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.Group;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.tool.Tool;
import org.ngbw.sdk.tool.TaskInitiate;
import org.ngbw.sdk.tool.DisabledResourceException;
import org.ngbw.sdk.common.util.ProcessUtils;
import org.ngbw.sdk.tool.RegistrationManager;

/**
 * This class is so far the main entry point into to the NGBW SDK. <br />
 * <p>
 * The <code>Workbench</code> spawns a <code>WorkbenchSession</code> for User bound interaction. The
 * Workbench keeps track of all spawned WorkbenchSessions and secures that each user interacts with
 * only one WorkbenchSession. The Workbench is also responsible for suspending WorkbenchSessions to
 * disk and resume suspended WorkbenchSessions.
 *
 * @author Roland H. Niedner
 * @author Paul Hoover
 *
 */
public class Workbench
{
    private static Workbench SINGLETON;
    
    private static final Log logger = LogFactory.getLog(Workbench.class);
    
    private final ServiceFactory serviceFactory;
    
    private final ConcurrentHashMap<String, WorkbenchSession> activeSessions = new ConcurrentHashMap<String, WorkbenchSession>();
    
    private Properties properties;


    /**
     * @return Workbench
     */
    public static synchronized Workbench 
    getInstance ()
    {
        if (SINGLETON == null)
        {
            SINGLETON = new Workbench();
        }
        
        return SINGLETON;
    }


    /**
     * @param cfg
     *
     * @return Workbench
     */
    public static synchronized Workbench 
    getInstance ( Resource cfg )
    {
        if (SINGLETON != null)
        {
            throw new WorkbenchException(
                    "A workbench instance already exists. Use getInstance() for follow up calls!");
        }
        
        SINGLETON = new Workbench(cfg);
        return SINGLETON;
    }


    /**
     * @param serviceFactory
     *
     * @return Workbench
     */
    public static synchronized Workbench 
    getInstance ( ServiceFactory serviceFactory )
    {
        if (SINGLETON != null)
        {
            throw new WorkbenchException("A workbench instance already exists. Use getInstance() for follow up calls!");
        }
        
        SINGLETON = new Workbench(serviceFactory);
        return SINGLETON;
    }


    protected Workbench ()
    {
        this(new Configuration().configure().buildServiceFactory());
    }


    protected Workbench ( Resource cfg )
    {
        this(new Configuration().configure(cfg).buildServiceFactory());
    }


    protected Workbench ( ServiceFactory factory )
    {
        try
        {
            Resource pr = Resource.getResource("workbench.properties");
            properties = pr.getProperties();

        }
        catch ( ResourceNotFoundException e )
        {
            properties = new Properties();
        }

        serviceFactory = factory;

        try
        {
            ConnectionManager.setConnectionSource();
            String hostname = ProcessUtils.getMyHostname();
            long pid = ProcessUtils.getMyPid();
            properties.setProperty("hostname", hostname);
            properties.setProperty("pid", String.valueOf(pid));
        }
        catch ( Exception err )
        {
            logger.error("", err);
            throw new WorkbenchException(err);
        }
    }


    /**
     * Get properties loaded from workbench.properties on the classpath. Should be treated as
     * read-only. Whitespace has been trimmed.
     * @return 
     */
    public Properties getProperties ()
    {
        return this.properties;
    }


    @Override
    protected void finalize ()
    {
        try
        {
            ConnectionSource connSource = ConnectionManager.getConnectionSource();

            if (connSource != null)
            {
                connSource.close();
            }
        }
        catch ( SQLException sqlErr )
        {
            logger.error("Caught an exception during finalization", sqlErr);
        }
    }


    /*
     * Workbench Basic Methods
     */
    /**
     * Method returns the ServiceFactory which provides access to all module controllers of the
     * workbench.
     *
     * @return serviceFactory
     */
    public ServiceFactory getServiceFactory ()
    {
        return serviceFactory;
    }
    
    
    public WorkbenchSession getSessionByUserEmail ( String email, String password ) throws UserAuthenticationException, IOException, SQLException 
    {
        User user = User.findUserByEmail(email);
        
        if (user == null) 
        {
            throw new UserAuthenticationException("User does not exist!");
        }
        
        return getSession(user.getUsername(), password);
    }


    /**
     * Method returns an exclusive session for the user if the authentication succeeds.
     *
     * @param username
     * @param password
     * @param passwdInPlain
     *
     * @return session
     *
     * @throws UserAuthenticationException
     * @throws SQLException
     * @throws IOException
     */
    public WorkbenchSession getSession ( String username, String password ) throws UserAuthenticationException, IOException, SQLException
    {
        logger.info("BEGIN: getSession(String, String)::WorkbenchSession");
        
        logger.debug(String.format("Username=[%s]", username));
        
        if (hasActiveSession(username))
        {
            throw new RuntimeException("User " + username + " already has an active session");
        }

        // this may be a case insensitive search
        User user = User.findUser(username);
        
        if (user == null || !user.getUsername().equalsIgnoreCase(username))
        {
            logger.error("User does not exist!");
            throw new UserAuthenticationException("User does not exist!");
        }

        String hash = StringUtils.getMD5HexString(password);

        if (!user.getPassword().equals(hash))
        {
            logger.error("Passwords don't match!");
            throw new UserAuthenticationException("Passwords don't match!");
        }

        WorkbenchSession wbSession = new WorkbenchSession(user, this);

        // make sure the key in our map has the same case as the username in the db.
        activeSessions.put(username, wbSession);
        
        logger.info("END: getSession(String, String)::WorkbenchSession");

        return wbSession;
    }


    public WorkbenchSession getSession ( String username ) throws UserAuthenticationException, IOException, SQLException
    {
        logger.info("BEGIN: getSession(String)::WorkbenchSession");
        
        if (hasActiveSession(username))
        {
            throw new RuntimeException("User " + username + " already has an active session");
        }

        // this may be a case insensitive search
        User user = User.findUser(username);

        if (user == null || !user.getUsername().equals(username))
        {
            throw new UserAuthenticationException("User does not exist!");
        }

        WorkbenchSession workbenchSession = new WorkbenchSession(user, this);

        // make sure the key in our map has the same case as the username in the db.
        activeSessions.put(username, workbenchSession);
        
        logger.info("END: getSession(String)::WorkbenchSession");

        return workbenchSession;
    }


    /**
     * Returns the active <code>WorkbenchSession</code> object for the indicated user. A
     * <code>WorkbenchSession</code> object is only returned if the user actually has an active
     * session, and if the given password matches the user's persisted password.
     *
     * @param username          the name of the user
     * @param encryptedPassword the MD5 hash value for the user's password
     *
     * @return a <code>WorkbenchSession</code> object if the user has an active session, null
     *         otherwise
     *
     * @throws IOException
     * @throws SQLException
     * @throws UserAuthenticationException
     */
    public WorkbenchSession getActiveSession ( String username, String encryptedPassword ) throws IOException, SQLException, UserAuthenticationException
    {
        WorkbenchSession session = activeSessions.get(username);

        if (session == null)
        {
            return null;
        }

        if (!session.getUser().getPassword().equals(encryptedPassword))
        {
            throw new UserAuthenticationException("Passwords don't match!");
        }

        return session;
    }


    /**
     * This is for internal use by the sdk. There are cases where some code is initiated from a
     * workbench session, but the session isn't passed to all methods that need it. Those methods
     * can get the session by using this method.
     */
    public WorkbenchSession retrieveActiveSession ( long userID ) throws Exception
    {
        User user = new User(userID);
        return activeSessions.get(user.getUsername());
    }


    /**
     * A temporary measure to enable interaction with the Sirius applet.
     *
     * @param username
     * @param encryptedPassword
     *
     * @return
     *
     * @throws IOException
     * @throws SQLException
     * @throws UserAuthenticationException
     */
    public WorkbenchSession getSessionForApplet ( String username, String encryptedPassword ) throws IOException, SQLException, UserAuthenticationException
    {
        WorkbenchSession session = getActiveSession(username, encryptedPassword);

        if (session != null)
        {
            return session;
        }

        User user = User.findUser(username);

        if (user == null || !user.getUsername().equals(username))
        {
            throw new UserAuthenticationException("User does not exist!");
        }

        if (!user.getPassword().equals(encryptedPassword))
        {
            throw new UserAuthenticationException("Passwords don't match!");
        }

        session = new WorkbenchSession(user, this);

        activeSessions.put(username, session);

        return session;
    }


    /**
     * Suspends the WorkbenchSession for the user with the submitted username by removing is from
     * the active session map.
     *
     * @param username
     */
    public void suspendSession ( String username )
    {
        activeSessions.remove(username);
    }


    /**
     * Method checks whether the user with the submitted username already has an active
     * WorkbenchSession.
     *
     * @param username
     *
     * @return hasActiveSession
     */
    public boolean hasActiveSession ( String username )
    {
        return getActiveUsers().contains(username);
    }
    
    
    public boolean hasActiveSessionByEmail ( String email ) throws IOException, SQLException 
    {
        User user = User.findUserByEmail(email);
        return (user == null)? false : hasActiveSession(user.getEmail()); 
    }


    /**
     * Method returns a set of all usernames from Users with an active WorkbenchSession.
     *
     * @return activeUsers
     */
    public Set<String> getActiveUsers ()
    {
        return activeSessions.keySet();
    }


    /*
     * MetaController Methods
     */
    /**
     * Method returns whether there is a SourceDocumentTransformer registered for the submitted
     * SourceDocumentType.
     *
     * @param type
     *
     * @return hasTransformer
     */
    public boolean hasTransformer ( SourceDocumentType type, RecordType targetType )
    {
        return serviceFactory.getCoreRegistry().hasTransformerClass(type, targetType);
    }


    /**
     * Method returns the set of RecordTypes that a SourceDocuemnt of the submitted type can be
     * transformed into.
     *
     * @param type
     *
     * @return targetTypes
     */
    public Set<RecordType> getTransformationTargetRecordTypes ( SourceDocumentType type )
    {
        return serviceFactory.getCoreRegistry().getTransformationTargetRecordTypes(type);
    }


    /**
     * Return the SourceDocumentTransformer for the submitted SourceDocument.
     *
     * @param sourceDocument
     *
     * @return transformer
     *
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    public SourceDocumentTransformer getTransformer ( SourceDocument sourceDocument, RecordType targetType ) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        if (sourceDocument == null)
        {
            throw new NullPointerException("SourceDocument cannot be null!");
        }
        if (targetType == null)
        {
            throw new NullPointerException("RecordType cannot be null!");
        }
        Class<SourceDocumentTransformer> transformerClass
                = serviceFactory.getCoreRegistry().getTransformerClass(sourceDocument.getType(), targetType);
        SourceDocumentTransformer transformer;

        transformer = transformerClass.getConstructor(ServiceFactory.class, SourceDocument.class, RecordType.class)
                .newInstance(serviceFactory, sourceDocument, targetType);

        return transformer;
    }


    /**
     * Retrieve all user data records associated with the submitted data item. The DataRecord is a
     * view that gives direct access to parsed out metadata of the source document. A DataItem may
     * contain 1 or several DataRecords. These DataRecords are to be understood as a View of the
     * UserDataItem with no life cycle of their own.
     *
     * @param dataItem
     *
     * @return DataRecords
     *
     * @throws SQLException
     * @throws IOException
     * @throws ParseException
     */
    public GenericDataRecordCollection<IndexedDataRecord> extractDataRecords ( UserDataItem dataItem ) throws IOException, SQLException, ParseException
    {
        if (dataItem == null)
        {
            throw new NullPointerException("UserDataItem cannot be null!");
        }
        return serviceFactory.getConversionService().read(dataItem);
    }


    /**
     * Method extract the DataRecords for each UserDataItem in the submitted list. The returned map
     * keys the extracted DataRecordCollection to the UserDataItem it was extracted from.
     *
     * @param dataItems
     *
     * @return dataRecordCollections
     */
    public Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>> extractDataRecordCollections (
            List<UserDataItem> dataItems )
    {
        if (dataItems == null)
        {
            throw new NullPointerException("UserDataItems cannot be null!");
        }
        Map<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>> dataRecordCollections
                = new HashMap<UserDataItem, GenericDataRecordCollection<IndexedDataRecord>>();
        for (UserDataItem dataItem : dataItems)
        {
            // Note to Hannes: I added the following try-catch block to allow processing
            // to continue even if a UserDataItem is encountered whose DataRecords cannot
            // be extracted.  This will often be the case, since users can upload data
            // with Unknown record type.  Please feel free to change this error handling
            // to whatever you feel is appropriate.
            // - Jeremy
            try
            {
                dataRecordCollections.put(dataItem, extractDataRecords(dataItem));
            }
            catch ( Exception e )
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Error extracting DataRecordCollection from UserDataItem "
                            + dataItem.getUserDataId() + ": " + e.getMessage());
                }
            }
        }
        return dataRecordCollections;
    }


    /**
     * Method extracts the individual record portion from SourceDocument associated with the
     * submitted UserDataItem corresponding to the submitted index of the selected DataRecord.
     *
     * @param dataItem
     * @param dataRecordIndex
     *
     * @return sourceDocument
     *
     * @throws SQLException
     * @throws IOException
     */
    public SourceDocument extractSubSourceDocument ( UserDataItem dataItem, int dataRecordIndex ) throws IOException, SQLException
    {
        if (dataItem == null)
        {
            throw new NullPointerException("UserDataItem cannot be null!");
        }
        List<SourceDocument> sourceDocuments = splitSourceDocument(dataItem);
        return sourceDocuments.get(dataRecordIndex);
    }


    /**
     * Method extracts the individual record portion from the submitted SourceDocument associated
     * corresponding to the submitted index of the selected DataRecord.
     *
     * @param sourceDocument
     * @param dataRecordIndex
     *
     * @return subSourceDocument
     *
     * @throws SQLException
     * @throws IOException
     */
    public SourceDocument extractSubSourceDocument ( SourceDocument sourceDocument, int dataRecordIndex ) throws IOException, SQLException
    {
        if (sourceDocument == null)
        {
            throw new NullPointerException("SourceDocument cannot be null!");
        }
        List<SourceDocument> sourceDocuments = splitSourceDocument(sourceDocument);
        return sourceDocuments.get(dataRecordIndex);
    }


    /**
     * Method extracts the individual record portions from SourceDocument associated with the
     * submitted UserDataItem corresponding to the submitted indices of the selected DataRecords.
     *
     * @param dataItem
     * @param dataRecordIndices
     *
     * @return sourceDocuments
     *
     * @throws SQLException
     * @throws IOException
     */
    public List<SourceDocument> extractSubSourceDocuments ( UserDataItem dataItem, int[] dataRecordIndices ) throws IOException, SQLException
    {
        if (dataItem == null)
        {
            throw new NullPointerException("UserDataItem cannot be null!");
        }
        List<SourceDocument> sourceDocuments = splitSourceDocument(dataItem);
        List<SourceDocument> filteredSourceDocuments = new ArrayList<SourceDocument>(dataRecordIndices.length);
        for (int index : dataRecordIndices)
        {
            filteredSourceDocuments.add(sourceDocuments.get(index));
        }
        return filteredSourceDocuments;
    }


    /**
     * Method separates all UserDataItems in the submitted folders into RecordTypes specific Lists
     * and returns these List keyed to their RecordType in a Map.
     *
     * @param folder
     *
     * @return typeUserDataItemLists
     *
     * @throws SQLException
     * @throws IOException
     */
    public Map<RecordType, List<UserDataItem>> sortDataItemsByRecordType ( Folder folder ) throws IOException, SQLException
    {
        CoreRegistry coreRegistry = serviceFactory.getCoreRegistry();
        Map<RecordType, List<UserDataItem>> typedLists = new HashMap<RecordType, List<UserDataItem>>();
        for (UserDataItem dataItem : folder.findDataItems())
        {
            SourceDocumentType sdt = dataItem.getType();
            RecordType rt = coreRegistry.getRecordType(sdt.getEntityType(), sdt.getDataType());
            if (rt == null)
            {
                rt = RecordType.UNKNOWN;
            }
            if (typedLists.containsKey(rt) == false)
            {
                typedLists.put(rt, new ArrayList<UserDataItem>());
            }
            typedLists.get(rt).add(dataItem);
        }
        return typedLists;
    }


    /**
     * Method separates all UserDataItems in the submitted folders into DataFormat specific Lists
     * and returns these List keyed to their RecordType in a Map.
     *
     * @param folder
     *
     * @return typeUserDataItemLists
     *
     * @throws SQLException
     * @throws IOException
     */
    public Map<DataFormat, List<UserDataItem>> sortDataItemsByDataFormat ( Folder folder ) throws IOException, SQLException
    {
        Map<DataFormat, List<UserDataItem>> typedLists = new HashMap<DataFormat, List<UserDataItem>>();

        for (UserDataItem dataItem : folder.findDataItems())
        {
            DataFormat format = dataItem.getDataFormat();

            if (typedLists.containsKey(format) == false)
            {
                List<UserDataItem> newList = new ArrayList<UserDataItem>();

                newList.add(dataItem);

                typedLists.put(format, newList);
            }
            else
            {
                typedLists.get(format).add(dataItem);
            }
        }

        return typedLists;
    }

    // Portal2 uses this.  It doesn't currently calculate predictedSus.

    public String submitTask ( Task task, Boolean loggedInViaIPlant ) throws Exception, DisabledResourceException
    {
        return submitTask(task, loggedInViaIPlant, null);
    }


    /*
     * public String submitTask(Task task) throws Exception, DisabledResourceException { return
     * submitTask(task, false); }
     */
    // REST API via jobs/Job.java uses this.
    public String submitTask ( Task task, Long predictedSus ) throws Exception, DisabledResourceException
    {
        return submitTask(task, false, predictedSus);
    }


    public String submitTask ( Task task, Boolean loggedInViaIPlant, Long predictedSus ) throws Exception, DisabledResourceException
    {
        return (new TaskInitiate(serviceFactory)).queueTask(task, loggedInViaIPlant, predictedSus);
    }
    
    
    public String saveAndSubmitTask ( Task task, Folder folder ) throws Exception, DisabledResourceException
    {
        return saveAndSubmitTask(task, folder, false);
    }


    /**
     * Method allows you to submit a transient task. Before submission the task is saved to the
     * target folder. see submitTask(Task task)
     *
     * @param task
     * @param folder
     * @param loggedInViaIPlant
     *
     * @return saved task instance
     *
     * @throws SQLException
     * @throws org.ngbw.sdk.tool.DisabledResourceException
     * @throws IOException
     * @returns unique jobHandle
     */
    public String saveAndSubmitTask ( Task task, Folder folder, Boolean loggedInViaIPlant ) throws Exception, DisabledResourceException
    {
        if (task == null)
        {
            throw new NullPointerException("task");
        }

        task.setEnclosingFolder(folder);

        task.save();

        long userId = task.getUserId();

        return (new TaskInitiate(serviceFactory)).queueTask(task, loggedInViaIPlant, null);
    }


    /**
     * Run already rendered commands synchronously. Does so by inserting a Task that isn't
     * associated with any user.
     *
     * @param toolName
     *
     * @param command           Rendered command line to be executed.
     *
     * @param input             Maps filenames (as they appear in command) to their contents as byte
     *                          arrays. The data will be staged to the specified files before the
     *                          command is executed.
     *
     * @param outputFiles       Maps parameter names to the names of the expected output files.
     *                          Filenames may include wildcards. The contents of the files will be
     *                          harvested to create the map that is returned.
     *
     * @param allowMissingFiles if false an exception will be thrown if any of the expected
     *                          outputFiles aren't found. If true, missing files don't generate an
     *                          exception. THIS PARAMETER IS NOW IGNORED.
     *                          BaseProcessWorker.storeOutputFiles() doesn't throw an exception when
     *                          a file is missing ... missing is always allowed.
     *
     * @return Map of output parameter name -> (map of filename -> file contents) Each output
     *         parameter may map to multiple filenames if wildcards were used in outputFiles.
     *
     * @throws Exception
     */
    /*
     * public Map<String, Map<String, byte[]>> runCommand(	String toolName, String[] command,
     * Map<String, byte[]> input, Map<String,String> outputFiles, boolean allowMissingFiles) throws
     * Exception { return (new CommandRunner(serviceFactory)).runCommand(toolName, command, input,
     * outputFiles); }
     */

 /*
     * Semantic Annotation Methods
     */
    /**
     * Return all registered EntityTypes that are mapped to non-abstract RecordTypes
     *
     * @return Set<EntityType>
     */
    public Set<EntityType> getEntityTypes ()
    {
        return serviceFactory.getCoreRegistry().getEntityTypes();
    }


    /**
     * Return all registered EntityTypes (such as PROTEIN, NUCLEIC_ACID, COMPOUND, etc.)
     *
     * @return Set<EntityType>
     */
    public Set<EntityType> getAllEntityTypes ()
    {
        return serviceFactory.getCoreRegistry().getAllEntityTypes();
    }


    /**
     * Return all registered DataTypes that are mapped to non-abstract RecordTypes.
     *
     * @return Set<DataType>
     */
    public Set<DataType> getDataTypes ()
    {
        return serviceFactory.getCoreRegistry().getDataTypes();
    }


    /**
     * Return all registered DataTypes (SEQUENCE, STRUCTURE, SEQUENCE_ALIGNMENT etc.)
     *
     * @return Set<DataType>
     */
    public Set<DataType> getAllDataTypes ()
    {
        return serviceFactory.getCoreRegistry().getAllDataTypes();
    }


    /**
     * Return all registered RecordTypes (such as PROTEIN_SEQUENCE, NUCLEIC_ACID_SEQUENCE,
     * COMPOUND_STRUCTURE, etc.)
     *
     * @return Set<RecordType>
     */
    public Set<RecordType> getRecordTypes ()
    {
        return serviceFactory.getCoreRegistry().getRecordTypes();
    }


    /**
     * Return all registered RecordTypes for the submitted EntityType (such as PROTEIN_SEQUENCE,
     * PROTEIN_SEQUENCE_ALIGNMENT, etc. for PROTEIN).
     *
     * @param entityType
     *
     * @return Set<RecordType>
     */
    public Set<RecordType> getRecordTypes ( EntityType entityType )
    {
        return serviceFactory.getCoreRegistry().getRecordTypes(entityType);
    }


    /**
     * Return all registered RecordTypes for the submitted DataType (such as PROTEIN_SEQUENCE,
     * NUCLEIC_ACID_SEQUENCE, etc. for SEQUENCE).
     *
     * @param dataType
     *
     * @return Set<RecordType>
     */
    public Set<RecordType> getRecordTypes ( DataType dataType )
    {
        return serviceFactory.getCoreRegistry().getRecordTypes(dataType);
    }


    /**
     * Returns the registered RecordType for the submitted EntityType and DataType.
     *
     * @param entityType
     * @param dataType
     *
     * @return RecordType
     */
    public RecordType getRecordType ( EntityType entityType, DataType dataType )
    {
        return serviceFactory.getCoreRegistry().getRecordType(entityType, dataType);
    }


    /**
     * Method returns the DataType of the submitted RecordType.
     *
     * @param recordType
     *
     * @return dataType
     */
    public DataType getDataType ( RecordType recordType )
    {
        return serviceFactory.getCoreRegistry().getDataType(recordType);
    }


    /**
     * Method returns the EntityType of the submitted RecordType.
     *
     * @param recordType
     *
     * @return entityType
     */
    public EntityType getEntityType ( RecordType recordType )
    {
        return serviceFactory.getCoreRegistry().getEntityType(recordType);
    }


    /**
     * Method returns all RecordFields for the submitted RecordType.
     *
     * @param recordType
     *
     * @return recordFields
     */
    public Set<RecordFieldType> getRecordFields ( RecordType recordType )
    {
        return serviceFactory.getCoreRegistry().getRecordFields(recordType);
    }


    /*
     * ConversionService Methods
     */
    /**
     * Method splits a (potentially) multi-record SourceDocument into a List of SourceDocuments,
     * each corresponding to an individual DataRecord.
     *
     * @param sourceDocument
     *
     * @return sourceDocumentList
     *
     * @throws SQLException
     * @throws IOException
     */
    public List<SourceDocument> splitSourceDocument ( SourceDocument sourceDocument ) throws IOException, SQLException
    {
        if (sourceDocument == null)
        {
            throw new NullPointerException("SourceDocument cannot be null!");
        }
        ConversionService cs = serviceFactory.getConversionService();
        List<SourceDocument> splitSourceDocuments = new ArrayList<SourceDocument>();
        RecordFilter filter = cs.getRecordFilter(sourceDocument.getDataFormat());
        BufferedReader br = new BufferedReader(new StringReader(new String(sourceDocument.getData())));
        filter.setInput(br);
        SourceDocumentType sdt = sourceDocument.getType();
        while (filter.hasNext())
        {
            SourceDocument sd = new SourceDocumentBean(sdt, filter.next().getBytes());
            splitSourceDocuments.add(sd);
        }
        filter.close();
        return splitSourceDocuments;
    }


    /**
     * Method returns all registered DataFormats that can be read into a DataRecord. Be aware that
     * some DataFormats encode different DataTypes (like a Fasta formatted data may represent a
     * single Sequence or a collection of Sequences or an Alignment. CAVE: registered does not imply
     * readable
     *
     * @return dataFormats
     */
    public Set<DataFormat> getRegisteredDataFormats ()
    {
        Set<SourceDocumentType> registeredTypes = serviceFactory
                .getConversionService().getConversionRegistry()
                .getRegisteredDocumentTypes();
        Set<DataFormat> dataFormats = new HashSet<DataFormat>();
        for (SourceDocumentType sourceDocumentType : registeredTypes)
        {
            dataFormats.add(sourceDocumentType.getDataFormat());
        }
        return dataFormats;
    }


    /**
     * Method returns all registered DataFormats for the submitted RecordType that can be read into
     * a DataRecord. CAVE: registered does not imply readable
     *
     * @param recordType
     *
     * @return dataFormats
     */
    public Set<DataFormat> getRegisteredDataFormats ( RecordType recordType )
    {
        CoreRegistry coreRegistry = serviceFactory.getCoreRegistry();
        Set<SourceDocumentType> registeredTypes = serviceFactory
                .getConversionService().getConversionRegistry()
                .getRegisteredDocumentTypes();
        Set<DataFormat> dataFormats = new HashSet<DataFormat>();
        for (SourceDocumentType sourceDocumentType : registeredTypes)
        {
            RecordType rt = coreRegistry.getRecordType(sourceDocumentType.getEntityType(), sourceDocumentType.getDataType());
            if (rt == null)
            {
                continue;
            }
            if (rt.equals(recordType))
            {
                dataFormats.add(sourceDocumentType.getDataFormat());
            }
        }
        return dataFormats;
    }


    /**
     * Parse a SourceDocument instance that contains a one or more entries (eg. single sequence
     * fasta file) and populate a DataRecordCollection.
     *
     * @param srcDocument
     *
     * @return dataRecordCollection
     *
     * @throws SQLException
     * @throws IOException
     * @throws ParseException
     */
    public GenericDataRecordCollection<IndexedDataRecord> read ( SourceDocument srcDocument ) throws IOException, SQLException, ParseException
    {
        return serviceFactory.getConversionService().read(srcDocument);
    }


    /**
     * This method will parse a SourceDocument and reassemble the data into the submitted target
     * SourceDocumentType. This conversion will only then lead to a successful result if the
     * information content of source format <= target format. <p>
     * @param sr
     *
     * cDocument
     * @param targetKey
     *
     * @return sourceDocument
     *
     * @throws SQLException
     * @throws IOException
     * @throws ParseException
     */
    public SourceDocument convert ( SourceDocument srcDocument,
                                    SourceDocumentType targetKey ) throws IOException, SQLException, ParseException
    {
        return serviceFactory.getConversionService().convert(srcDocument,
                                                             targetKey);
    }


    /**
     * This method will parse all SourceDocuments of the submitted input collection and reassemble
     * all of them into one new SourceDocuments of the submitted target SourceDocumentType. This
     * conversion will only then lead to a successful result if the information content of source
     * format <= target format. <p>
     * @param sr
     *
     * cDocuments
     * @param targetKey
     *
     * @return sourceDocument
     *
     * @throws SQLException
     * @throws IOException
     * @throws ParseException
     */
    public SourceDocument convert ( Collection<SourceDocument> srcDocuments,
                                    SourceDocumentType targetKey ) throws IOException, SQLException, ParseException
    {
        return serviceFactory.getConversionService().convert(srcDocuments,
                                                             targetKey);
    }


    /**
     * Method returns all target SemanticKeys that the submitted source DataFormat can be converted
     * into.
     *
     * @param sourceDocumentType
     *
     * @return targetSemanticKeys
     */
    public Set<SourceDocumentType> getSourceDocumentTypes (
            SourceDocumentType sourceDocumentType )
    {
        return serviceFactory.getConversionService().getTargetSourceDocumentTypes(
                sourceDocumentType);
    }


    /**
     * Method checks whether there is a SourceDocumentReader registered in the ConversionService for
     * the submitted SourceDocumentType.
     *
     * @param sourceDocumentType
     *
     * @return canRead
     */
    public boolean canRead ( SourceDocumentType sourceDocumentType )
    {
        return serviceFactory.getConversionService()
                .canRead(sourceDocumentType);
    }


    /**
     * Method checks the submitted SourceDocument whether the data are indeed formatted in the
     * declared SourceDocumentType. It will also set the flag of the sourceDocument to validated =
     * true if the check is successful.
     *
     * @param srcDocument
     *
     * @return isValid
     *
     * @throws SQLException
     * @throws IOException
     */
    public boolean validate ( SourceDocument srcDocument ) throws IOException, SQLException
    {
        return serviceFactory.getConversionService().validate(srcDocument);
    }


    /**
     * Method checks whether there is a converter registered in the ConversionService that can
     * submitted SourceDocument can be converted into the target format.
     *
     * @param sourceKey
     * @param targetKey
     *
     * @return true if the SourceDocument can be converted into the target format.
     */
    public boolean canConvert ( SourceDocumentType sourceKey,
                                SourceDocumentType targetKey )
    {
        return serviceFactory.getConversionService().canConvert(sourceKey,
                                                                targetKey);
    }


    /*
     * ************ DatasetService Methods **********
     */
    /**
     * Method returns all RecordTypes that have at least one Dataset registered.
     *
     * @return recordTypes
     */
    public Set<RecordType> getSearchableRecordTypes ()
    {
        return serviceFactory.getDatasetService().getSearchableRecordTypes();
    }


    /**
     * Method returns all EntityTypes that have at least one Dataset registered.
     *
     * @return entityTypes
     */
    public Set<EntityType> getSearchableEntityTypes ()
    {
        return serviceFactory.getDatasetService().getSearchableEntityTypes();
    }


    /**
     * Method returns all DataTypes that have at least one Dataset registered.
     *
     * @return dataTypes
     */
    public Set<DataType> getSearchableDataTypes ()
    {
        return serviceFactory.getDatasetService().getSearchableDataTypes();
    }


    /**
     * Method returns all registered Datasets.
     *
     * @return datasets
     */
    public Set<Dataset> getDatasets ()
    {
        return serviceFactory.getDatasetService().getDatasets();
    }


    /**
     * Method returns all registered Datasets for the submitted DataType and EntityType.
     *
     * @param entityType
     * @param dataType
     *
     * @return datasets
     */
    public Set<Dataset> getDatasets ( EntityType entityType, DataType dataType )
    {
        return serviceFactory.getDatasetService().getDatasets(entityType,
                                                              dataType);
    }


    /**
     * Method returns all registered Datasets for the submitted RecordType.
     *
     * @param recordType
     *
     * @return datasets
     */
    public Set<Dataset> getDatasets ( RecordType recordType )
    {
        EntityType entityType = serviceFactory.getCoreRegistry().getEntityType(
                recordType);
        DataType dataType = serviceFactory.getCoreRegistry().getDataType(
                recordType);
        return serviceFactory.getDatasetService().getDatasets(entityType,
                                                              dataType);
    }


    /**
     * Method returns the SourceDocumentType for the submitted Dataset.
     *
     * @param dataset
     *
     * @return sourceDocumentType
     */
    public SourceDocumentType getSourceDocumentType ( Dataset dataset )
    {
        return serviceFactory.getDatasetService()
                .getSourceDocumentType(dataset);
    }


    /**
     * Method returns the RecordType for the submitted Dataset.
     *
     * @param dataset
     *
     * @return recordType
     */
    public RecordType getRecordType ( Dataset dataset )
    {
        return serviceFactory.getDatasetService().getRecordType(dataset);
    }


    /*
     * Query Methods
     */
    /**
     * Get a SimpleSearchMetaQuery for the submitted Dataset. A SimpleSearchMetaQuery can be used
     * multiple times and executed with a different searchPhrase respectively.
     *
     * @param dataset
     *
     * @return SimpleSearchMetaQuery for the dataset parameter
     */
    public SimpleSearchMetaQuery getSimpleSearchQuery ( Dataset dataset )
    {
        return serviceFactory.getDatasetService().getSimpleSearchQuery(dataset);
    }


    /**
     * Get a SimpleSearchMetaQuery for the submitted Datasets. A SimpleSearchMetaQuery can be used
     * multiple times and executed with a different searchPhrase respectively.
     *
     * @param datasets
     *
     * @return SimpleSearchMetaQuery for the datasets parameter
     */
    public SimpleSearchMetaQuery getSimpleSearchQuery ( Set<Dataset> datasets )
    {
        return serviceFactory.getDatasetService()
                .getSimpleSearchQuery(datasets);
    }


    /*
     * ************ ToolService Methods **********
     */
    /**
     * Return all registered Tools.
     *
     * @return Set<Tool>
     */
    public Set<String> getToolIds ()
    {
        return serviceFactory.getToolRegistry().getToolIds();
    }


    /*
     * Return all registered tools that don't have an inactive=true attribute.
     *
     */
    public Set<String> getActiveToolIds ()
    {
        return serviceFactory.getToolRegistry().getActiveToolIds();
    }


    /**
     * User and UserData Administrative Methods
     */
    /**
     * Method allows a client to register a new user account. The method checks that the submitted
     * fields do not violate any constraints in the user database.
     *
     * @param user
     *
     * @return ValidationResult
     *
     * @throws SQLException
     * @throws IOException
     */
    public ValidationResult registerNewUser ( User user ) throws IOException, SQLException
    {
        ValidationResult result = new ValidationResult();

        if (User.findUser(user.getUsername()) != null)
        {
            result.addError("Username " + user.getUsername() + " is not available!");
            return result;
        }

        //if (User.findUserByEmailAndRole(user.getEmail(), user.getRole().toString()) != null)
        if (User.findUserByEmail(user.getEmail()) != null)
        {
            result.addError("A user with this email: " + user.getEmail() + " already exists.");
            return result;
        }
        
        if (!exemptFromMultipleAccountCheck(user.getEmail())) {
            logger.info("registerNewUser() comment = " + user.getComment());
            User existingUser = null;
            if (user.getComment() != null && (existingUser = User.findUserByComment(user.getComment())) != null) {
                String warning = "User (username = " + existingUser.getUsername() + ", email = "
                        + existingUser.getEmail() + ") is trying to create a new account with username = "
                        + user.getUsername() + ", email = " + user.getEmail();
                logger.info(warning);
                result.addError("New account authentication fails. If you have any question, please contact us at cosmic2support@umich.edu");
                result.addWarning(warning);
                RegistrationManager.getRegistrationBlacklistManager().addToTheList(user.getEmail());
                return result;
            }

            
            if (RegistrationManager.getRegistrationBlacklistManager().findIt(user.getEmail())) {
                String warning = "New account creation  with username = " + user.getUsername() + ", email = "
                        + user.getEmail() + " is rejected because the email is in the blacklist";
                logger.info(warning);
                result.addError("New account authentication fails (gmail.com, 163.com, qq.com, globusid.com, etc are not allowed to be used to register a new account). " +
                        "If you have any question, please contact us at cosmic2support@umich.edu");
                result.addWarning(warning);
                return result;
            }
        }

        user.save();
        return result;
    }


    /**
     * Method allows a client to fully register a guest user account. The method checks that the
     * submitted fields do not violate any constraints in the user database. The method will
     * typically simply update the existing guest user account.
     *
     * @param user
     *
     * @return ValidationResult
     *
     * @throws SQLException
     * @throws IOException
     */
    public ValidationResult registerGuestUser ( User user ) throws IOException, SQLException
    {
        return registerNewUser(user);
    }


    /**
     * Method allows a client to update user information. The method checks that the updated fields
     * do not violate any constraints in the user database.
     *
     * @param user
     *
     * @return ValidationResult
     *
     * @throws SQLException
     * @throws IOException
     */
    public ValidationResult updateUser ( User user ) throws IOException, SQLException
    {
        user.save();

        return new ValidationResult();
    }


    /**
     * Method allows an administrator to reset a user password without knowing the existing
     * password.
     *
     * @param username
     * @param newPassword
     *
     * @throws SQLException
     * @throws IOException
     */
    public void resetPasswordAdmin ( String username, String newPassword ) throws IOException, SQLException
    {
        User user = User.findUser(username);

        if (user == null || !user.getUsername().equals(username))
        {
            throw new WorkbenchException("User does not exist!");
        }

        user.setPassword(newPassword);

        user.save();
    }


    /**
     * Method saves the submitted group and assigns the user with the submitted username as group
     * administrator.
     *
     * @param group
     * @param administrator
     *
     * @return group
     *
     * @throws SQLException
     * @throws IOException
     */
    public Group saveNewGroup ( Group group, User administrator ) throws IOException, SQLException
    {
        group.setAdministrator(administrator);

        group.save();

        return group;
    }

    // Factory methods

//    /**
//     * Method returns a new transient User instance.
//     *
//     * @return user
//     */
//    public User getNewUserInstance ()
//    {
//        return new User();
//    }


    /**
     * Method returns a new transient Group instance.
     *
     * @return group
     */
    public Group getNewGroupInstance ()
    {
        return new Group();
    }


    public Tool getTool ( String toolId )
    {
        return new Tool(toolId, serviceFactory.getToolRegistry());
    }


    public static void convertEncoding ( File file ) throws Exception
    {
        org.ngbw.sdk.common.util.ConvertEncoding.convertInPlace(file);
    }
    
    
    /** 
     * Retrieves a property from properties file. The parameter <code>defaultValue</code> will be 
     * returned if the property cannot be found. 
     * 
     * @param name the name of property 
     * @param defaultValue value to be used if the property does not exist 
     * @return 
     */
    public String getProperty ( String name, String defaultValue ) 
    {
        return (this.properties == null)?  
                defaultValue : this.properties.getProperty(name, defaultValue);
    }
    
    
    /** 
     * Retrieves a property from properties file and converts it to an integer value. 
     * The parameter <code>defaultValue</code> will be returned if the property cannot be found. 
     * 
     * @param name the name of property 
     * @param defaultValue value to be used if the property does not exist 
     * @return 
     */
    public Integer getPropertyAsInt ( String name, Integer defaultValue ) 
    {
        try {
            return Integer.parseInt(getProperty(name, null));
        }
        catch ( NumberFormatException nfe ) {
            return defaultValue;
        }
    }
    
    
    /** 
     * Retrieves a property from properties file and converts it to a long value. 
     * The parameter <code>defaultValue</code> will be returned if the property cannot be found. 
     * 
     * @param name the name of property 
     * @param defaultValue value to be used if the property does not exist 
     * @return 
     */
    public Long getPropertyAsLong ( String name, Long defaultValue ) 
    {
        try {
            return Long.parseLong(getProperty(name, null));
        }
        catch ( NumberFormatException nfe ) {
            return defaultValue;
        }
    }
    
    
    /** 
     * Retrieves a property from properties file and converts it to a float value. 
     * The parameter <code>defaultValue</code> will be returned if the property cannot be found. 
     * 
     * @param name the name of property 
     * @param defaultValue value to be used if the property does not exist 
     * @return 
     */
    public Float getPropertyAsFloat ( String name, Float defaultValue ) 
    {
        try {
            return Float.parseFloat(getProperty(name, null));
        }
        catch ( NumberFormatException nfe ) {
            return defaultValue;
        }
    }
    
    
    /** 
     * Retrieves a property from properties file and converts it to a double value. 
     * The parameter <code>defaultValue</code> will be returned if the property cannot be found. 
     * 
     * @param name the name of property 
     * @param defaultValue value to be used if the property does not exist 
     * @return 
     */
    public Double getPropertyAsDouble ( String name, Double defaultValue ) 
    {
        try {
            return Double.parseDouble(getProperty(name, null));
        }
        catch ( NumberFormatException nfe ) {
            return defaultValue;
        }
    }
    
    
    public long getCPUresourceConversionId () throws NumberFormatException 
    {
        Properties wbProperties = getProperties();
        
        try {
            return Long.parseLong(wbProperties.getProperty("cpu.resource.conversion.id"));
        }
        catch ( NumberFormatException nfe ) {
            logger.error("${cpu.resource.conversion.id} property is not set.", nfe);
            throw new NumberFormatException("${cpu.resource.conversion.id} property is not set.");
        }
    }
    
    
    public long getGPUresourceConversionId () throws NumberFormatException 
    {
        Properties wbProperties = getProperties();
        
        try {
            return Long.parseLong(wbProperties.getProperty("gpu.resource.conversion.id"));
        }
        catch ( NumberFormatException nfe ) {
            logger.error("${gpu.resource.conversion.id} property is not set.", nfe);
            throw new NumberFormatException("${gpu.resource.conversion.id} property is not set.");
        }
    }

    public boolean exemptFromMultipleAccountCheck(String email)
    {
        return RegistrationManager.exemptFromMultipleAccountCheck(email);
    }

}
