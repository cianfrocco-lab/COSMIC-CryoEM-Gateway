package edu.sdsc.globusauth.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by cyoun on 10/6/16.
 */
public class OauthUtils {

    public static Properties getConfig(String path) {
        InputStream is = OauthUtils.class.getClassLoader().getResourceAsStream(path);
        Properties config = new Properties();
        try {
            config.load(is);
        } catch (IOException e) {
            System.out.println("Could not load properties from " + path);
            e.printStackTrace();
            return null;
        }
        return config;
    }
}
