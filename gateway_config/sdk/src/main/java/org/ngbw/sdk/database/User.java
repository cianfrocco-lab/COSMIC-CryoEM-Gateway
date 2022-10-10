/*
 * User.java
 */
package org.ngbw.sdk.database;


import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.ValidationException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.core.shared.UserRole;
import org.ngbw.sdk.common.util.ProcessRunner;


/**
 *
 * @author Paul Hoover
 *
 */
public class User extends VersionedRow implements Comparable<User> {
	public static final String TERAGRID = "teragrid";
	public static final String DATA_SIZE_EXCEEDS_MAX = "data_size_exceeds_max";

	public static final Map<String, String> US_TERRITORIES = new HashMap<>();

	static {
		US_TERRITORIES.put("US", "US"); // United States
		US_TERRITORIES.put("AS", "AS"); // American Samoa
		US_TERRITORIES.put("GU", "GU"); // Guam
		US_TERRITORIES.put("PR", "PR"); // Puerto Rico
		US_TERRITORIES.put("VI", "VI"); // Virgin Island
	}

	// nested classes


	/**
	 *
	 */
	private class RemoveAllMembershipsOp implements RowOperation {

		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			PreparedStatement deleteStmt = dbConn.prepareStatement("DELETE FROM user_group_lookup WHERE USER_ID = ? AND GROUP_ID <> ?");

			try {
				m_key.setParameter(deleteStmt, 1);
				m_defaultGroupId.setParameter(deleteStmt, 2);

				deleteStmt.executeUpdate();
			}
			finally {
				deleteStmt.close();
			}

			m_memberships = null;
		}
	}

	/**
	 *
	 */
	private class MembershipSet extends MonitoredSet<Group> {

		// constructors


		protected MembershipSet(Set<Group> groups)
		{
			super(groups);
		}


		// protected methods


		@Override
		protected void addSetAddOp(Group element)
		{
			if (element.isNew())
				throw new WorkbenchException("Can't add membership in an unpersisted group");

			Column<Long> groupId = new LongColumn("GROUP_ID", false, element.getGroupId());
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(groupId);

			m_opQueue.add(new InsertOp("user_group_lookup", cols));
		}

		@Override
		protected void addSetRemoveOp(Group element)
		{
			long groupId = element.getGroupId();

			if (groupId == m_defaultGroupId.getValue())
				throw new WorkbenchException("Can't remove membership in default group");

			Column<Long> groupIdCol = new LongColumn("GROUP_ID", false, groupId);
			CompositeKey key = new CompositeKey(m_key, groupIdCol);

			m_opQueue.add(new DeleteOp("user_group_lookup", key));
		}

		@Override
		protected void addSetClearOp()
		{
			m_opQueue.add(new RemoveAllMembershipsOp());
		}
	}

	/**
	 *
	 */
	private class PreferenceMap extends MonitoredMap<String, String> {

		// constructors


		protected PreferenceMap(Map<String, String> prefMap)
		{
			super(prefMap);
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, String value)
		{
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			Column<String> prefValue = new StringColumn("VALUE", true, 100, value);
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(prefName);
			cols.add(prefValue);

			m_opQueue.add(new InsertOp("user_preferences", cols));
		}

		@Override
		protected void addMapSetOp(String key, String oldValue, String newValue)
		{
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			Column<String> prefValue = new StringColumn("VALUE", true, 100, newValue);
			CompositeKey prefKey = new CompositeKey(m_key, prefName);

			m_opQueue.add(new UpdateOp("user_preferences", prefKey, prefValue));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			CompositeKey prefKey = new CompositeKey(m_key, prefName);

			m_opQueue.add(new DeleteOp("user_preferences", prefKey));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new DeleteOp("user_preferences", getKey()));
		}
	}

	/**
	 *
	 */
	private class CreateDefaultGroupOp implements RowOperation {

		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			if (!m_defaultGroupId.isNull())
				return;

			Group defaultGroup = new Group();

			defaultGroup.setGroupname(m_username.getValue());
			defaultGroup.setAdministratorId(m_key.getValue());
			defaultGroup.members().add(User.this);
			defaultGroup.save(dbConn);

			long groupId = defaultGroup.getGroupId();

			m_defaultGroupId.setValue(groupId);

			(new UpdateOp(TABLE_NAME, getKey(), m_defaultGroupId)).execute(dbConn);
		}
	}

	/**
	 *
	 */
	private class CreateHomeFolderOp implements RowOperation {

		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			Folder homeFolder = new Folder(User.this);

			homeFolder.setLabel(m_username.getValue());
			homeFolder.save(dbConn);
		}
	}


	// data fields
	protected static final String TABLE_NAME = "users";
	private static final String KEY_NAME = "USER_ID";
	private static final Log log = LogFactory.getLog(User.class.getName());


	private final Column<String> m_firstName = new StringColumn("FIRST_NAME", false, 100);
	private final Column<String> m_lastName = new StringColumn("LAST_NAME", false, 100);
	private final Column<String> m_username = new StringColumn("USERNAME", false, 200);
	private final Column<String> m_password = new StringColumn("PASSWORD", false, 50);
	private final Column<String> m_institution = new StringColumn("INSTITUTION", true, 255);
	private final Column<String> m_role = new StringColumn("ROLE", false, 50);
	private final Column<String> m_streetAddress = new StringColumn("STREET_ADDRESS", true, 255);
	private final Column<String> m_city = new StringColumn("CITY", true, 100);
	private final Column<String> m_state = new StringColumn("STATE", true, 50);
	private final Column<String> m_country = new StringColumn("COUNTRY", true, 50);
	private final Column<String> m_mailCode = new StringColumn("MAILCODE", true, 10);
	private final Column<String> m_zipCode = new StringColumn("ZIP_CODE", true, 10);
	private final Column<String> m_areaCode = new StringColumn("AREA_CODE", true, 10);
	private final Column<String> m_phoneNumber = new StringColumn("PHONE_NUMBER", true, 20);
	private final Column<String> m_email = new StringColumn("EMAIL", false, 200);
	private final Column<String> m_websiteUrl = new StringColumn("WEBSITE_URL", true, 255);
	private final Column<String> m_comment = new StringColumn("COMMENT", true, 255);
	private final Column<Long> m_defaultGroupId = new LongColumn("DEFAULT_GROUP_ID", true);
	private final Column<Boolean> m_active = new BooleanColumn("ACTIVE", false);
	private final Column<Boolean> m_canSubmit = new BooleanColumn("CAN_SUBMIT", false);
	private final Column<Date> m_lastLogin  = new DateColumn("LAST_LOGIN", true);
	private final Column<String> m_umbrellaAppname = new StringColumn("UMBRELLA_APPNAME", false, 30);
	private final Column<String>  m_activationCode  = new StringColumn("ACTIVATION_CODE", true, 50);
	private final Column<Date>  m_activationSent  = new DateColumn("ACTIVATION_SENT", true);
	private final Column<Date>  m_dateCreated  = new DateColumn("DATE_CREATED", false);
	private final Column<Integer> m_maxUploadSizeGB = new IntegerColumn
		( "MAX_UPLOAD_SIZE_GB", true, 50 );
	private MembershipSet m_memberships;
	private PreferenceMap m_preferences;
	private long m_dataSize = -1;
	private long m_dataSizeDU = -1;


	// constructors


	public User()
	{
		this(TABLE_NAME, KEY_NAME);

		//setAppname("");
		setActive(true);
		setCanSubmit(true);
		setUmbrellaAppname("");
		m_dateCreated.setValue(new Date());
	}

	public User(long userId) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

		m_key.assignValue(userId);

		load();
	}

	User(Connection dbConn, long userId) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

		m_key.assignValue(userId);

		load(dbConn);
	}

	private User(String tableName, String keyName)
	{
		super(tableName, keyName);
		construct(m_firstName, m_lastName, m_username, m_password, m_institution, m_role,
			m_streetAddress, m_city, m_state, m_country, m_mailCode, m_zipCode, m_areaCode, m_phoneNumber,
			m_email,  m_websiteUrl, m_comment, m_defaultGroupId, m_active, m_canSubmit, m_lastLogin,
			m_umbrellaAppname, m_activationCode, m_activationSent, m_dateCreated, m_maxUploadSizeGB );
	}


	// public methods


	public long getUserId()
	{
		return m_key.getValue();
	}


	/**
	 * Get the user's cached total documents' size.  Will return 0 if there is
	 * a problem querying the database.
	 */
	public long getDataSize()
	{
		// If database query has not been done, do it...
		if ( m_dataSize == -1 )
		{
			try
			{
				m_dataSize = queryDataSize();
			}
			catch ( Exception e )
			{
				return ( 0 );
			}
		}

		return ( m_dataSize );
	}

	/**
	 * Get the user's globus_transfers du.  Will return 0 if there is
	 * a problem querying the database.
	 */
	public long getDataSizeDU()
	{
		log.debug("start of getDataSizeDU()");
		// If database query has not been done, do it...
		if ( m_dataSizeDU == -1 )
		{
			try
			{
				m_dataSizeDU = queryDataSizeDU();
			}
			catch ( Exception e )
			{
				return ( 0 );
			}
		}

		log.debug("m_dataSizeDU: (" + m_dataSizeDU + ")");
		return ( m_dataSizeDU );
	}


	/**
	 * Function to query the database for the user's data size.
     * Mona - added inclusion of the new userdata_dir table's size column.
	 * @return long - total number of bytes; could be 0 if nothing found
	 **/
	public long queryDataSize() throws SQLException
	{
        //log.debug ( "MONA: entered User.queryDataSize()" );
		long userid = getUserId();
        //log.debug ( "MONA: userid = " + userid );

        /* Before adding userdata_dir table...
		String query = "SELECT data_usage.NUM_DOCUMENTS + input_usage.NUM_DOCUMENTS + output_usage.NUM_DOCUMENTS AS TOTAL_DOCUMENTS, " +
				"data_usage.TOTAL_LENGTH + input_usage.TOTAL_LENGTH + output_usage.TOTAL_LENGTH AS TOTAL_LENGTH " +
				"FROM users " +
				"INNER JOIN ( SELECT users.USER_ID, " +
					"COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
					"SUM(source_documents.LENGTH) AS TOTAL_LENGTH FROM users " +
					"INNER JOIN userdata ON users.USER_ID = userdata.USER_ID " +
					"INNER JOIN source_documents ON userdata.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
					"WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
				"AS data_usage ON users.USER_ID = data_usage.USER_ID " +
				"INNER JOIN ( SELECT users.USER_ID, " +
					"COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
					"SUM(source_documents.LENGTH) AS TOTAL_LENGTH FROM users " +
					"INNER JOIN tasks ON users.USER_ID = tasks.USER_ID " +
					"INNER JOIN task_input_parameters ON tasks.TASK_ID = task_input_parameters.TASK_ID " +
					"INNER JOIN task_input_source_documents ON task_input_parameters.INPUT_ID = task_input_source_documents.INPUT_ID " +
					"INNER JOIN source_documents ON task_input_source_documents.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
					"WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
				"AS input_usage ON users.USER_ID = input_usage.USER_ID " +
				"INNER JOIN ( SELECT users.USER_ID, " +
					"COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
					"SUM(source_documents.LENGTH) AS TOTAL_LENGTH FROM users " +
					"INNER JOIN tasks ON users.USER_ID = tasks.USER_ID " +
					"INNER JOIN task_output_parameters ON tasks.TASK_ID = task_output_parameters.TASK_ID " +
					"INNER JOIN task_output_source_documents ON task_output_parameters.OUTPUT_ID = task_output_source_documents.OUTPUT_ID " +
					"INNER JOIN source_documents ON task_output_source_documents.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
					"WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
				"AS output_usage ON users.USER_ID = output_usage.USER_ID " +
				"WHERE users.USER_ID = ?";
        */

        String query = "SELECT " +
            "data_usage.NUM_DOCUMENTS + input_usage.NUM_DOCUMENTS + output_usage.NUM_DOCUMENTS + data_dir_usage.NUM_DOCUMENTS AS TOTAL_DOCUMENTS, " +
            "data_usage.TOTAL_LENGTH + input_usage.TOTAL_LENGTH + output_usage.TOTAL_LENGTH + data_dir_usage.TOTAL_LENGTH AS TOTAL_LENGTH FROM users " +
            "INNER JOIN ( SELECT users.USER_ID, " +
            "COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
            "COALESCE(SUM(source_documents.LENGTH), 0) AS TOTAL_LENGTH " +
            "FROM users " +
            "LEFT JOIN userdata ON users.USER_ID = userdata.USER_ID " +
            "LEFT JOIN source_documents ON userdata.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
            "WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
            "AS data_usage ON users.USER_ID = data_usage.USER_ID " +
            "INNER JOIN ( SELECT users.USER_ID, " +
            "COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
            "COALESCE(SUM(source_documents.LENGTH), 0) AS TOTAL_LENGTH FROM users " +
            "LEFT JOIN tasks ON users.USER_ID = tasks.USER_ID " +
            "LEFT JOIN task_input_parameters ON tasks.TASK_ID = task_input_parameters.TASK_ID " +
            "LEFT JOIN task_input_source_documents ON task_input_parameters.INPUT_ID = task_input_source_documents.INPUT_ID " +
            "LEFT JOIN source_documents ON task_input_source_documents.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
            "WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
            "AS input_usage ON users.USER_ID = input_usage.USER_ID " +
            "INNER JOIN ( SELECT users.USER_ID, " +
            "COUNT(source_documents.SOURCE_DOCUMENT_ID) AS NUM_DOCUMENTS, " +
            "COALESCE(SUM(source_documents.LENGTH), 0) AS TOTAL_LENGTH FROM users " +
            "LEFT JOIN tasks ON users.USER_ID = tasks.USER_ID " +
            "LEFT JOIN task_output_parameters ON tasks.TASK_ID = task_output_parameters.TASK_ID " +
            "LEFT JOIN task_output_source_documents ON task_output_parameters.OUTPUT_ID = task_output_source_documents.OUTPUT_ID " +
            "LEFT JOIN source_documents ON task_output_source_documents.SOURCE_DOCUMENT_ID = source_documents.SOURCE_DOCUMENT_ID " +
            "WHERE users.USER_ID = ? GROUP BY users.USER_ID ) " +
            "AS output_usage ON users.USER_ID = output_usage.USER_ID " +
            "INNER JOIN (SELECT users.USER_ID, " +
            "COUNT(userdata_dir.USERDATA_ID) AS NUM_DOCUMENTS, " +
            "COALESCE(SUM(userdata_dir.SIZE), 0) AS TOTAL_LENGTH " +
            "FROM users LEFT JOIN userdata_dir ON users.USER_ID = userdata_dir.USER_ID " +
            "WHERE users.USER_ID = ? GROUP BY users.USER_ID) " +
            "AS data_dir_usage ON users.USER_ID = data_dir_usage.USER_ID " +
            "WHERE users.USER_ID = ?";

		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = dbConn.prepareStatement ( query );
		ResultSet answer = null;

		m_dataSize = 0;

		try
		{
			selectStmt.setLong ( 1, userid );
			selectStmt.setLong ( 2, userid );
			selectStmt.setLong ( 3, userid );
			selectStmt.setLong ( 4, userid );
			selectStmt.setLong ( 5, userid );

			answer = selectStmt.executeQuery();

			// Make sure there is at least one row...
			if ( answer.isBeforeFirst() )
			{
				// We will only take the first row
				answer.next();
				m_dataSize = answer.getLong ( 2 );
                //log.debug ( "MONA: m_dataSize = " + m_dataSize );
			}
		}
		finally
		{
			if ( answer != null )
				answer.close();
		}

		selectStmt.close();
		dbConn.close();

		return ( m_dataSize );
	}

	/**
	 * Function to query the database for the user's data size.
     * Mona - added inclusion of the new userdata_dir table's size column.
	 * @return long - total number of bytes; could be 0 if nothing found
	 **/
	public long queryDataSizeDU() throws SQLException, IOException, InterruptedException
	{
        //log.debug ( "MONA: entered User.queryDataSizeDU()" );
		//long userid = getUserId();
		String dused;
		String dusize;
                ProcessRunner pr = new ProcessRunner(true);
                //int exitCode = pr.run("du -sb " + getGlobusDirectory() + " 2>/dev/null");
                int exitCode = pr.run("du -sb " + getGlobusDirectory());
        	//log.debug( "after du, with exitCode (" + exitCode + ")" );
        	//log.debug( "pr.getStdOut(): (" + pr.getStdOut() + ")" );
        	//log.debug( "pr.getStdErr(): (" + pr.getStdErr() + ")" );
                //if (pr.getStdOut().length() == 0 || pr.getStdErr().length() > 0)
                if (pr.getStdOut().length() == 0)
                {
                        //throw new Exception("du says: " + pr.getStdOut() + "\n" + pr.getStdErr());
        	log.debug( "after du, with exitCode (" + exitCode + ")" );
        	log.debug( "pr.getStdOut(): (" + pr.getStdOut() + ")" );
        	log.debug( "pr.getStdErr(): (" + pr.getStdErr() + ")" );
			dused = "";
			dusize = "-1";
                } else {
			dused = pr.getStdOut();
			dusize = dused.split("\\s+")[0];
		}
		//log.debug("dused: (" + dused + ")");
		//log.debug("before Long.parseLong(" + dusize + ")");
		m_dataSizeDU = Long.parseLong(dusize);
        	//log.debug ( "dused = (" + dused + ")" );
        	//log.debug ( "dusize = (" + dusize + ")" );
        //log.debug ( "MONA: userid = " + userid );
		return (m_dataSizeDU);
	}

	public Date getDateCreated()
	{
		return m_dateCreated.getValue();
	}

	public String getFirstName()
	{
		return m_firstName.getValue();
	}

	public void setFirstName(String firstName)
	{
		m_firstName.setValue(firstName);
	}

	public String getLastName()
	{
		return m_lastName.getValue();
	}

	public void setLastName(String lastName)
	{
		m_lastName.setValue(lastName);
	}

	public String getUsername() { return m_username.getValue(); }
	public void setUsername(String username)
	{
		m_username.setValue(username);
	}


	/*
		Higher level code (see sdk.clients) ensures that REST_END_USER_UMBRELLA
		have usernames of the form <appname>.<end_username> where appname
		is just letters numbers and underscores.  We need to prefix usernames
		we get from umbrella apps with appname because username needs to be unique.
	*/
	public String getEndUsername()
	{
		if (getRole() == UserRole.REST_END_USER_UMBRELLA)
		{
			int dot = getUsername().indexOf('.');
			return getUsername().substring(dot + 1);
		}
		return getUsername();
	}

	/*
	public String getAppname() { return m_appname.getValue(); }
	public void setAppname(String username)
	{
		m_appname.setValue(username);
	}
	*/

	public String getPassword()
	{
		return m_password.getValue();
	}

	public void setPassword(String password)
	{
		m_password.setValue(StringUtils.getMD5HexString(password));
	}

	public String getInstitution()
	{
		return m_institution.getValue();
	}

	public void setInstitution(String institution)
	{
		m_institution.setValue(institution);
	}

	public UserRole getRole()
	{
		return UserRole.valueOf(m_role.getValue());
	}

	public void setRole(UserRole role)
	{
		m_role.setValue(role.toString());
	}

	public String getStreetAddress()
	{
		return m_streetAddress.getValue();
	}

	public void setStreetAddress(String streetAddress)
	{
		m_streetAddress.setValue(streetAddress);
	}

	public String getCity()
	{
		return m_city.getValue();
	}

	public void setCity(String city)
	{
		m_city.setValue(city);
	}

	public String getState()
	{
		return m_state.getValue();
	}

	public void setState(String state)
	{
		m_state.setValue(state);
	}

	public String getCountry()
	{
		return m_country.getValue();
	}

	public void setCountry(String country)
	{
		m_country.setValue(country);
	}

	public String getMailCode()
	{
		return m_mailCode.getValue();
	}

	public void setMailCode(String mailCode)
	{
		m_mailCode.setValue(mailCode);
	}

	public String getZipCode()
	{
		return m_zipCode.getValue();
	}

	public void setZipCode(String zipCode)
	{
		m_zipCode.setValue(zipCode);
	}

	public String getAreaCode()
	{
		return m_areaCode.getValue();
	}

	public void setAreaCode(String areaCode)
	{
		m_areaCode.setValue(areaCode);
	}

	public String getPhoneNumber()
	{
		return m_phoneNumber.getValue();
	}

	public void setPhoneNumber(String phoneNumber)
	{
		m_phoneNumber.setValue(phoneNumber);
	}

	public String getEmail()
	{
		return m_email.getValue();
	}

	public void setEmail(String email)
	{
		m_email.setValue(email);
	}

	public String getUmbrellaAppname()
	{
		return m_umbrellaAppname.getValue();
	}

	public void setUmbrellaAppname(String umbrellaAppname)
	{
		m_umbrellaAppname.setValue(umbrellaAppname);
	}

	public String getWebsiteUrl()
	{
		return m_websiteUrl.getValue();
	}

	public void setWebsiteUrl(String websiteUrl)
	{
		m_websiteUrl.setValue(websiteUrl);
	}

	public String getComment()
	{
		return m_comment.getValue();
	}

	public void setComment(String comment)
	{
		m_comment.setValue(comment);
	}

	public long getDefaultGroupId()
	{
		return m_defaultGroupId.getValue();
	}

	public void setDefaultGroupId(Long defaultGroupId)
	{
		m_defaultGroupId.setValue(defaultGroupId);
	}

	public Group getDefaultGroup() throws IOException, SQLException
	{
		if (m_defaultGroupId.isNull())
			return null;

		return new Group(m_defaultGroupId.getValue());
	}

	public void setDefaultGroup(Group defaultGroup)
	{
		if (defaultGroup != null && !defaultGroup.isNew())
			setDefaultGroupId(defaultGroup.getGroupId());
		else
			setDefaultGroupId(null);
	}

	public boolean isActive()
	{
		return m_active.getValue();
	}

	public boolean isUsUser ()
	{
		String country = this.getCountry();
		return (country == null)? false : (US_TERRITORIES.get(country.trim()) != null);
	}

	public void setActive(Boolean active)
	{
		m_active.setValue(active);
	}


    public String getGlobusDirectory()
    {
        //log.debug ( "MONA: User.getGlobusDirectory()" );
        String globus_root =
            Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
        //log.debug ( "MONA: globus_root = " + globus_root );
        String username = getUsername();
        //log.debug ( "MONA: globus_root = " + globus_root );
        return ( globus_root + "/" + username );
    }


	/**
	 * Updated by Mona to also check user preference for max data size setting.
	 * If user's cansubmit value is true but there is a problem checking the
	 * user preference exceed max setting, will let user submit.
	 * @return boolean - true if user can submit; false otherwise
	 **/
	public boolean canSubmit()
	{
		boolean submit = m_canSubmit.getValue();

		if ( submit )
		{
			String exceeds = null;

			try
			{
				Map<String, String> p = preferences();
				exceeds = p.get ( DATA_SIZE_EXCEEDS_MAX );
			}
			catch ( Exception e )
			{
				return ( true );
			}

			if ( exceeds == null || ! exceeds.equals ( "TRUE" ) )
				return ( true );
		}

            	log.debug("DATA_SIZE_EXCEEDS_MAX failed to validate user (" + getUsername() + ") with data used (" + getDataSize() + ")");
		return ( false );
	}
	
	public void setCanSubmit(Boolean canSubmit)
	{
		m_canSubmit.setValue(canSubmit);
	}

	public Date getLastLogin() { return m_lastLogin.getValue(); }
	public void setLastLogin(Date date) { m_lastLogin.setValue(date); }

	public Date getActivationSent() { return m_activationSent.getValue(); }
	public void setActivationSent(Date date) { m_activationSent.setValue(date); }

	public String getActivationCode() { return m_activationCode.getValue(); }
	public void setActivationCode(String code) { m_activationCode.setValue(code); }
	
	/**
	 * @return -1 if the user doesn't have a max size; 0 means the user is
	 * not allow to upload; otherwise returns the maximum upload size for
	 * the user in GB
	 */
	public int getMaxUploadSizeGB()
	{
		int answer = -1;
		
		if ( m_maxUploadSizeGB != null )
			answer = m_maxUploadSizeGB.getValue();
		
		return ( answer );
	}

	public void setMaxUploadSizeGB(int maxuploadsizegb)
	{
		m_maxUploadSizeGB.setValue(maxuploadsizegb);
	}

	public Folder getHomeFolder() throws IOException, SQLException
	{
		String path = FolderItem.SEPARATOR + FolderItem.escapePath(m_username.getValue());

		return Folder.findFolder(path);
	}

	public Set<Group> memberships() throws IOException, SQLException
	{
		if (m_memberships == null) {
			Set<Group> groupMemberships;

			if (!isNew())
				groupMemberships = Group.findMemberships(m_key.getValue());
			else
				groupMemberships = new TreeSet<Group>();

			m_memberships = new MembershipSet(groupMemberships);
		}

		return m_memberships;
	}

	public Map<String, String> preferences() throws IOException, SQLException
	{
		if (m_preferences == null) {
			Map<String, String> newPreferences = new TreeMap<String, String>();

			if (!isNew()) {
				Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
				PreparedStatement selectStmt = null;
				ResultSet prefRows = null;

				try {
					selectStmt = dbConn.prepareStatement("SELECT PREFERENCE, VALUE FROM user_preferences WHERE USER_ID = ?");

					m_key.setParameter(selectStmt, 1);

					prefRows = selectStmt.executeQuery();

					while (prefRows.next())
						newPreferences.put(prefRows.getString(1), prefRows.getString(2));
				}
				finally {
					if (prefRows != null)
						prefRows.close();

					if (selectStmt != null)
						selectStmt.close();

					dbConn.close();
				}
			}

			m_preferences = new PreferenceMap(newPreferences);
		}

		return m_preferences;
	}

	public List<Folder> findFolders() throws IOException, SQLException
	{
		if (isNew())
			return null;

		return Folder.findFolders(getKey());
	}

	public List<UserDataItem> findDataItems() throws IOException, SQLException
	{
		if (isNew())
			return null;

		return UserDataItem.findDataItems(getKey());
	}

	public List<Task> findTasks() throws IOException, SQLException
	{
		if (isNew())
			return null;

		return Task.findTasks(getKey());
	}




	/*******************************************************************************************************************
		Methods that return only activated users (i.e. activation_code == null)
	*******************************************************************************************************************/
	public static List<User> findActiveUsers() throws IOException, SQLException
	{
		//return findUsers(new NotNullCriterion("ACTIVATION_CODE", Types.VARCHAR));
		return findUsers(new StringCriterion("ACTIVATION_CODE", null));
	}
	public static User findUser ( String username ) throws IOException, SQLException
	{
		List<User> users = retrieveUsers(new StringCriterion("USERNAME", username), new StringCriterion("ACTIVATION_CODE", null));
		return (users == null || users.isEmpty())? null : users.get(0);
	}
	public static User findUserByEmail ( String email ) throws IOException, SQLException
	{
		List<User> users = retrieveUsers(new StringCriterion("EMAIL", email), new StringCriterion("ACTIVATION_CODE", null));
		return (users == null || users.isEmpty())? null : users.get(0);
	}
	/*
	* Email is unique for each role except REST_END_USER_UMBRELLA. For umbrella users, email is
	* unique for each value of umbrella_appname. umbrella_appname is empty except for
	* REST_END_USER_UMBRELLA accounts.
	*
	* This only returns the first user found so should not be called for REST_END_USER_UMBRELLA
	* users.
	*/
	public static User findUserByEmail ( String email, UserRole role ) throws IOException, SQLException
	{
		List<User> users;

		if (role == null)
		{
			users = retrieveUsers(new StringCriterion("EMAIL", email), new StringCriterion("ACTIVATION_CODE", null));
		}
		else
		{
			users = retrieveUsers(new StringCriterion("EMAIL", email), new StringCriterion("ROLE", role.toString()), new StringCriterion("ACTIVATION_CODE", null));
		}

		return (users == null || users.isEmpty())? null : users.get(0);
	}

	public static List<User> findActiveUsersByRole(UserRole role) throws IOException, SQLException
	{
		if (role == null)
			return findActiveUsers();

		return findUsers(new StringCriterion("ACTIVATION_CODE", null), new StringCriterion("ROLE", role.toString()));
	}
	public static User findUserByUsername(String username) throws IOException, SQLException
	{
		List<User> users = findUsers(new StringCriterion("USERNAME", username));
		for (User u : users)
		{
			if (u.getActivationCode() == null)
			{
				return u;
			}
		}
		return null;
	}
	// don't call this with role = umbrella because email isn't unique for umbrella users and this only returns the first.
	public static User findUserByEmailAndRole(String email, String role) throws IOException, SQLException
	{
		List<User> users = findUsers(new StringCriterion("EMAIL", email), new StringCriterion("ACTIVATION_CODE", null), new StringCriterion("ROLE", role.toString()));
		if (users.size() < 1)
		{
			return null;
		}
		return users.get(0);
	}


	/*******************************************************************************************************************
		Methods that return only inactive users (inactive means their account was never activated by email confirmation
		and the app is configured to require email confirmation)
	*******************************************************************************************************************/
	public static List<User> findInactiveUsers() throws IOException, SQLException
	{
		return findUsers(new NotNullCriterion("ACTIVATION_CODE", Types.VARCHAR));
	}
	public static List<User> findInactiveUsersByRole(UserRole role) throws IOException, SQLException
	{
		if (role == null)
			return findInactiveUsers();

		return findUsers(new NotNullCriterion("ACTIVATION_CODE", Types.VARCHAR), new StringCriterion("ROLE", role.toString()));
	}



	/*******************************************************************************************************************
		Methods that return both activated and unactivated user records.
	*******************************************************************************************************************/
	public static List<User> findAllUsers() throws IOException, SQLException
	{
		return findUsers();
	}

    /**
 *      * Retrieves all users with specified role.
 *           *
 *                * @param role
 *                     * @return
 *                          * @throws IOException
 *                               * @throws SQLException
 *                                    */
	public static List<User> findAllUsers ( UserRole role ) throws IOException, SQLException
	{
		if (role == null)
		{
			return retrieveUsers();
		}

		return retrieveUsers(new StringCriterion("ROLE", role.toString()));
	}

    /**
     * Queries the database for a list of users base on criterion/criteria.
     *
     * <pre>
     * If multiple criteria are specified, they will be AND'd together.
     * </pre>
     *
     * @param keys criteria
     * @return
     * @throws IOException
     * @throws SQLException
     */
    private static List<User> retrieveUsers ( Criterion ... keys ) throws IOException, SQLException
    {
        //logger.info("BEGIN: retrieveUsers(Criterion ...)::List<User>");

        // TODO: find a way to combine criterion with relational operators.

        StringBuilder stmtBuilder = new StringBuilder("SELECT " + KEY_NAME + " FROM " + TABLE_NAME);

        if (keys.length > 0)
        {
            stmtBuilder.append(" WHERE ");
            stmtBuilder.append(keys[0].getPhrase());

            for (int i = 1; i < keys.length; i += 1)
            {
                stmtBuilder.append(" AND ");
                stmtBuilder.append(keys[i].getPhrase());
            }
        }

        Connection dbConn = null;
        PreparedStatement selectStmt = null;
        ResultSet resultSet = null;

        try
        {
            //logger.debug("PreparedStatement >>>\n"+ stmtBuilder.toString());

            dbConn = ConnectionManager.getConnectionSource().getConnection();
            selectStmt = dbConn.prepareStatement(stmtBuilder.toString());

            int index = 1;

            for (int i = 0; i < keys.length; i += 1)
            {
                index = keys[i].setParameter(selectStmt, index);
            }

            resultSet = selectStmt.executeQuery();

            List<User> users = new ArrayList<User>();

            while (resultSet.next())
            {
                users.add(new User(dbConn, resultSet.getLong(1)));
            }

            //logger.info("END: retrieveUsers(Criterion ...)::List<User>");

            return users;
        }
        finally
        {
            if (resultSet != null)
            {
            	resultSet.close();
            }

            if (selectStmt != null)
            {
            	selectStmt.close();
            }

            dbConn.close();

            //logger.info("FINALLY: retrieveUsers(Criterion ...)::List<User>");
        }
    }
	public static List<User> findAllUsersByRole(UserRole role) throws IOException, SQLException
	{
		if (role == null)
			return findAllUsers();

		return findUsers(new StringCriterion("ROLE", role.toString()));
	}
	public static User findAllUserByUsername(String username) throws IOException, SQLException
	{
		List<User> users = findUsers(new StringCriterion("USERNAME", username));
		if (users.size() < 1)
		{
			return null;
		}
		return users.get(0);
	}
	/*
		Email is unique for each role except REST_END_USER_UMBRELLA.  For umbrella users, email is unique for each value of umbrella_appname.
		umbrella_appname is empty except for REST_END_USER_UMBRELLA accounts.

		This only returns the first user found so should not be called for REST_END_USER_UMBRELLA users.
	*/
	public static User findAllUserByEmailAndRole(String email, String role) throws IOException, SQLException
	{
		List<User> users = findUsers(new StringCriterion("EMAIL", email), new StringCriterion("ROLE", role.toString()));
		if (users.size() < 1)
		{
			return null;
		}
		return users.get(0);
	}



	/*
		Accepts multiple criterion that will be AND'd together.

		TODO: find a way to combine criterion with relational operators.
	*/
	private static List<User> findUsers(Criterion... keys) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder("SELECT " + KEY_NAME + " FROM " + TABLE_NAME);

		if (keys.length > 0) {
			stmtBuilder.append(" WHERE ");
			stmtBuilder.append(keys[0].getPhrase());

			for (int i = 1 ; i < keys.length ; i += 1) {
				stmtBuilder.append(" AND ");
				stmtBuilder.append(keys[i].getPhrase());
			}
		}

		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet userRow = null;

		try {
			selectStmt = dbConn.prepareStatement(stmtBuilder.toString());

			int index = 1;

			for (int i = 0 ; i < keys.length ; i += 1)
				index = keys[i].setParameter(selectStmt, index);

			userRow = selectStmt.executeQuery();

			List<User> users = new ArrayList<User>();

			while (userRow.next())
				users.add(new User(dbConn, userRow.getLong(1)));

			return users;
		}
		finally {
			if (userRow != null)
				userRow.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}
	}











	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof User == false)
			return false;

		User otherUser = (User) other;

		if (isNew() || otherUser.isNew())
			return false;

		return getUserId() == otherUser.getUserId();
	}

	@Override
	public int hashCode()
	{
		return (new Long(getUserId())).hashCode();
	}

	@Override
	public int compareTo(User other)
	{
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getUserId() - other.getUserId());
	}

	/**
		I'm adding a feature (for teragrid hosts) that lets us store user's personal account info and use it
		to run jobs.  For teragrid we just need to store a single "charge code" string so I'm storing it in
		the user preferences.

		The preference name has the form "account_<accountGroup>".  The same teragrid charge code is used
		for multiple teragrid hosts, so in the tool registry and ToolResource class I'm adding an accountGroup
		field.  For instance, both abe and lonestar will have accountGroup=teragrid.

		When a user edits his profile and enters his teragrid accountGroup we store it by calling:
			User.setAccount(User.TERAGRID, chargeCode)
		Pass in null for chargeCode to clear the value.

		This form can display the existing value if any with
			User.getAccount(User.TERAGRID).
		ProcessWorker's do:
			User.getAccount(ToolResource.getAccountGroup())

		Internally, we add the "account_" prefix to reduce the possibility of name clashes with other
		preferences.
	*/

	public void setAccount(String accountGroup, String chargeCode)
	{
		try
		{
			Map<String, String> p = preferences();
			accountGroup = "account_" + accountGroup;
			p.put(accountGroup, chargeCode);
			save();
		}
		catch(Exception e)
		{
			log.error("", e);
			throw new WorkbenchException("Internal Database Error");
		}
	}

	/**
		Returns null if not found.
	*/
	public String getAccount(String accountGroup)
	{
		try
		{
			Map<String, String> p = preferences();
			accountGroup = "account_" + accountGroup;
			return p.get(accountGroup);
		}
		catch(Exception e)
		{
			log.error("", e);
			throw new WorkbenchException("Internal Database Error");
		}
	}

	public void deleteData() throws IOException, SQLException
	{
		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

		try {
			dbConn.setAutoCommit(false);

			deleteData(dbConn);

			dbConn.commit();
		}
		catch (IOException | SQLException err) {
			log.error("", err);

			dbConn.rollback();

			throw err;
		}
		finally {
			dbConn.close();
		}
	}


	// package methods


	/*
		We don't accept usernames or email addresses with single quotes because
		we use single quotes to escape the field to the shell when invoking
		applications like deleteUser.py.  We check in save because this is
		where field length errors will also throw ValidationExceptions.
	*/
	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
			if (m_username.getValue().contains("'"))
			{
				throw new ValidationException("Username may not contain single quote characters.");
			}
			if (m_email.getValue().contains("'"))
			{
				throw new ValidationException("Email may not contain single quote characters.");
			}

			super.save(dbConn);
	}

	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
		super.load(dbConn);

		m_memberships = null;
		m_preferences = null;
	}

	@Override
	void delete(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

		delete(dbConn, m_key.getValue(), m_defaultGroupId.getValue());

		m_key.reset();
	}

	void deleteData(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

		deleteFolders(dbConn, getKey());

		(new CreateHomeFolderOp()).execute(dbConn);
	}

	static Set<User> findMembers(long groupId) throws IOException, SQLException
	{
		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet userRows = null;

		try {
			selectStmt = dbConn.prepareStatement(
					"SELECT USER_ID " +
					"FROM groups " +
						"INNER JOIN user_group_lookup ON groups.GROUP_ID = user_group_lookup.GROUP_ID " +
						"INNER JOIN " + TABLE_NAME + " ON user_group_lookup.USER_ID = " + TABLE_NAME + ".USER_ID " +
					"WHERE groups.GROUP_ID = ?"
			);

			selectStmt.setLong(1, groupId);

			userRows = selectStmt.executeQuery();

			Set<User> members = new TreeSet<User>();

			while (userRows.next())
				members.add(new User(dbConn, userRows.getLong(1)));

			return members;
		}
		finally {
			if (userRows != null)
				userRows.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}
	}

	static void delete(Connection dbConn, long userId) throws IOException, SQLException
	{
		long groupId = getDefaultGroupId(dbConn, userId);

		delete(dbConn, userId, groupId);
	}


	// protected methods


	@Override
	protected void pushInsertOps()
	{
		m_opQueue.push(new CreateHomeFolderOp());
		m_opQueue.push(new CreateDefaultGroupOp());

		super.pushInsertOps();
	}


	// private methods


	private static void delete(Connection dbConn, long userId, long defaultGroupId) throws IOException, SQLException
	{
		log.debug("In delete user for userid=" + userId);
		Criterion userKey = new LongCriterion(KEY_NAME, userId);

		unsetAdministrator(dbConn, userId);

		(new DeleteOp("user_preferences", userKey)).execute(dbConn);
		(new DeleteOp("user_group_lookup", userKey)).execute(dbConn);

		deleteFolders(dbConn, userKey);

		// TODO: make this conditional on role = UserRole.REST_ADMIN?
		deleteApplications(dbConn, userId);

		(new DeleteOp(TABLE_NAME, userKey)).execute(dbConn);

		Group.delete(dbConn, defaultGroupId);
	}

	/*
	Registration process with email activation

		- If user is required to activate via emailed link after registration, then create user
		record with a non-null activation_code and activation_sent = time you send the activation
		code to the user.  If user isn't required to activate, then when he registers create user
		record with activation_code and activation_sent = null. ( Or just call User.confirmActivation() to do it).

		- Periodically find all expired registrations (activation_sent non null and older than some period of time)
		and delete these users.

		- When user clicks on his email link and the code in his link matches one of our activation codes,
		activate that user by setting his activation_code and activation_sent to null.

		- A user is "activated" if activation_code = null.   This works regardless of whether we're requiring email
		activation or not.
	*/
	public static List <User> findExpiredRegistrations(long numberOfHours) throws IOException, SQLException
	{
		Criterion criterion = new OlderThanCriterion("ACTIVATION_SENT", numberOfHours, "HOUR");
		return findUsers(criterion);
	}

	public void confirmActivation()
	{
		setActivationCode(null);
		setActivationSent(null);
	}

	public void setActivation(String code)
	{
		setActivationCode(code);
		setActivationSent(new Date());
	}

	public boolean isActivated()
	{
		return (getActivationCode() == null);
	}

	private static long getDefaultGroupId(Connection dbConn, long userId) throws IOException, SQLException
	{
		Column<Long> defaultGroupId = new LongColumn("DEFAULT_GROUP_ID", true);
		Criterion userKey = new LongCriterion(KEY_NAME, userId);

		(new SelectOp(TABLE_NAME, userKey, defaultGroupId)).execute(dbConn);

		return defaultGroupId.getValue();
	}

	private static void unsetAdministrator(Connection dbConn, long userId) throws IOException, SQLException
	{
		Criterion key = new LongCriterion("ADMINISTRATOR", userId);
		Column<Long> nullAdmin = new LongColumn("ADMINISTRATOR", true);

		(new UpdateOp("groups", key, nullAdmin)).execute(dbConn);
	}

	private static void deleteFolders(Connection dbConn, Criterion userKey) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder();

		stmtBuilder.append("SELECT FOLDER_ID FROM folders WHERE ");
		stmtBuilder.append(userKey.getPhrase());

		PreparedStatement selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
		ResultSet folderRows = null;

		try {
			userKey.setParameter(selectStmt, 1);

			folderRows = selectStmt.executeQuery();

			while (folderRows.next())
				Folder.delete(dbConn, folderRows.getLong(1));
		}
		finally {
			if (folderRows != null)
				folderRows.close();

			selectStmt.close();
		}
	}

	private static void deleteApplications(Connection dbConn, long userId) throws IOException, SQLException
	{
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT NAME FROM applications WHERE AUTH_USER_ID = ?");
		ResultSet apps = null;

		try {
			selectStmt.setLong(1, userId);

			apps = selectStmt.executeQuery();

			while (apps.next())
			{
				log.debug("Deleting application " + apps.getString(1) + " associated with userid=" + userId);
				Application.delete(dbConn, apps.getString(1));
			}
		}
		finally {
			if (apps != null)
				apps.close();

			selectStmt.close();
		}
	}
}
