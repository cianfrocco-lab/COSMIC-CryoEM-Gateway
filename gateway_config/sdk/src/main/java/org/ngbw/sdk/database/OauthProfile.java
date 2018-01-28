package org.ngbw.sdk.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.ngbw.sdk.database.User;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Choonhan Youn
 *
 */

public class OauthProfile extends GeneratedKeyRow implements Comparable<OauthProfile> {

    private static final Log log = LogFactory.getLog(OauthProfile.class.getName());
    private static String accountingPeriodStart = null;

    private static final String TABLE_NAME = "oauth_profile";
    private static final String KEY_NAME = "PROFILE_ID"; // PROFILE_ID is the primary, auto inc key

    private final Column<Long> m_userId = new LongColumn("USER_ID", true);
    private final Column<String> m_identityid = new StringColumn("IDENTITY_ID", true, 255);
    private final Column<String> m_username = new StringColumn("USERNAME", true, 255);
    private final Column<String> m_linkusername = new StringColumn("LINK_USERNAME", true, 255);
    private final Column<String> m_firstname = new StringColumn("FIRST_NAME", false, 100);
    private final Column<String> m_lastname = new StringColumn("LAST_NAME", false, 100);
    private final Column<String> m_email = new StringColumn("EMAIL", true, 200);
    private final Column<String> m_institution = new StringColumn("INSTITUTION", true, 255);

    // constructors
    public OauthProfile()
    {
        super(TABLE_NAME, KEY_NAME);

        construct(m_userId, m_identityid, m_username,
                m_linkusername, m_firstname, m_lastname,
                m_email, m_institution);
    }

    public OauthProfile(long profileId) throws IOException, SQLException
    {
        this();
        m_key.assignValue(profileId);
        load();
    }

    OauthProfile(Connection dbConn, long profileId) throws IOException, SQLException
    {
        this();
        m_key.assignValue(profileId);
        load(dbConn);
    }


    // public methods
    public long getProfileId()
    {
        return m_key.getValue();
    }
    public long getUserId()
    {
        return m_userId.getValue();
    }
    public void setUserId(long userId)
    {
        m_userId.setValue(userId);
    }

    public String getIdentityId() { return m_identityid.getValue(); }
    public void setIdentityId(String identityId) { m_identityid.setValue(identityId);}

    public String getUsername()
    {
        return m_username.getValue();
    }
    public void setUsername(String username)
    {
        m_username.setValue(username);
    }

    public String getLinkUsername() { return m_linkusername.getValue(); }
    public void setLinkUsername(String linkUsername) { m_linkusername.setValue(linkUsername); }

    public String getFirstname() { return m_firstname.getValue(); }
    public void setFirstname(String firstname) { m_firstname.setValue(firstname);}

    public String getLastname() { return m_lastname.getValue(); }
    public void setLastname(String lastname) { m_lastname.setValue(lastname);}

    public String getEmail() { return m_email.getValue(); }
    public void setEmail(String email) { m_email.setValue(email);}

    public String getInstitution() { return m_institution.getValue(); }
    public void setInstitution(String institution) { m_institution.setValue(institution);}

    public User getUser() throws IOException, SQLException
    {
        return new User(m_userId.getValue());
    }


    public static List<OauthProfile> findAllOauthProfile() throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
        PreparedStatement selectStmt = null;
        ResultSet userRows = null;

        try {
            selectStmt = dbConn.prepareStatement("SELECT " + KEY_NAME + " FROM " + TABLE_NAME);
            userRows = selectStmt.executeQuery();
            List<OauthProfile> users = new ArrayList<OauthProfile>();

            while (userRows.next())
                users.add(new OauthProfile(dbConn, userRows.getLong(1)));

            return users;
        }
        finally {
            if (userRows != null)
                userRows.close();

            if (selectStmt != null)
                selectStmt.close();

            dbConn.close();
        }
    }

    public static OauthProfile findOauthprofileByIdentityId(String identityId) throws IOException, SQLException
    {
        return findOauthprofile(new StringCriterion("IDENTITY_ID", identityId));
    }

    /*
    public static void addProfile(OauthProfile profile) throws IOException, SQLException
    {
        log.debug("Adding linkage for globus user " + profile.getUsername());
        profile.save();
    }
    */

    @Override
    public boolean equals(Object other)
    {
        if (other == null)
            return false;

        if (this == other)
            return true;

        if (other instanceof OauthProfile == false)
            return false;

        OauthProfile otherUser = (OauthProfile) other;

        if (isNew() || otherUser.isNew())
            return false;

        return getProfileId() == otherUser.getProfileId();
    }

    @Override
    public int hashCode()
    {
        return (new Long(getProfileId())).hashCode();
    }

    @Override
    public int compareTo(OauthProfile other)
    {
        if (other == null)
            throw new NullPointerException("other");

        if (this == other)
            return 0;

        if (isNew())
            return -1;

        if (other.isNew())
            return 1;

        return (int) (getProfileId() - other.getProfileId());
    }

    // private methods
    private static OauthProfile findOauthprofile(Criterion key) throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

        try {
            Column<Long> profileId = new LongColumn(KEY_NAME, false);
            (new SelectOp(TABLE_NAME, key, profileId, false)).execute(dbConn);

            if (profileId.isNull())
                return null;

            return new OauthProfile(dbConn, profileId.getValue());
        }
        finally {
            dbConn.close();
        }
    }

}
