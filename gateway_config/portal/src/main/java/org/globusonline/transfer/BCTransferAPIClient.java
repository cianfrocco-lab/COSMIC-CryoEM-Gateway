/*
 * Copyright 2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globusonline.transfer;

import java.io.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

import java.net.MalformedURLException;
import java.net.URL;

import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
//import org.bouncycastle.util.io.pem.PemReader;

/**
 * Extension to the base client which supports reading PEM files using
 * Bouncy Castle, so the client cert/key don't have to be converted
 * to PKCS12.
 */
public class BCTransferAPIClient extends BaseTransferAPIClient {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println(
                    "Usage: java org.globusonline.transfer.BCTransferAPIClient "
                    + "username [path [cafile certfile keyfile [baseurl]]]");
                System.exit(1);
            }
            String username = args[0];

            String path = "/endpoint_search?filter_scope=my-endpoints";
            if (args.length > 1)
                path = args[1];

            String cafile = null;
            if (args.length > 2)
                cafile = args[2];

            String certfile = null;
            if (args.length > 3)
                certfile = args[3];

            String keyfile = null;
            if (args.length > 4)
                keyfile = args[4];

            String baseUrl = null;
            if (args.length > 5)
                baseUrl = args[5];

            BCTransferAPIClient c = new BCTransferAPIClient(username,
                                                FORMAT_JSON, cafile, certfile,
                                                keyfile, baseUrl);
            HttpsURLConnection r = c.request("GET", path);
            BCTransferAPIClient.printResult(r);
            r.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BCTransferAPIClient(String username, String format)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, format, null, null, null, null);
    }

    public BCTransferAPIClient(String username, String format, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, format, null, null, null, baseUrl);
    }

    public BCTransferAPIClient(String username, String format,
                      String trustedCAFile, String certFile, String keyFile)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, format, trustedCAFile, certFile, keyFile, null);
    }
    
	/**
	 * Create a client for the user.
	 * 
	 * @param username
	 *            the Globus Online user to sign in to the API with.
	 * @param format
	 *            the content type to request from the server for responses. Use
	 *            one of the FORMAT_ constants.
	 * @param trustedCAFile
	 *            path to a PEM file with a list of certificates to trust for
	 *            verifying the server certificate. If null, just use the trust
	 *            store configured by property files and properties passed on
	 *            the command line.
	 * @param keyManagers
	 *            the keymanager(s) that contain the certificate (chain) to use
	 *            for authentication. If null, just use the trust store
	 *            configured by property files and properties passed on the
	 *            command line.
	 * @param baseUrl
	 *            alternate base URL of the service; can be used to connect to
	 *            different versions of the API and instances running on
	 *            alternate servers. If null, the URL of the latest version
	 *            running on the production server is used.
	 */
	public BCTransferAPIClient(String username, String format,
			String trustedCAFile, KeyManager[] keyManagers, String baseUrl)
					throws KeyManagementException, NoSuchAlgorithmException {
		super(username, format, null, null, baseUrl);

        if (trustedCAFile == null) {
            // Use default CA file that includes GoDaddy, InCommon,
            // and GlobusOnline.
            trustedCAFile = getClass().getResource(
                                    "all-bundle_ca.cert").toString();
        }

		if (trustedCAFile != null) {
			try {
				this.trustManagers = createTrustManagers(trustedCAFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
        }

		this.keyManagers = keyManagers;

		initSocketFactory(true);
	}

    /**
     * Create a client for the user.
     *
     * @param username  the Globus Online user to sign in to the API with.
     * @param format  the content type to request from the server for responses.
     *             Use one of the FORMAT_ constants.
     * @param trustedCAFile path to a PEM file with a list of certificates
     *                      to trust for verifying the server certificate.
     *                      If null, just use the trust store configured by
     *                      property files and properties passed on the
     *                      command line.
     * @param certFile  path to a PEM file containing a client certificate
     *                  to use for authentication. If null, use the key
     *                  store configured by property files and properties
     *                  passed on the command line.
     * @param keyFile  path to a PEM file containing a client key
     *                 to use for authentication. If null, use the key
     *                 store configured by property files and properties
     *                 passed on the command line.
     * @param baseUrl  alternate base URL of the service; can be used to
     *                 connect to different versions of the API and instances
     *                 running on alternate servers. If null, the URL of
     *                 the latest version running on the production server
     *                 is used.
     */
    public BCTransferAPIClient(String username, String format,
                               String trustedCAFile, String certFile,
                               String keyFile, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        super(username, format, null, null, baseUrl);

        if (trustedCAFile != null) {
            try {
                this.trustManagers = createTrustManagers(trustedCAFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (certFile != null) {
            try {
                this.keyManagers = createKeyManagers(certFile, keyFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        initSocketFactory(true);
    }

    static TrustManager[] createTrustManagers(String trustedCAFile)
                            throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);

        // Read the cert(s). The file must contain only certs, a cast
        // Exception will be thrown if it contains anything else.
        // TODO: wrap in friendly exception, it's a user error not a
        // programming error if the file contains a non-cert.
        FileReader fileReader = new FileReader(trustedCAFile);
        PEMReader r = new PEMReader(fileReader);
        X509Certificate cert = null;
        try {
            Object o = null;
            int i = 0;
            while ((o = r.readObject()) != null) {
                cert = (X509Certificate) o;
//                System.out.println("trusted cert subject: "
//                                   + cert.getSubjectX500Principal());
//                System.out.println("trusted cert issuer: "
//                                   + cert.getIssuerX500Principal());

                ks.setEntry("server-ca" + i,
                            new KeyStore.TrustedCertificateEntry(cert), null);
                i++;
            }
        } finally {
            r.close();
            fileReader.close();
        }

        // Shove the key store in a TrustManager.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                                    TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf.getTrustManagers();
    }

    // TODO: support encrypted key.
    static KeyManager[] createKeyManagers(String certFile, String keyFile)
                            throws GeneralSecurityException, IOException {
        // Create a new empty key store.
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);

        // Read the key. Ignore any none-key data in the file, to
        // support PEM files containing both certs and keys.
        FileReader fileReader = new FileReader(keyFile);
        PEMReader r = new PEMReader(fileReader);
        KeyPair keyPair = null;
        try {
            Object o = null;
            while ((o = r.readObject()) != null) {
                if (o instanceof KeyPair) {
                    keyPair = (KeyPair) o;
                }
            }
        } finally {
            r.close();
            fileReader.close();
        }

        // Read the cert(s). Ignore any none-cert data in the file, to
        // support PEM files containing both certs and keys.
        fileReader = new FileReader(certFile);
        r = new PEMReader(fileReader);
        X509Certificate cert = null;
        ArrayList<Certificate> chain = new ArrayList<Certificate>();
        try {
            Object o = null;
            int i = 0;
            while ((o = r.readObject()) != null) {
                if (!(o instanceof X509Certificate))
                    continue;
                cert = (X509Certificate) o;
//                System.out.println("client cert subject: "
//                                   + cert.getSubjectX500Principal());
//                System.out.println("client cert issuer: "
//                                   + cert.getIssuerX500Principal());
                chain.add(cert);
            }
        } finally {
            r.close();
            fileReader.close();
        }

        // The KeyStore requires a password for key entries.
        char[] password = { ' ' };

        // Since we never write out the key store, we don't bother protecting
        // the key.
        ks.setEntry("client-key",
                    new KeyStore.PrivateKeyEntry(keyPair.getPrivate(),
                                         chain.toArray(new Certificate[0])),
                    new KeyStore.PasswordProtection(password));

        // Shove the key store in a KeyManager.
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                                    KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        return kmf.getKeyManagers();
    }
}
