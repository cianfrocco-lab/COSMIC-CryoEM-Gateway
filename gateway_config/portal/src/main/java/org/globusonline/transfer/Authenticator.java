package org.globusonline.transfer;

import javax.net.ssl.HttpsURLConnection;

public interface Authenticator {
	/**
	 * @param c The connection that needs to be authenticated
	 */
	public void authenticateConnection(HttpsURLConnection c);
}
