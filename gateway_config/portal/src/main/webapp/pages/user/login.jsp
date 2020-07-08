<%@ include file="/pages/common/taglibs.jsp" %>
<%@ page isELIgnored='false'%>
<head>
  <title>Login</title>
  <content tag="menu">Home</content>
</head>

<body onLoad="detectBrowser()"/>
<%-- BEGIN MAIN CONTENT AREA --%>
  <div class="col-xs-10">
    <div class="alert alert-danger" role="alert">
        <font size="+1"><span class="glyphicon glyphicon-exclamation-sign"
            aria-hidden="true"></span>
        <span class="sr-only">Warning:</span>
        The COSMIC<sup>2</sup> Science Gateway will be
        down for general system maintenance<br>&nbsp;&nbsp;&nbsp;&nbsp;
        on Tuesday, July 21, 2020 between 16:30 and 23:30 Pacific Time.</font>
    </div>

      <h2>Welcome to COSMIC<sup>2</sup> !</h2> 

      <br>
      <h3>This is a science gateway for cryo-EM structure determination.</h3><br>

      <h4>Please login below with your university credentials and you are
          ready to go!<br>
          If you are a new user, we will email you shortly when your
          transfer access has been enabled.</h4><br>

    <s:url id="loginUrl" action="login"/>
        <s:a cssClass="btn btn-primary" href="%{loginUrl}">Login</s:a><br><br>

      Questions about this login process?
      <a href="https://cosmic-cryoem.org/user-authentication/">Please read more here.</a><br><br>

	  By logging into COSMIC<sup>2</sup>, you have agreed with the <a href="https://cosmic-cryoem.org/terms-of-use/">Terms of Use</a>.<br><br>
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
