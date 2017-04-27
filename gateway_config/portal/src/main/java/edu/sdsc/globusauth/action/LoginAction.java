package edu.sdsc.globusauth.action;

import org.apache.log4j.Logger;

import org.ngbw.web.actions.NgbwSupport;

/**
 * Created by cyoun on 10/5/16.
 */

public class LoginAction extends NgbwSupport {

    private static final Logger logger =
        Logger.getLogger ( LoginAction.class.getName() );

    public String input() {
        logger.debug ( "MONA: LoginAction.input() debug" );
        logger.error ( "MONA: LoginAction.input() error" );
        logger.info ( "MONA: LoginAction.input() info" );
        logger.warn ( "MONA: LoginAction.input() warn" );
        info ( "MONA: LoginAction.input() plain info" );
        return INPUT;
    }

    public String authcallback() {
        logger.debug ( "MONA: LoginAction.authcallback() debug" );
        logger.error ( "MONA: LoginAction.authcallback() error" );
        logger.info ( "MONA: LoginAction.authcallback() info" );
        logger.warn ( "MONA: LoginAction.authcallback() warn" );
        info ( "MONA: LoginAction.authcallback() plain info" );
        return "authcallback";
    }

}
