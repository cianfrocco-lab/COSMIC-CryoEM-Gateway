# Globus Integration

CIPRES web framework demonstrating how to build COSMIC2 gateway using the Globus platform.

## Overview

In order to build the COSMIC2 science gateway, we needed to introduce a new way for users to upload data. 
Since the [CIPRES Science Gateway](https://www.phylo.org/) utilizes small (<1 MB), text-based inputs, 
users are able to upload (or paste) their input files directly into the web site. 
To solve this problem, we are utilizing the [Globus Services platform](https://www.globus.org/) to handle large dataset transfers, in addition to user authentication. 
Currently, Globus has not prototyped support for the Apache Struts 2 framework that powers the CIPRES Science Gateway.

To this end, we have integrated Globus APIs into the CIPRES framework to handle the transfer of large data files from user’s local storage to Comet at SDSC. 
Specifically, we have built COSMIC2 Gateway using Globus Auth and Transfer APIs, taking advantage of service based appraoches to research data management implemented by Globus. 
The Globus Auth API is used for authenticating users and obtaining access to file transfer services. 
Then, the Globus Transfer API interfaces with the Globus file transfer service, including monitoring the progress of file transfer tasks, managing file transfer endpoints, listing remote files and directories, and submitting new transfer tasks.
In more details, please see the [Globus documentation](https://docs.globus.org/api/).

A strong feature of Globus is being able to leverage Globus Auth, which allows for a fully spec compliant OAuth2 implementation that works in conjunction with identity providers that are accessible to the user-base of COSMIC2 (e.g. Google, XSEDE, Universities). 
In order for COSMIC2 Science Gateway users to interact with Globus servers, we created a custom login system using Globus Auth API on CIPRES framework that replaces built-in user authentication. 
When a user connects with the portal using Globus login, selecting one of identity providers, the user is prompted to authenticate, and to consent to giving the application access to the requested scopes. 
After successful sign-in confirms, an OpenID Connect “id_token” is returned as part of the OAuth2 Access Token Response. It contains a user profile such as email address (“email”), identity’s full name (“name”), identity username (“preferred_username”), and Globus Auth identity id (“sub”). 
As the part of the user object, these data are stored in the database. But, the existing CIPRES user object has more fields to control other services, for example, task service, job management service, etc. 
In addition, in order to keep the same user session within this framework, if CIPRES user object is not in the database, it is created and invoked automatically. Also the portal maintains an access token in the user session, which provides temporary, secure access to Transfer APIs.

Within the COSMIC2 gateway, we have designed and implemented three user interfaces for data management service. 
In order for users to transfer data to COSMIC2, they will first install Globus Connect Personal (GCP) on their local machine (e.g. laptop, data server, etc.). 
Once this GCP endpoint is activated, the user can browse available data endpoints in the COSMIC2 gateway in the “My Endpoints” interface. 
In order to change the specific path on the GCP endpoint, we used the endpoint bookmarks service to allow users to create an alias for an endpoint and path. 
When the user navigates to the endpoints page on COSMIC2, by default the user’s GCP data endpoint is set as the source endpoint and the XSEDE Globus endpoint on Comet is set as the destination endpoint. 
We have implemented the Globus endpoint on Comet in COSMIC2 to only allow users to select Comet as the destination without the possibility of navigating through the filesystem on Comet. 
Once the Comet endpoint is selected, the COSMIC2 gateway will determine the location of the uploaded files.

Next, after the user selects the destination endpoint (XSEDE Comet), the “Transfer” interface provides an interactive mode for users to select data for upload.
This interface lists the directory contents at the specific path on a remote source endpoint’s file system using file operation service, selects multiple files at a time in transfer window, and sends them to the destination endpoint’s repository. 
Whereas individual users will authenticate to launch their GCP endpoints, the COSMIC2 gateway had to incorporate a specific security credential environment in order to authenticate to the XSEDE Comet endpoint. 
Since the XSEDE endpoint belongs to the XSEDE identity provider, we had to use the COSMIC2 community account (issued by XSEDE), which is used to authenticate to that endpoint on behalf of COSMIC2 users on Comet. 
Currently XSEDE security team issued only community credentials for a community user account. In order to provide these credentials for an XSEDE endpoint, a Globus activation type, “[delegation_proxy](https://docs.globus.org/api/transfer/endpoint_activation/#delegate_proxy)” was selected.
It is applied for this community account that has a copy of user’s credential, and is supported by all GridFTP endpoints. 
The “activation_requirements” service in Transfer APIs returns the activation requirements document that contains a list of activation types supported by that endpoint. 
The delegation activation type in this document provides the public key so that the proxy generator creates a delegated X.509 proxy credential using that public key, signed by the community account’s credential. 
After the endpoint activation of two data repositories, the specific user directory in a shared project directory on XSEDE endpoint is created to save data files transferred. 
At the time of task submission, two transfer specific fields are set up via the system property file. 
The “encrypt_data” field encrypts the data channel if true, and the “sync_level” field controls what checks are performed to determine if a file should be copied so that COSMIC2 portal sets up level 2, which means copying files if the timestamp of the destination is older than the timestamp of the source. 
When submitting a transfer task, the submission ID should be obtained. The transfer task job with this ID is then submitted via Transfer service, and the useful fields of the returned result document are stored in the database as the transfer record. 
Currently, in order to update this record, it depends upon request from the user.

Finally, the “Transfer Status” interface lists transfer tasks only for two weeks based on task creation time. 
Since transfer activity is asynchronous operations, the task ID is returned from successful submission, and can be used to monitor the progress of the task. 
If the task status is in “ACTIVE” or “INACTIVE” indicating the progress of the task, that transfer task has “Refresh” button to track the progress of a newly submitted task.
Since we use default values (true) of common transfer fields related to notification in transfer document, a user will have email notification fromn Globus service, depeding on the task status.

## Getting Started

If you have already deployed CIPRES framework, you can stop the server running and skip step 1 & 2.
1. Download source codes for CIPRES framework at [CIPRES SVN](https://svn.sdsc.edu/repo/scigap/trunk/).
    * svn checkout https://svn.sdsc.edu/repo/scigap/trunk source
2. Install CIPRES framework based on [instructions](https://svn.sdsc.edu/repo/scigap/trunk/documents/framework_install.txt).
3. Dowload source codes for globus integration at [Github](COSMIC-CryoEM-Gateway).
    * git clone https://github.com/leschzinerlab/COSMIC-CryoEM-Gateway.git
4. Add [libraries](globus_sources/portal/pom.xml) below into the __dependencies__ tag in the maven pom file, ***portal/pom.xml***.
  ```xml
		<!-- cyoun: globus integration work { -->
		<!-- https://mvnrepository.com/artifact/com.google.apis/google-api-services-oauth2 -->
		<dependency>
			<groupId>com.google.apis</groupId>
			<artifactId>google-api-services-oauth2</artifactId>
			<version>v2-rev120-1.22.0</version>
		</dependency>
		<!-- https://mvnrepository.com/artifact/org.apache.oltu.oauth2/org.apache.oltu.oauth2.client -->
		<dependency>
			<groupId>org.apache.oltu.oauth2</groupId>
			<artifactId>org.apache.oltu.oauth2.client</artifactId>
			<version>1.0.2</version>
		</dependency>
		<!-- https://mvnrepository.com/artifact/org.hibernate/hibernate-core -->
		<dependency>
			<groupId>org.hibernate</groupId>
			<artifactId>hibernate-core</artifactId>
			<version>5.2.3.Final</version>
		</dependency>
		<!-- https://mvnrepository.com/artifact/com.mchange/c3p0 -->
		<dependency>
			<groupId>com.mchange</groupId>
			<artifactId>c3p0</artifactId>
			<version>0.9.5.2</version>
		</dependency>
		<!-- https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk16 -->
		<dependency>
			<groupId>org.bouncycastle</groupId>
			<artifactId>bcprov-jdk16</artifactId>
			<version>1.45</version>
		</dependency>
		<!--} cyoun: globus integration work -->
  ```
5. Copy the globus source directory, **globus_sources/portal/src/main/java** into the CIPRES source directory, **portal/src/main/java**.
    * cp -R globus_sources/portal/src/main/java/edu portal/src/main/java
    * cp -R globus_sources/portal/src/main/java/org/globusonline portal/src/main/java/org
    * cp -R globus_sources/portal/src/main/java/org/json portal/src/main/java/org
6. Deploy configuration files.
    * Copy config files, ***hibernate.cfg.xml*** and ***oauth_consumer.properties*** in the the globus source directory, **globus_sources/portal/src/main/resources** into the CIPRES source directory, **portal/src/main/resources**.
        * cp globus_sources/portal/src/main/resources/hibernate.cfg.xml portal/src/main/resources/
        * cp globus_sources/portal/src/main/resources/oauth_consumer.properties portal/src/main/resources/
    * Edit the database connection parts below in the ***hibernate.cfg.xml*** file.
        ```xml
          <property name="connection.driver_class">com.mysql.jdbc.Driver</property>
              <property name="connection.url">jdbc:mysql://localhost:3306/cipres?autoReconnect=true</property>
              <property name="connection.username">root</property>
              <property name="connection.password"></property>
          <property name="dialect">org.hibernate.dialect.MySQLDialect</property>
        ```
    * Configure oauth parameters provided by Globus, and properties for endpoint activation in the ***oauth_consumer.properties*** file.
        * See [Globus Auth Developer Guide](https://docs.globus.org/api/auth/developer-guide/)
        * See [Proxy Creation Overview](https://github.com/globusonline/transfer-api-client-python/tree/master/mkproxy)
        * Install the proxy generator in the globus source directory, **globus_sources/mkproxy**.
            * Linux: chmod +x compile.sh ; ./compile.sh
            * macOS Sierra version 10.12.4: chmod +x compile_macos.sh ; ./compile_macos.sh

    * Comment actions below in the CIPRES struts file, ***portal/src/main/resources/struts.xml***.
        ```xml
        <!--
            <action name="profile" class="org.ngbw.web.actions.ProfileManager">
                 <result>/pages/user/profile.jsp</result>
            </action>
      
            <action name="login" class="org.ngbw.web.actions.FolderManager" method="login">
                 <interceptor-ref name="loginStack"/>
                 <result name="success" type="redirectAction">home</result>
                 <result name="input">/pages/user/login.jsp</result>
                 <result name="iplant_register">/pages/user/iplantRegister.jsp</result>
                 <allowed-methods>input,login</allowed-methods>
            </action>
      
            <action name="logout" class="org.ngbw.web.actions.SessionManager" method="logout">
                 <interceptor-ref name="defaultStack"/>
                 <result type="redirectAction">welcome</result>
                 <result name="iplant_logout" type="redirect">/Shibboleth.sso/Logout</result>
            </action>
      
        -->
        ```
    * Add actions below into the CIPRES struts file, ***portal/src/main/resources/struts.xml***.
        ```xml
          <!-- Start: Globus Auth & Transfer actions -->
          <action name="profile" class="edu.sdsc.globusauth.action.ProfileAction">
              <result name="success">/pages/oauth/profile.jsp</result>
              <result name="failure">/pages/error.jsp</result>
              <result name="transfer" type="redirectAction">transfer</result>
          </action>
          
          <action name="transfer" class="edu.sdsc.globusauth.action.TransferAction" method="transfer">
              <result name="success">/pages/oauth/transfer.jsp</result>
              <result name="transferstatus" type="redirectAction">status?taskId=${taskId}</result>
              <result name="dataendpoints" type="redirectAction">endpointlist?empty=true</result>
              <result name="failure">/pages/error.jsp</result>
          </action>
          
           <action name="status" class="edu.sdsc.globusauth.action.TransferStatusAction" method="transfer_status">
               <result name="success">/pages/oauth/transfer_status.jsp</result>
               <result name="failure">/pages/error.jsp</result>
           </action>
          
           <action name="endpointlist" class="edu.sdsc.globusauth.action.EndpointListAction" method="endpoint_list">
               <result name="success">/pages/oauth/endpoint_list.jsp</result>
               <result name="failure">/pages/error.jsp</result>
           </action>
          
           <action name="signup" class="org.ngbw.web.actions.NgbwSupport">
               <interceptor-ref name="defaultStack"/>
               <result type="redirectAction">authcallback?signup=true</result>
           </action>
          
           <action name="login" class="edu.sdsc.globusauth.action.LoginAction" method="authcallback">
               <interceptor-ref name="loginStack"/>
               <result name="input">/pages/user/login.jsp</result>
               <result name="authcallback" type="redirectAction">authcallback</result>
               <allowed-methods>authcallback,input</allowed-methods>
           </action>
          
           <action name="authcallback" class="edu.sdsc.globusauth.action.AuthCallbackAction" method="globuslogin">
               <interceptor-ref name="defaultStack"/>
               <result name="failure">/pages/error.jsp</result>
               <result name="profileredirect" type="redirectAction">profile</result>
               <result name="authredirect" type="redirect">${authurl}</result>
               <result name="transfer" type="redirectAction">transfer</result>
               <result name="success" type="redirectAction">home</result>
               <result name="dataendpoints" type="redirectAction">endpointlist?empty=true</result>
               <allowed-methods>input,globuslogin</allowed-methods>
           </action>
          
           <action name="logout" class="edu.sdsc.globusauth.action.LogoutAction" method="globuslogout">
               <interceptor-ref name="defaultStack"/>
               <result type="redirect">${logouturl}</result>
           </action>
           <!-- End: Globus Auth & Transfer actions -->
        ```
7. Copy the globus source directory, **globus_sources/portal/src/main/webapp/pages** into the CIPRES source directory, **portal/src/main/webapp/pages**.
    * cp -R globus_sources/portal/src/main/webapp/pages/oauth portal/src/main/webapp/pages
    * cp globus_sources/portal/src/main/webapp/pages/user/data/pasteForm.jsp portal/src/main/webapp/pages/user/data
8. Copy the globus source directory, **globus_sources/my_config/portal/src/main/webapp/pages** into your gateway config directory, ***my_config*/portal/src/main/webapp/pages**.
    * cp globus_sources/my_config/portal/src/main/webapp/pages/common/menu.jsp my_config/portal/src/main/webapp/pages/common
    * cp globus_sources/my_config/portal/src/main/webapp/pages/common/user/login.jsp my_config/portal/src/main/webapp/pages/common/user
9. Build and deploy the framework using scripts. For example, *./build.py --conf-dir=my_config deploy*. 
