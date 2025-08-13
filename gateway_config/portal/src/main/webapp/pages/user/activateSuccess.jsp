<%@ taglib prefix="s" uri="/struts-tags" %>
  
<head>
    <title>Account Activation</title>
    <content tag="menu">Home</content>
</head>
<body>
    <div class="col-xs-6 col-xs-offset-3">
        <div class="callout">
            Your account has been activated successfully.<br>
            You can now <a href="<s:url action='home.action'/>">login to your account</a>.
        </div>
    </div>
</body>
