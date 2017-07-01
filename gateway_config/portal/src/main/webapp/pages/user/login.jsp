<%@ include file="/pages/common/taglibs.jsp" %>
<%@ page isELIgnored='false'%>
<head>
  <title>Login</title>
  <content tag="menu">Home</content>
</head>
<body onLoad="detectBrowser()"/>
<%-- BEGIN MAIN CONTENT AREA --%>
<div class="col-xs-5">
    <%--
    <b><font size="+1">Please click on the "Login" button in the above menu to get authorization to use the COSMIC2 Science Gateway.</font><p>
        
        You will be redirected to the Globus OAuth2 login page where you can choose your organization.  After you've successfully authenticated with your organization, you will be automatically returned to the COSMIC2 gateway as an authorized user.</b>
    --%>
<s:url id="loginUrl" action="login"/>
<s:a cssClass="btn btn-primary" href="%{loginUrl}">Login</s:a>
</div>

<%-- previous CIPRES user login... --%>
<%--
  <s:url id="registerUrl" action="register"/>
  <s:url id="guestUrl" action="guestLogin"/>
  <s:url id="forgotPasswordUrl" action="forgotPassword" method="input" includeParams="none"/>
  <s:url id="loginUrl" action="login"/>

  <%-- BEGIN MAIN CONTENT AREA --%>
<%--
  <div class="col-xs-7">

    <div class="col-xs-6">
      <h4>Login:</h4>
      <s:form id="loginBox" action="login" theme="simple" role="form">
        <div class="form-group">
          <label>Username</label>
          <s:textfield cssClass="form-control" name="username" placeholder="Username"/>
        </div>
        <div class="form-group">
          <label>Password</label>
          <s:password cssClass="form-control" name="currentPassword" size="Password"/>
        </div>
        <div class="form-group">
          <s:submit cssClass="btn btn-primary" value="Login" method="login"/>
          <s:reset cssClass="btn btn-primary"/>
        </div>
        <s:a href="%{forgotPasswordUrl}">Forgot Password?</s:a>
      </s:form>
      <%-- under the cipres box --%>
<%--
      <div>
        <s:a href="%{registerUrl}">Register</s:a><b> &nbsp;| &nbsp;</b>
        <s:a href="%{guestUrl}">Proceed without Registering</s:a>
       </div>
      <s:a href="%{loginUrl}">Login</s:a>
      <s:submit cssClass="btn btn-primary" value="Login" method="login"/>
    </div>
  </div>
  --%>
</body>
</html>
