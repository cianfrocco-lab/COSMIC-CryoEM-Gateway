<%@ include file="/pages/common/taglibs.jsp" %><head>
<%@ page isELIgnored='false'%>
<head>
  <title>Login</title>
  <content tag="menu">Home</content>
</head>
<body onLoad="detectBrowser()"/>

  <s:url id="registerUrl" action="register"/>
  <s:url id="guestUrl" action="guestLogin"/>
  <s:url id="forgotPasswordUrl" action="forgotPassword" method="input" includeParams="none"/>

  <%-- BEGIN MAIN CONTENT AREA --%>
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
      <div>
        <s:a href="%{registerUrl}">Register</s:a><b> &nbsp;| &nbsp;</b>
        <s:a href="%{guestUrl}">Proceed without Registering</s:a>
       </div>
    </div>
  </div>
</body>
</html>
