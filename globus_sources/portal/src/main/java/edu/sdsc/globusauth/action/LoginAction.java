package edu.sdsc.globusauth.action;

import org.ngbw.web.actions.NgbwSupport;

/**
 * Created by cyoun on 10/5/16.
 */

public class LoginAction extends NgbwSupport {

    public String input() {
        return INPUT;
    }

    public String authcallback() {
        return "authcallback";
    }

}
