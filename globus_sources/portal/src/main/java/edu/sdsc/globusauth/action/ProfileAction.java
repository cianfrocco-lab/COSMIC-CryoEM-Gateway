package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.controller.ProfileManager;
import edu.sdsc.globusauth.model.OauthProfile;
import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.ngbw.sdk.common.util.ValidationResult;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.web.actions.SessionManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by cyoun on 10/8/16.
 */
public class ProfileAction extends SessionManager {

    private static final Logger logger = Logger.getLogger(ProfileAction.class.getName());

    private OauthProfile profile;
    private ProfileManager profileManager;

    //private String name;
    //private String email;
    //private String institution;

    public ProfileAction() {
        profileManager = new ProfileManager();
    }

    public String execute() throws Exception {
        //User profile information. Assocated with a Globus Auth identity.
        if (request.getMethod().equals(OauthConstants.HTTP_GET)) {
            String identity_id = (String) getSession().get(OauthConstants.PRIMARY_IDENTITY);
            logger.info("Profile: "+identity_id);
            OauthProfile db_profile = profileManager.load(identity_id);
            if (db_profile == null) {
                profileManager.add(profile);
            } else {
                getSession().put(OauthConstants.EMAIL, db_profile.getEmail());
                getSession().put(OauthConstants.FIRST_NAME, db_profile.getFirstName());
                getSession().put(OauthConstants.LAST_NAME, db_profile.getLastName());
                getSession().put(OauthConstants.INSTITUTION,db_profile.getInstitution());
            }
            return SUCCESS;
        } else if (request.getMethod().equals(OauthConstants.HTTP_POST)) {
            if (validateProfile()) {
                OauthProfile form_profile = getProfile();
                getSession().put(OauthConstants.EMAIL, form_profile.getEmail());
                getSession().put(OauthConstants.FIRST_NAME, form_profile.getFirstName());
                getSession().put(OauthConstants.LAST_NAME, form_profile.getLastName());
                getSession().put(OauthConstants.INSTITUTION, form_profile.getInstitution());

                form_profile.setIdentityId((String) getSession().get(OauthConstants.PRIMARY_IDENTITY));
                //form_profile.setUserName((String) getSession().get(OauthConstants.PRIMARY_USERNAME));
                logger.info(form_profile);
                try {
                    profileManager.update(form_profile);
                    //profileManager.updateUser(form_profile);
                    if (updateUserInfo(form_profile)) {
                        reportUserMessage((String) getSession().get(OauthConstants.PRIMARY_USERNAME) + " was updated.");
                    } else {
                        reportUserError((String) getSession().get(OauthConstants.PRIMARY_USERNAME) + " was not updated.");
                    }
                } catch (Exception e) {
                    reportError(e, (String) getSession().get(OauthConstants.PRIMARY_USERNAME) + " was not updated.");
                    //e.printStackTrace();
                }
            }
            return SUCCESS;
        } else {
            return "failure";
        }
    }

    public boolean updateUserInfo(OauthProfile oauthProfile) {
            ValidationResult result = getController().editUser(oauthProfile.getEmail(),
                    oauthProfile.getFirstName(), oauthProfile.getLastName(),
                    oauthProfile.getInstitution(), null,null); //getCountry(), getAccount());

            if (!result.isValid()) {
                for (String error : result.getErrors())
                    reportUserError(error);
                return false;
            }
            //addActionMessage("Personal information successfully updated.");
            return true;
    }

    /*
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return email;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }
    public String getOrganization(){
        return organization;
    }
    */
    public void setProfile(OauthProfile profile) {
        this.profile = profile;
    }
    public OauthProfile getProfile() {
        return profile;
    }
    //simple validation
    /*
    public void validate(){
        if("".equals(getName())){
            addFieldError("Name", getText("name.required"));
        }
    }
    */

    private boolean validateProfile() {
        String fname = getProfile().getFirstName();
        if (fname == null || fname.trim().equals("")) {
            addFieldError("First Name", "First Name is required.");
        }
        String lname = getProfile().getLastName();
        if (fname == null || fname.trim().equals("")) {
            addFieldError("Last Name", "Last Name is required.");
        }
        String email = getProfile().getEmail();
        if (email == null || email.trim().equals("")) {
            addFieldError("Email", "Email is required.");
        } else if (!validateEmail(email)) {
            addFieldError("Email", "Sorry, the email address you entered is invalid");
        }

        String institution = getProfile().getInstitution();
        if (institution == null || institution.trim().equals("")) {
            addFieldError("Institution", "Institution is required.");
        }

        if (hasFieldErrors()) return false;
        else return true;
    }

    private boolean validateEmail(String email) {
        final String EMAIL_PATTERN =
                "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                        + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        Pattern pattern = Pattern.compile(EMAIL_PATTERN);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

}
