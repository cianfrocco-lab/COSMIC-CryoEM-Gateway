package org.globusonline.transfer;

import javax.net.ssl.HttpsURLConnection;

/**
 * An authenticator to transfer that uses a Nexus Goauth token.
 *
 * @author jbryan
 *
 */
public class GoauthAuthenticator implements Authenticator {

	private String token;

	public GoauthAuthenticator(String token) {
		this.setToken(token);
	}

	public void authenticateConnection(HttpsURLConnection c) {
		//String auth_string = "Globus-Goauthtoken " + this.token;
        String auth_string = "Bearer " + this.token;
		c.setRequestProperty("Authorization", auth_string);
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

}
