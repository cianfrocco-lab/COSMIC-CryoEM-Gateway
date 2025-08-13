/*
 * Client.java
 */
package org.ngbw.sdk.clients;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.ValidationException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.api.tool.FieldError;
import org.ngbw.sdk.common.util.CountryCode;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.core.shared.UserRole;
import org.ngbw.sdk.database.User;

import org.cipres.utils.MailService;

import org.springframework.context.support.ClassPathXmlApplicationContext;


/**
 * @author Terri Liebowitz Schwartz
 *
 */

/*
 * There are 2 roles associated with REST service accounts: REST_END_USER_UMBRELLA - see
 * UmbrellaUser.java REST_USER Use this Client class to create and update User records of type
 * REST_USER
 */
public class Client
{
    private static final Log logger = LogFactory.getLog(Client.class.getName());
    
    public static final String USERNAME_ERR_MSG = 
            String.format("Username must be %d - %d characters long.  Only letters, numbers, hyphens, and underscores are allowed.",
                          StringUtils.MIN_USERNAME_LENGTH, StringUtils.MAX_USERNAME_LENGTH);
    
    public static final String EMPTY_EMAIL_ERR_MSG = "A valid email address, that you regularly check, is required.";
    public static final String INVALID_EMAIL_ERR_MSG = "Invalid email address."; 


    /*
     * Can't change role associated with a user.
     */
    public static User updateUser (
            User user,
            String password,
            String first_name,
            String last_name,
            String institution,
            String street_address,
            String city,
            String state,
            String country,
            String mailcode,
            String zip_code,
            String area_code,
            String phone_number,
            String email,
            String website_url,
            String comment,
            String xsedeId,
            String canSubmit ) throws Exception
    {

        // Can throw ValidationException
        user = validate(user, password, first_name, last_name, institution,
                        street_address, city, state, country, mailcode, zip_code, area_code, phone_number, email, website_url, comment,
                        xsedeId, canSubmit);
        /*
         * Will throw a ValidationException if any of the string fields are too long. I didn't want
         * to duplicate the checking that our db layer does in the validate method. Also, email and
         * username may have been unique at the time we checked but are no longer unique when we
         * save to the db, in which case a mysql exception will be thrown here. Todo: look for
         * constraint violation exception and map to a ValidationException?
         */
        //user.setLastLogin(new Date());
        user.save();
        return user;
    }


    /*
     * If this is used by a client registration app and the app wants us to require email
     * verification to complete registration, app must send an activationUrl that points to a page
     * on the registration web site.
     */
    public static User createUser (
            String username,
            String password,
            // UserRole role,
            String first_name,
            String last_name,
            String institution,
            String street_address,
            String city,
            String state,
            String country,
            String mailcode,
            String zip_code,
            String area_code,
            String phone_number,
            String email,
            String website_url,
            String comment,
            Boolean activationRequired ) throws Exception
    {
        logger.info("BEGIN: createUser(String...)::User");
        
        User user = null;        
        List<FieldError> errors = new ArrayList<FieldError>();

        // validate username
        if (username == null || username.trim().isEmpty())
        {
            logger.debug("username is missing");
            errors.add(new FieldError("username", "Username is required."));
        }
        else
        {
            // gets active and not yet activated users
            //if (User.findAllUserByUsername(username) != null)
            if (User.findUser(username) != null)
            {
                errors.add(new FieldError("username", "Username is already taken."));
            }
            else if (!StringUtils.isValidFormat(StringUtils.USERNAME_PATTERN, username.trim()))
            {
                errors.add(new FieldError("username", USERNAME_ERR_MSG));
            }
            else
            {
                user = new User();
                user.setUsername(username);
                user.setRole(UserRole.REST_USER);
                user.setCanSubmit(true);

                try
                {
                    user = validate(user, password, first_name, last_name, institution,
                                    street_address, city, state, country, mailcode, zip_code, area_code, phone_number, email, website_url, comment,
                                    null, null);
                    
                    if (activationRequired)
                    {
                        user.setActivation(makeActivationCode(user.getUsername()));
                    }
                    
                    user.save();
                }
                catch ( ValidationException ve )
                {
                    errors.addAll(ve.fieldError);
                }
            }
        }
        
        if (!errors.isEmpty())
        {
            throw new ValidationException(errors);
        }
        
        logger.info("END: createUser(String...)::User");
        
        return user;
    }


    private static User validate (
            User user,
            String password,
            String first_name,
            String last_name,
            String institution,
            String street_address,
            String city,
            String state,
            String country,
            String mailcode,
            String zip_code,
            String area_code,
            String phone_number,
            String email,
            String website_url,
            String comment,
            String xsedeId,
            String canSubmit ) throws Exception
    {
        /*
         * This method is called by both create and update, so only set fields in user object if we
         * received a non null value for them; otherwise, leave them unchanged.
         */
        if (first_name != null)
        {
            user.setFirstName(first_name);
        }
        if (last_name != null)
        {
            user.setLastName(last_name);
        }
        if (password != null)
        {
            user.setPassword(password);
        }
        if (institution != null)
        {
            user.setInstitution(institution);
        }
        if (street_address != null)
        {
            user.setStreetAddress(street_address);
        }
        if (city != null)
        {
            user.setCity(city);
        }
        if (state != null)
        {
            user.setState(state);
        }
        if (country != null)
        {
            user.setCountry(country);
        }
        if (mailcode != null)
        {
            user.setMailCode(mailcode);
        }
        if (zip_code != null)
        {
            user.setZipCode(zip_code);
        }
        if (area_code != null)
        {
            user.setAreaCode(area_code);
        }
        if (phone_number != null)
        {
            user.setPhoneNumber(phone_number);
        }
        if (email != null)
        {
            user.setEmail(email);
        }
        if (website_url != null)
        {
            user.setWebsiteUrl(website_url);
        }
        if (comment != null)
        {
            user.setComment(comment);
        }

        if (xsedeId != null)
        {
            xsedeId = xsedeId.trim();
            user.setAccount(User.TERAGRID, (xsedeId.length() == 0 ? null : xsedeId));
        }
        if (canSubmit != null)
        {
            user.setCanSubmit(new Boolean(canSubmit));
        }
        // Can throw ValidationException
        validate(user);
        return user;
    }
    
    
//    public static String validateEmail ( String email )
//    {
//        if (!StringUtils.isValidEmailFormat_(email))
//        {
//            return "A valid email address, that you regularly check, is required.";
//        }
//
//        return null;
//    }    
    
    
    public static synchronized String  
    validateEmail ( String email ) 
    {
        if (email == null || email.trim().isEmpty()) 
        {
            return EMPTY_EMAIL_ERR_MSG;
        }
        else if (!email.contains("@"))
        {
            return INVALID_EMAIL_ERR_MSG;
        }
        
        String strArr[] = email.split("@");
        
        if (strArr.length < 2) 
        {
            return INVALID_EMAIL_ERR_MSG;
        }
        
        // Need to check username and demain separately. 

//        String username = strArr[0]; // username portion 
//        String domain = strArr[1];   // domain portion 
//        
//        Pattern pattern = Pattern.compile(EMAIL_USER_ACCOUNT_PATTERN);
//        Matcher matcher = pattern.matcher(username);
//        boolean validUserName = matcher.matches();
//        
//        pattern = Pattern.compile(EMAIL_DOMAIN_NAME_PATTERN);
//        matcher = pattern.matcher(domain);
//        boolean validDomain = matcher.matches();
//
//        return validUserName && validDomain;

        return null; // return NULL when there is no error. 
    }


    /*
     * Null values not allowed for: email, first_name, last_name, password, role, username, country.
     * User object may already be saved in db (in case of update) or may not yet be saved
     * (register/create)
     */
    private static void validate ( User user ) throws Exception
    {
        List<FieldError> errors = new ArrayList<FieldError>();

        // validate email
        String errorMsg = validateEmail(user.getEmail());
        if (errorMsg != null)
        {
            errors.add(new FieldError("email", errorMsg));
        }

        User u;

        // findAll* methods include users who aren't yet activated.
        if ((u = User.findUserByEmail(user.getEmail(), user.getRole())) != null)
        {
            if (!u.equals(user))
            {
                errors.add(new FieldError("email", "Email address is already in use."));
            }
        }

        // validate other required fields
        if (user.getFirstName() == null || user.getFirstName().isEmpty())
        {
            errors.add(new FieldError("first_name", "First Name is required."));
        }
        
        if (user.getLastName() == null || user.getLastName().isEmpty())
        {
            errors.add(new FieldError("last_name", "Last Name is required."));
        }
        
        if (user.getPassword() == null || user.getPassword().isEmpty())
        {
            errors.add(new FieldError("password", "Password is required."));
        }
        
        try
        {
            CountryCode.getCountry(user.getCountry());
        }
        catch ( WorkbenchException we )
        {
            errors.add(new FieldError("country", we.getMessage()));
        }
        
        if (errors.size() > 0)
        {
            throw new ValidationException(errors);
        }
    }


    /*
     * This should not be exposed by REST API. It's for the person who deploys or manages cipres to
     * run on the machine where cipres is running. See cipresAdmin script.
     */
    public static User createCipresAdmin (
            String username,
            String password,
            String first_name,
            String last_name,
            String email ) throws Exception
    {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setFirstName(first_name);
        user.setLastName(last_name);
        user.setEmail(email);
        user.setRole(UserRole.ADMIN);
        user.save();
        return user;
    }


    /*
     * Not exposed by REST API. See cipresAdmin script.
     */
    public static List<User> getCipresAdmins () throws Exception
    {
        return User.findAllUsers(UserRole.ADMIN);
    }


    /*
     * We already know user exists when this is called.
     */
    public static User activate ( User user, String code ) throws Exception
    {
        if (code == null)
        {
            throw new ValidationException("code", "code is required.");
        }
        String storedCode = user.getActivationCode();
        if (storedCode == null)
        {
            // already activated.
            return user;
        }
        if (!code.equals(storedCode))
        {
            // invalid activation code.
            throw new ValidationException("code", "activation code is not valid.");
        }
        user.confirmActivation();

        // this will throw if another thread or process changes or deletes the record after we read it.
        // catch the specific exception we get for record having been deleted and say that activation code expired
        // user needs to register again?  If user clicks the activation link multiple times, fast, what happens?
        user.save();
        return user;
    }


    public static String makeActivationCode ( String username ) //throws UnsupportedEncodingException 
    {
        Date now = new Date();
        return StringUtils.getMD5HexString(username + (new Date()).toString());
    }


    public static void emailActivationCode ( String url, User user ) throws Exception
    {
        ClassPathXmlApplicationContext appContext
                = new ClassPathXmlApplicationContext("tool/spring-mail.xml");
        MailService mailservice = (MailService) appContext.getBean("mailService");
        String restName = Workbench.getInstance().getProperties().getProperty("rest.name");

        // uses templates in resources/tool/activation-text.ftl and activation-html.ftl
        // with these properties.
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("username", user.getUsername());
        properties.put("activationUrl", url);

        mailservice.sendMimeMessage(user.getEmail(),
                                    null, restName + " Registration", "activation", properties);
    }


    public static void main ( String[] args ) throws Exception
    {
//        ConnectionManager.setConnectionSource(new DriverConnectionSource());
//        String list = System.getProperty("list");
//        if (list != null)
//        {
//            List<User> users = getCipresAdmins();
//            for (User user : users)
//            {
//                logger.info("Found cipres administrator: " + user.getUsername());
//            }
//            if (users.size() > 0)
//            {
//                System.exit(0);
//            }
//            logger.info("There are no cipres administrator accounts in the database.");
//            System.exit(1);
//        }
//
//        String username = System.getProperty("username", "");
//        username = username.trim();
//        if (username.length() == 0)
//        {
//            logger.error("username required. ");
//            System.exit(-1);
//        }
//        String password = System.getProperty("password", "");
//        password = password.trim();
//        if (password.length() == 0)
//        {
//            logger.error("password required. ");
//            System.exit(-1);
//        }
//        String email = System.getProperty("email", "");
//        email = email.trim();
//        if (email.length() == 0)
//        {
//            logger.error("email required. ");
//            System.exit(-1);
//        }
//
//        User user = null;
//        try
//        {
//            user = createCipresAdmin(username, password, username, username, email);
//        }
//        catch ( Exception e )
//        {
//            logger.error("", e);
//        }
//        if (user == null)
//        {
//            System.exit(-1);
//        }
//        logger.debug("Created cipresadmin user " + username + ", uid=" + user.getUserId());

        Client client = new Client();
        client.testPatterns();
    }
    
    
    private void testPatterns () 
    {
        boolean status = false; 
        
        String username = "jbilewitch";
        System.out.println(String.format("Username [%s], Valid? [%s]",
                                         username, StringUtils.isValidFormat(StringUtils.USERNAME_PATTERN, username)));
        
        username = "jaret_123";
        System.out.println(String.format("Username [%s], Valid? [%s]",
                                         username, StringUtils.isValidFormat(StringUtils.USERNAME_PATTERN, username)));
        
        username = "j_bilewitch";
        System.out.println(String.format("Username [%s], Valid? [%s]",
                                         username, StringUtils.isValidFormat(StringUtils.USERNAME_PATTERN, username)));
        
    }
    
}
