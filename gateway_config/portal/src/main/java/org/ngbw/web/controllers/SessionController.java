package org.ngbw.web.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.common.util.SendError;
import org.ngbw.sdk.UserAuthenticationException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.common.util.ValidationResult;
import org.ngbw.sdk.core.shared.UserRole;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.Group;
import org.ngbw.sdk.database.Sso;
import org.ngbw.sdk.database.User;


/**
 * Controller class to handle NGBW web application user session management. Parent class of all NGBW
 * web controller classes.
 *
 * @author Jeremy Carver
 */
public class SessionController
{
    // folder description preference key
    public static final String DESCRIPTION = "description";

    // default guest folder properties
    public static final String GUEST_FOLDER_LABEL = "Guest Folder";
    public static final String GUEST_FOLDER_DESCRIPTION
            = "This folder organizes all of your workbench data and tasks.";

    // current workbench session
    private WorkbenchSession workbenchSession;

    private static final Log logger = LogFactory.getLog(SessionController.class.getName());


    /**
     * Default constructor. A SessionController object should only be instantiated with this
     * constructor if it represents a workbench session in which the user is not yet authenticated.
     */
    public SessionController ()
    {
        workbenchSession = null;
    }


    /**
     * Constructor taking an authenticated WorkbenchSession argument. The argument must be a valid
     * WorkbenchSession object in order to establish the proper connection with the model layer that
     * is required to interact with the workbench.
     *
     * @param workbenchSession	The WorkbenchSession object representing the current authenticated
     *                         user's workbench session.
     *
     * @throws IllegalArgumentException	if the provided WorkbenchSession object is null.
     */
    public SessionController ( WorkbenchSession workbenchSession ) throws IllegalArgumentException
    {
        if (workbenchSession == null)
        {
            throw new IllegalArgumentException("The provided WorkbenchSession is null.");
        }
        else
        {
            this.workbenchSession = workbenchSession;
        }
    }


    /**
     * Retrieves the current authenticated user's workbench session.
     *
     * @return	The WorkbenchSession object representing the current authenticated user's workbench
     *         session.
     */
    public WorkbenchSession getWorkbenchSession ()
    {
        return workbenchSession;
    }


    /**
     * Retrieves the currently active workbench interface.
     *
     * @return	The Workbench object associated with the current user session. Returns null if an
     *         error occurs.
     */
    public Workbench getWorkbench ()
    {
        try
        {
            WorkbenchSession session = getWorkbenchSession();
            if (session == null)
            {
                return Workbench.getInstance();
            }
            else
            {
                return session.getWorkbench();
            }
        }
        catch ( Throwable error )
        {
            reportError(error, "Error retrieving Workbench");
            return null;
        }
    }


    /**
     * Determines whether an authenticated user is currently logged in.
     *
     * @return	true if a user is currently logged in to the application, false otherwise.
     */
    public boolean isAuthenticated ()
    {
        return (getWorkbenchSession() != null);
    }


    /**
     * Retrieves the current authenticated user.
     *
     * @return	The User object representing the current authenticated user. Returns null if no
     *         authenticated workbench session is present, or if an error occurs.
     */
    public User getAuthenticatedUser ()
    {
        WorkbenchSession session = getWorkbenchSession();
        
        if (session == null)
        {
            logger.debug("The current user could not be retrieved "
                            + "because the workbench session is null.");
            return null;
        }
        else
        {
            try
            {
                return session.getUser();
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving authenticated user");
                return null;
            }
        }
    }


    /**
     * Retrieves the current authenticated user's username.
     *
     * @return	The current authenticated user's username. Returns null if no authenticated workbench
     *         session is present, or if an error occurs.
     */
    public String getAuthenticatedUsername ()
    {
        WorkbenchSession session = getWorkbenchSession();
        if (session == null)
        {
            logger.debug("The current user's username could not be retrieved "
                    + "because the workbench session is null.");
            return null;
        }
        else
        {
            try
            {
                return session.getUsername();
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving authenticated user's username");
                return null;
            }
        }
    }


    /**
     * Retrieves the current authenticated user's (encrypted) password.
     *
     * @return	The current authenticated user's encrypted password. Returns null if no authenticated
     *         workbench session is present, or if an error occurs.
     */
    public String getAuthenticatedPassword ()
    {
        User user = getAuthenticatedUser();
        if (user == null)
        {
            return null;
        }
        else
        {
            return user.getPassword();
        }
    }


    /**
     * Retrieves the current authenticated user's role. This is used to determine the user's web
     * application permissions and functionality.
     *
     * @return	The UserRole object representing the current authenticated user's role in the
     *         application. Returns null if no authenticated workbench session is present, or if an
     *         error occurs.
     */
    public UserRole getAuthenticatedUserRole ()
    {
        WorkbenchSession session = getWorkbenchSession();
        if (session == null)
        {
            logger.debug("The current user's role could not be retrieved "
                    + "because the workbench session is null.");
            return null;
        }
        else
        {
            try
            {
                return session.getUserRole();
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving authenticated user's role");
                return null;
            }
        }
    }


    /**
     * Retrieves the set of groups to which the current authenticated user belongs.
     *
     * @return	The Set of all NGBW user Groups that the current authenticated user is a member of.
     *         Returns null if no authenticated workbench session is present, or if an error occurs.
     */
    public Set<Group> getAuthenticatedUserGroups ()
    {
        WorkbenchSession session = getWorkbenchSession();
        if (session == null)
        {
            logger.debug("The current user's set of groups could not be retrieved "
                    + "because the workbench session is null.");
            return null;
        }
        else
        {
            try
            {
                return session.getGroups();
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving authenticated user's role");
                return null;
            }
        }
    }

    /**
     * Determines whether the current user is registered. Registered users have access to a greater
     * variety of features and functionality than guest users.
     *
     * @return	true if the current user is registered, false otherwise.
     */
    public boolean isRegistered ()
    {
        UserRole role = getAuthenticatedUserRole();
        if (role == null || role.equals(UserRole.GUEST))
        {
            return false;
        }
        else
        {
            return true;
        }
    }


    /**
     * Retrieves the specified user preference for the current authenticated user.
     *
     * @param preference	The key or label of the preference to be retrieved.
     *
     * @return	The value of the specified user preference. Returns null if no authenticated user is
     *         logged in, if the argument preference key is null, or an error occurs.
     */
    public String getUserPreference ( String preference )
    {
        User user = getAuthenticatedUser();
        if (user == null)
        {
            logger.debug("User preference could not be retrieved "
                    + "because no authenticated user is present.");
            return null;
        }
        else if (preference == null)
        {
            logger.debug("User preference could not be retrieved "
                    + " because the provided preference key is null.");
            return null;
        }
        else
        {
            try
            {
                user.load();
                return user.preferences().get(preference);
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving user preference \"" + preference + "\"");
                return null;
            }
        }
    }


    /**
     * Saves the specified preference value to the currently authenticated user's stored map of user
     * preferences.
     *
     * @param preference	The key or label of the preference to be saved.
     * @param value			   The value of the preference to be saved.
     *
     * @return	true if the user preference was successfully saved, false otherwise
     */
    public boolean setUserPreference ( String preference, String value )
    {
        if (value == null)
        {
            return clearUserPreference(preference);
        }
        User user = getAuthenticatedUser();
        if (user == null)
        {
            logger.debug("User preference could not be written because no "
                    + "authenticated user is present.");
            return false;
        }
        else if (preference == null)
        {
            logger.debug("User preference could not be written "
                    + " because the provided preference key is null.");
            return false;
        }
        else
        {
            try
            {
                user.preferences().put(preference, value);
                getWorkbenchSession().updateUser(user);
                info("User preference \"" + preference + "\" -> \"" + value
                        + "\" successfully written.");
                return true;
            }
            catch ( Throwable error )
            {
                reportError(error, "Error writing user preference \"" + preference
                            + "\" -> \"" + value + "\"");
                return false;
            }
        }
    }


    /**
     * Removes the specified preference value from the currently authenticated user's stored map of
     * user preferences.
     *
     * @param preference	The key or label of the preference to be cleared.
     * @param value			   The value of the preference to be cleared.
     *
     * @return	true if the user preference was successfully cleared, false otherwise
     */
    public boolean clearUserPreference ( String preference )
    {
        User user = getAuthenticatedUser();
        if (user == null)
        {
            logger.debug("User preference could not be removed because no "
                    + "authenticated user is present.");
            return false;
        }
        else if (preference == null)
        {
            logger.debug("User preference could not be removed "
                    + " because the provided preference key is null.");
            return false;
        }
        else
        {
            try
            {
                user.preferences().remove(preference);
                getWorkbenchSession().updateUser(user);
                info("User preference \"" + preference + "\" successfully removed.");
                return true;
            }
            catch ( Throwable error )
            {
                reportError(error, "Error removing user preference \"" + preference + "\"");
                return false;
            }
        }
    }


    /**
     * Retrieves the registered User with the specified username.
     *
     * @param username	The username of the user to be retrieved.
     *
     * @return	The user with the specified username, if present. Returns null if no user exists with
     *         this username, if the argument username is null, or an error occurs.
     */
    public User getUserByUsername ( String username )
    {
        if (username == null)
        {
            logger.debug("User could not be retrieved "
                            + " because the provided username is null.");
            return null;
        }
        else
        {
            try
            {
                return User.findUser(username);
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving user with username \"" + username + "\"");
                return null;
            }
        }
    }


    /**
     * Retrieves the registered User with the specified email address.
     *
     * @param email	The email address of the user to be retrieved.
     *
     * @return	The user with the specified email address, if present. Returns null if no user exists
     *         with this email address, if the argument email address is null, or an error occurs.
     */
    public User getUserByEmail ( String email )
    {
        if (email == null)
        {
            logger.debug("User could not be retrieved "
                    + " because the provided email address is null.");
            return null;
        }
        else
        {
            try
            {
                //return User.findUserByEmailAndRole(email, UserRole.STANDARD.toString());
                return User.findUserByEmail(email);
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving user with email address \"" + email + "\" and standard role.");
                return null;
            }
        }
    }


    /**
     * Determine whether the current has missing required profile information. The only thing
     * currently required is that STANDARD users must have institution and country information.
     * Other types of users will be ignored; including GUEST users who are NOT allowed to update
     * their profile information.
     *
     * @author - Mona Wong
     * @return - boolean, true if user has missing info; FALSE otherwise.
     */
    public boolean needProfileInfo ()
    {
        User user = getAuthenticatedUser();

        if (user == null)
        {
            return true;
        }

        else
        {
            String inst = user.getInstitution();
            String country = user.getCountry();
            UserRole role = user.getRole();

            if (role == UserRole.STANDARD
                    && (inst == null || inst.trim().equals("")
                        || country == null || country.trim().equals("")))
            {
                return true;
            }

            else
            {
                return false;
            }
        }
    }


    // * ================================================================ 
    // * Functionality Methods
    // * ================================================================
    
    /**
     * Creates and registers a fully populated workbench User object for the user with the specified
     * information. It is assumed that the argument values have already been properly validated
     * according to the client's particular business rules.
     *
     * @param username		  The username of the user attempting to register.
     * @param firstName		 The first name of the user attempting to register.
     * @param lastName		  The last name of the user attempting to register.
     * @param email			    The email address of the user attempting to register.
     * @param country
     * @param institution	The primary institution with which the user attempting to register is
     *                    affiliated.
     *
     * @return	The ValidationResult object representing the outcome of the user account
     *         registration. Returns null if an error occurs.
     */
    // This is called for iplant users.
    public ValidationResult registerUser ( String username, String email, String firstName, String lastName,
                                           String institution, String country )
    {
        // Make up a random password.
        String password = "Iplant" + username + Calendar.getInstance().getTimeInMillis();
        return doRegisterUser(username, password, email, firstName, lastName, institution, country, UserRole.STANDARD, null);
    }


    public ValidationResult registerUser ( String username, String password, String email,
                                           String firstName, String lastName, String institution, String country )
    {
        return doRegisterUser(username, password, email, firstName, lastName, institution, country, UserRole.STANDARD, null);
    }

    public ValidationResult registerUser ( String username, String password, String email,
                                           String firstName, String lastName, String institution, String country, String comment )
    {
        return doRegisterUser(username, password, email, firstName, lastName, institution, country, UserRole.STANDARD, comment);

    }

    public ValidationResult registerUser ( String username, String password, String email,
                                           String firstName, String lastName, String institution, String country, UserRole role )
    {
        return doRegisterUser(username, password, email, firstName, lastName, institution, country, role, null);
    }


    private ValidationResult doRegisterUser ( String username, String password, String email,
                                              String firstName, String lastName, String institution, String country, UserRole role, String comment )
    {
        if (StringUtils.isNullOrEmpty(username, true))
        {
            logger.error("User account could not be created because the provided username is null.");
            return null;
        }
        else if (StringUtils.isNullOrEmpty(password, true))
        {
            logger.error("User account could not be created because the provided password is null.");
            return null;
        }
        else if (StringUtils.isNullOrEmpty(lastName, true))
        {
            logger.error("User account could not be created because the provided last name is null.");
            return null;
        }
        else if (StringUtils.isNullOrEmpty(firstName, true))
        {
            logger.error("User account could not be created because the provided first name is null.");
            return null;
        }
/*
        else if (StringUtils.isNullOrEmpty(country, true))
        {
            logger.error("User account could not be created because the country is not specified.");
            return null;
        }
*/
        try
        {
            User user = new User(); // workbench.getNewUserInstance();
            user.setUsername(username.toLowerCase());
            user.setPassword(password);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setCountry(country);
            user.setRole(role);
            logger.info("doRegisterUser() comment = " + comment);
            if (comment != null) {
                user.setComment(comment);
            }

            // Instituion field is now required but we'll leave this test
            if (!StringUtils.isNullOrEmpty(institution, true))
            {
                user.setInstitution(institution);
            }

            // register user
            Workbench workbench = getWorkbench();
            logger.info("doRegisterUser() workbench = " + (workbench == null? "null": "not null"));
            ValidationResult result = workbench.registerNewUser(user);
            logger.info("doRegisterUser() after registerNewUser user = " + user.getUsername());

            // log in newly registered user
            if (result.isValid())
            {
                logger.info ( "User account \"" + username + "\" (role \"" +
                    role.toString() + "\") successfully created." );

                // This call will send email to admin to notify of new account
                SendError.send ( "New account was created for " + username + " with email: " + email );

                login(username, password);
            }

            return result;
        }
        catch ( IOException | SQLException | UserAuthenticationException error )
        {
            reportError(error, "Error creating account for user \"" + username + "\"");
        }

        return null;
    }


    public User getCorrespondingCipresUser ( String iplantName ) throws Exception
    {
        Workbench workbench = getWorkbench();
        Sso sso = Sso.findSsoBySsoUsername(iplantName);
        if (sso != null)
        {
            return sso.getUser();
        }
        return null;
    }


    /**
     * Creates and registers a fully populated workbench User object with a randomized username,
     * representing a guest user account.
     *
     * @return	The ValidationResult object representing the outcome of the guest account
     *         registration. Returns null if an error occurs.
     */
    public ValidationResult registerGuestUser ()
    {
        logger.info("BEGIN: registerGuestUser()::ValidationResult");
        
        String guestName = "guest-" + Calendar.getInstance().getTimeInMillis();
        logger.debug("Guest name: " + guestName);

        // set password and email to numerical portion of guest username
        String guestNumber = guestName.replaceAll("\\D*", "");

        ValidationResult result = registerUser(guestName, guestNumber, guestNumber,
                                               "Guest", "User", null, "US", UserRole.GUEST);
        
        WorkbenchSession session = getWorkbenchSession();
        
//        logger.debug("ValidationResult is " + ((result == null)? "NULL" : "NOT NULL."));
//        logger.debug("ValidationResult is " + ((result != null && result.isValid())? "VALID" : "NULL/INVALID."));
//        logger.debug("WorkbenchSession is " + ((session == null)? "NULL" : "NOT NULL."));
        
//        if (result != null && !result.isValid())
//        {
//            logger.error("Error(s) in ValidationResult ...");
//            for (String err : result.getErrors())
//            {
//                logger.error(err);
//            }
//        }
        
        if (result != null && result.isValid() && session != null)
        {
            try
            {
                Folder guestFolder = session.getFolderInstance();
                guestFolder.setLabel(GUEST_FOLDER_LABEL);
                guestFolder.preferences().put(DESCRIPTION, GUEST_FOLDER_DESCRIPTION);
                session.saveFolder(guestFolder);
                info("Default folder successfully created for guest user \"" + guestName + "\".");
            }
            catch ( Throwable error )
            {
                reportError(error, "Error creating default folder for guest user \"" + guestName + "\"");
            }
        }
        
        logger.info("END: registerGuestUser()::ValidationResult");
        
        return result;
    }


    /**
     * Logs the specified user into the NGBW web application.
     *
     * @param username	The username of the user attempting to log in.
     *
     * @return	The WorkbenchSession object representing the newly activated user session, if the
     *          user successfully passes authentication. Returns null if an error occurs.
     *
     * @throws	UserAuthenticationException if the user supplies an invalid username and/or password.
     */
    public WorkbenchSession login ( String username ) throws UserAuthenticationException
    {
        return doLogin(username, null, null);
    }


    public WorkbenchSession login ( String username, String password ) throws UserAuthenticationException
    {
        if (password == null)
        {
            logger.debug("User could not be logged in because the provided password is null.");
            return null;
        }

        return doLogin(username, null, password);
    }
    
    
    public WorkbenchSession loginWithEmail ( String email, String password ) throws UserAuthenticationException
    {
        logger.info("BEGIN: loginWithEmail(String, String)::WorkbenchSession");
        
        if (password == null)
        {
            logger.debug("User could not be logged in because the provided password is null.");
            return null;
        }
        
        WorkbenchSession wb = doLogin(null, email, password);
        
        logger.info("END: loginWithEmail(String, String)::WorkbenchSession");
        
        return wb;
    }

    
    /** 
     * Authenticates user by using either the username or email address. 
     * 
     * @param username
     * @param email
     * @param password
     * @return
     * @throws UserAuthenticationException 
     */
    private WorkbenchSession doLogin ( String username, String email, String password ) throws UserAuthenticationException
    {
        logger.info("BEGIN: doLogin(String, String, String)::WorkbenchSession");
        
        logger.debug(String.format("Username=[%s], Email=[%s]", username, email));
        
        if (username == null && email == null)
        {
            logger.debug("User could not be logged in because both provided username and email are null.");
            return null;
        }
        else
        {
            try
            {
                if (username == null || username.trim().isEmpty()) 
                {
                    logger.debug(String.format("Finding user by email [%s] ...", email));
                    User user = User.findUserByEmail(email);
                    
                    if (user != null)
                    {
                        logger.debug(String.format("Finding user by email [%s] ... found.", email));
                        username = user.getUsername();
                    } 
                    else {
                        logger.debug(String.format("Finding user by email [%s] ... not found!", email));
                    }
                }
                
                Workbench workbench = getWorkbench();

                if (workbench.hasActiveSession(username))
                {
                    // TODO: do the right thing 
                    // OK, but what is the right thing? 
                    logger.error("User \"" + username + "\" is attempting to log in, but "
                                 + "an active session already exists for this user.  This session "
                                 + "is being suspended.");
                    
                    workbench.suspendSession(username);
                }

                WorkbenchSession session = null;

                if (password == null)
                {
                    logger.debug("Getting WorkbenchSession with username only ...");
                    session = workbench.getSession(username);
                }
                else
                {
                    logger.debug("Getting WorkbenchSession with username and password ...");
                    session = workbench.getSession(username, password);
                }

                if (session == null)
                {
                    logger.debug("The WorkbenchSession retrieved when attempting to authenticate "
                                 + "user \"" + username + "\" is null.");
//                    return null;
                }
                else
                {
//                    workbenchSession = session;
//                    return workbenchSession;
                    logger.debug("WorkbenchSession retrieved successfully.");
                }

                workbenchSession = session;
            }
            catch ( UserAuthenticationException error )
            {
                reportCaughtError(error, "Error authenticating user \"" + username + "\"");
                throw new UserAuthenticationException("Sorry, either the account is not activated or the information you entered "
                        + "did not match our records!  If your account is not activated, please click the link in the activation email to activate it before trying to login again.", error);
            }
            catch ( IOException | SQLException error )
            {
                reportError(error, "Error authenticating user \"" + username + "\"");
//                return null;
            }
        }
        
        logger.info("END: doLogin(String, String, String)::WorkbenchSession");
        return workbenchSession; 
    }


    /**
     * Retrieves the active WorkbenchSession owned by the specified user, if the user is currently
     * logged into the NGBW web application.
     *
     * @param username			       The username of the user whose active session is to be retrieved.
     * @param encryptedPassword	The (encrypted) password of the user whose active session is to be
     *                          retrieved.
     *
     * @return	The WorkbenchSession object representing the currently active user session, if the
     *         user successfully passes authentication. Returns null if an error occurs.
     *
     * @throws	UserAuthenticationException	if the user supplies an invalid username and/or
     *                                     (encrypted) password.
     */
    public WorkbenchSession getActiveSession ( String username, String encryptedPassword ) throws UserAuthenticationException
    {
        if (username == null)
        {
            logger.debug("User's active session could not be retrieved "
                    + "because the provided username is null.");
            return null;
        }
        else if (encryptedPassword == null)
        {
            logger.debug("User's active session could not be retrieved "
                    + "because the provided password is null.");
            return null;
        }
        else
        {
            try
            {
                WorkbenchSession session = getWorkbench().getActiveSession(username, encryptedPassword);
                if (session == null)
                {
                    logger.debug("No active workbench session currently exists for  "
                            + "user \"" + username + "\".");
                    return null;
                }
                else
                {
                    workbenchSession = session;
                    logger.debug("Currently active session successfully retrieved for user \""
                            + username + "\".");
                    return workbenchSession;
                }
            }
            catch ( UserAuthenticationException error )
            {
                reportCaughtError(error, "Error retrieving active session for user \""
                                  + username + "\"");
                throw new UserAuthenticationException("Sorry, the information you entered "
                        + "did not match our records.  Please try again.", error);
            }
            catch ( Throwable error )
            {
                reportError(error, "Error retrieving active session for user \""
                            + username + "\"");
                return null;
            }
        }
    }


    /**
     * Logs the current authenticated user out of the NGBW web application. Any remaining
     * WorkbenchSession reference must still be cleaned up, however.
     *
     * @return	true if the user is successfully logged out, false otherwise.
     */
    public boolean logout ()
    {
        String username = getAuthenticatedUsername();
        
        if (username == null)
        {
            logger.debug("The current user could not be logged out because the user's username is null.");
            return false;
        }
        else
        {
            try
            {
                Workbench workbench = getWorkbench();
                if (workbench.hasActiveSession(username))
                {
                    workbench.suspendSession(username);
                    workbenchSession = null;
                    info("User \"" + username + "\" successfully logged out.");
                    return true;
                }
                else
                {
                    logger.debug("The current user could not be logged out because "
                            + "the user has no active session.");
                    return false;
                }
            }
            catch ( Throwable error )
            {
                reportError(error, "Error logging out user \"" + username + "\"");
                return false;
            }
        }
    }


    /**
     * Changes the specified user's password to the specified new value.
     *
     * @param username		  The username of the user whose password is to be changed.
     * @param newPassword	The new password to change to.
     *
     * @return	true if the user's password is successfully changed, false otherwise.
     */
    public boolean changePassword ( String username, String newPassword )
    {
        if (username == null)
        {
            logger.debug("The specified user's password could not be changed "
                    + "because the provided username is null.");
            return false;
        }
        else if (newPassword == null)
        {
            logger.debug("The specified user's password could not be changed "
                    + "because the provided new password is null.");
            return false;
        }
        else
        {
            try
            {
                getWorkbench().resetPasswordAdmin(username, newPassword);
                return true;
            }
            catch ( Throwable error )
            {
                reportError(error, "Error changing user \"" + username + "\"'s password");
                return false;
            }
        }
    }


    /**
     * Updates an existing user's password with the specified new password. It is assumed that the
     * argument values have already been properly validated according to the client's particular
     * business rules.
     *
     * @param oldPassword	The user's current password.
     * @param newPassword	The user's new password.
     *
     * @return	true if the user's password was successfully updated, false otherwise
     *
     * @throws UserAuthenticationException if the user supplies an invalid current password.
     */
    public boolean editPassword ( String oldPassword, String newPassword ) throws UserAuthenticationException
    {
        User user = getAuthenticatedUser();
        if (user == null)
        {
            logger.debug("User's password could not be updated because no "
                    + "authenticated user is present.");
            return false;
        }
        else if (oldPassword == null)
        {
            logger.debug("User\"" + user.getUsername() + "\"'s password could not "
                    + "be updated because the provided old password is null.");
            return false;
        }
        else if (newPassword == null)
        {
            logger.debug("User\"" + user.getUsername() + "\"'s password could not "
                    + "be updated because the provided new password is null.");
            return false;
        }
        else
        {
            try
            {
                getWorkbenchSession().resetPassword(oldPassword, newPassword);
                info("User \"" + user.getUsername() + "\"'s password successfully updated.");
                return true;
            }
            catch ( UserAuthenticationException error )
            {
                reportCaughtError(error, "Error updating user \"" + user.getUsername()
                                  + "\"'s password");
                throw new UserAuthenticationException("Sorry, the information you entered "
                        + "did not match our records.  Please try again.", error);
            }
            catch ( Throwable error )
            {
                reportCaughtError(error, "Error updating user \"" + user.getUsername()
                                  + "\"'s password");
                return false;
            }
        }
    }


    /**
     * Updates an existing user with the specified information. It is assumed that the argument
     * values have already been properly validated according to the client's particular business
     * rules.
     *
     * @param firstName		 The first name of the user to be edited.
     * @param lastName		  The last name of the user to be edited.
     * @param email			    The email address of the user to be edited.
     * @param institution	The primary institution with which the user to be edited is affiliated.
     * @param country
     * @param account
     *
     * @return	true if the user was successfully updated, false otherwise
     */
    public ValidationResult editUser ( String email, String firstName, String lastName,
                                       String institution, String country, String account )
    {
        User user = getAuthenticatedUser();
        
        ValidationResult result = new ValidationResult();
        
        if (user == null)
        {
            logger.debug("User could not be edited because no authenticated user is present.");
            result.addError("User could not be edited because no authenticated user is present.");
            return result;
        }
        else if (user.getRole() == UserRole.GUEST)
        {
            result.addError("Guest accounts can't be modified.  Please logout and register for a regular account.");
            return result;
        }
        
        try
        {
            User userFromEmail = User.findUserByEmail(email, UserRole.STANDARD);
            if (userFromEmail != null && userFromEmail.getUserId() != user.getUserId())
            {
                result.addError("A user with this email: " + email + " already exists!");
                return result;
            }

            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            if (institution != null && institution.trim().equals("") == false)
            {
                user.setInstitution(institution);
            }
            else
            {
                user.setInstitution(null);
            }

            if (country != null && !country.trim().isEmpty())
            {
                user.setCountry(country);
            }
            else
            {
                user.setCountry(null);
            }

//            if (user.getRole() == UserRole.GUEST)
//            {
//                result.addError("Guest accounts can't be modified.  Please logout and register for a regular account.");
//                return result;
//            }

            /*
             * Commented out following code because we now allow admin to change their own account
             * info also. if (user.getRole() != UserRole.STANDARD) { result.addError("Only
             * 'standard' Cipres web site accounts can be modified here. Your account has role " +
             * user.getRole().toString()); return result; }
             */

            // Make sure email isn't changed to email address already in use by another STANDARD user.
            /*
            User userFromEmail = User.findUserByEmail(user.getEmail(), UserRole.STANDARD);
            
            if (userFromEmail != null && userFromEmail.getUserId() != user.getUserId())
            {
                result.addError("A user with this email: " + user.getEmail() + " already exists!");
                return result;
            }*/

            // TODO: took this out.  We aren't letting users set their own tg account.
            user.setAccount("teragrid", account);

            // update user
            getWorkbenchSession().updateUser(user);
            info("User \"" + user.getUsername() + "\" successfully edited.");
            return result;
        }
        catch ( Throwable error )
        {
            reportError(error, "Error editing user \"" + user.getUsername() + "\"");
            result.addError("Error editing user \"" + user.getUsername() + "\"");
            return result;
        }
    }


    protected void info ( String message )
    {
        if (logger.isInfoEnabled())
        {
            logger.info(getUsernameString() + message);
        }
    }


    protected void debug ( String message )
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(getUsernameString() + message);
        }
    }


    protected void debug ( String message, Throwable error )
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(getUsernameString() + message, error);
        }
    }


    protected void reportCaughtError ( Throwable error, String message )
    {
        debug(message + ": " + error.getMessage());
    }


    protected void reportError ( Throwable error, String message )
    {
        debug(message + ": " + error.getMessage(), error);
    }


    private String getUsernameString ()
    {
        String username = null;
        if (workbenchSession != null)
        {
            try
            {
                username = workbenchSession.getUsername();
            }
            catch ( Throwable error )
            {
            }
        }
        if (username != null)
        {
            username += " - ";
        }
        else
        {
            username = "";
        }
        return username;
    }

}
