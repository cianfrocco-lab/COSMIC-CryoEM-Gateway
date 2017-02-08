package org.globusonline.transfer;

import java.security.GeneralSecurityException;
import java.util.*;
import java.io.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class GoauthExample {
    public static void main(String args[]) {
        if (args.length < 1) {
            System.err.println(
                "Usage: java org.globusonline.transfer.GoauthExample "
                + "username [oauthToken [baseurl]]]");
            System.exit(1);
        }
        String username = args[0];

        String oauthToken = null;
        if (args.length > 1 && args[1].length() > 0)
            oauthToken  = args[1];

        String baseUrl = null;
        if (args.length > 2 && args[2].length() > 0)
            baseUrl = args[2];

        try {
            //System.out.println("using token: " + oauthToken.substring(0, 20)
            //                   + "...");
            Authenticator authenticator = new GoauthAuthenticator(oauthToken);
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                                            null, baseUrl);
            c.setAuthenticator(authenticator);
            //System.out.println("base url: " + c.getBaseUrl());
            Example e = new Example(c);
            e.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
