package edu.sdsc.globusauth.controller;

/**
 * Created by cyoun on 10/12/16.
 */

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import edu.sdsc.globusauth.model.OauthProfile;
import edu.sdsc.globusauth.model.TransferRecord;
import edu.sdsc.globusauth.model.User;
import edu.sdsc.globusauth.util.HibernateUtil;
import edu.sdsc.globusauth.util.StringUtils;
import edu.sdsc.globusauth.util.UserRole;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class ProfileManager extends HibernateUtil {
    public OauthProfile add(OauthProfile oathProfile) {
        User user = new User();
        String password = "Globus" + oathProfile.getUsername() + Calendar.getInstance().getTimeInMillis();
        user.setFirstName(oathProfile.getFirstName());
        user.setLastName(oathProfile.getLastName());
        user.setEmail(oathProfile.getEmail());
        user.setUsername(oathProfile.getUsername());
        user.setPassword(StringUtils.getMD5HexString(password));
        user.setRole(UserRole.STANDARD.toString());
        user.setActive(true);
        user.setCanSubmit(true);
        user.setUmbrellaAppname("");
        user.setVersion(1);
        //user.setDateCreated(new Date());

        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();
        session.save(user);
        oathProfile.setUserId(user.getId());
        session.save(oathProfile);
        session.getTransaction().commit();
        return oathProfile;
    }

    public OauthProfile addUser(OauthProfile oathProfile) {
        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();
        session.save(oathProfile);
        session.getTransaction().commit();
        return oathProfile;
    }

    public int updateLinkUsername(OauthProfile oathProfile) {
        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();

        String sql = "update OauthProfile set linkUsername = :linkusername"
                + " where identityId = :identityId";
        Query query = session.createQuery(sql);
        query.setParameter("linkusername", oathProfile.getLinkUsername());
        query.setParameter("identityId", oathProfile.getIdentityId());
        int result = query.executeUpdate();

        session.getTransaction().commit();
        return result;
    }

    public int update(OauthProfile oathProfile) {
        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();

        String sql = "update OauthProfile set email = :email, firstName = :fname, "
                + "lastName = :lname, institution = :ins"
                + " where identityId = :identityId";
        Query query = session.createQuery(sql);
        query.setParameter("email", oathProfile.getEmail());
        query.setParameter("fname", oathProfile.getFirstName());
        query.setParameter("lname", oathProfile.getLastName());
        query.setParameter("ins", oathProfile.getInstitution());
        query.setParameter("identityId", oathProfile.getIdentityId());
        int result = query.executeUpdate();

        session.getTransaction().commit();
        return result;
    }

    public int updateUser(OauthProfile oauthProfile) {
        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();

        //User update
        String u_sql = "update User set email = :email, firstName = :fname, "
                + "lastName = :lname, institution = :ins"
                + " where username = :userName";
        Query u_query = session.createQuery(u_sql);
        u_query.setParameter("email", oauthProfile.getEmail());
        u_query.setParameter("fname", oauthProfile.getFirstName());
        u_query.setParameter("lname", oauthProfile.getLastName());
        u_query.setParameter("ins", oauthProfile.getInstitution());
        u_query.setParameter("userName", oauthProfile.getUsername());
        int result = u_query.executeUpdate();

        session.getTransaction().commit();
        return result;
    }

    public OauthProfile load(String identityId) {
        OauthProfile profile = null;
        Session session = HibernateUtil.getSessionFactory().openSession(); //getCurrentSession();
        session.beginTransaction();
        Query query = session.createQuery("FROM OauthProfile WHERE identityId = :identity");
        query.setParameter("identity",identityId);
        List<OauthProfile> profiles = query.list();
        if (profiles != null && profiles.size() > 0)
            profile = (OauthProfile)profiles.get(0);
        session.getTransaction().commit();
        return profile;
    }

    public void addRecord(TransferRecord tr) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        session.beginTransaction();
        session.save(tr);
        session.getTransaction().commit();
    }

    public int updateRecord(TransferRecord tr) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        session.beginTransaction();

        //Record update
        String u_sql = "update TransferRecord set status = :status, completionTime = :ctime, "
                + "filesTransferred = :ft, faults = :faults, directories = :dir, files = :file, "
                + "filesSkipped = :fs, byteTransferred = :bt"
                + " where taskId = :taskId";
        Query u_query = session.createQuery(u_sql);
        u_query.setParameter("status", tr.getStatus());
        u_query.setParameter("ctime", tr.getCompletionTime());
        u_query.setParameter("ft", tr.getFilesTransferred());
        u_query.setParameter("faults", tr.getFaults());
        u_query.setParameter("dir", tr.getDirectories());
        u_query.setParameter("file", tr.getFiles());
        u_query.setParameter("fs", tr.getFilesSkipped());
        u_query.setParameter("bt", tr.getByteTransferred());
        u_query.setParameter("taskId", tr.getTaskId());
        int result = u_query.executeUpdate();

        session.getTransaction().commit();
        return result;
    }

    public List<String> loadRecord(Long userId) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        session.beginTransaction();

        Query query = session.createQuery("SELECT taskId FROM TransferRecord WHERE userId = :userid AND (status = :status1 OR status = :status2)");
        query.setParameter("userid",userId);
        query.setParameter("status1","ACTIVE");
        query.setParameter("status2","INACTIVE");
        List<String> trs = query.list();

        session.getTransaction().commit();
        return trs;
    }
}
