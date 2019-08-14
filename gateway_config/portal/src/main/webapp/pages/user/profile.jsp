<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>User Profile</title>
  <content tag="menu">My Profile</content>
</head>
<body>
  <!-- Mona removed displaying of property as it messes up formatting and
        info is displayed again down below...
  <h4><span class="username"><s:property value="authenticatedUsername"/></span> 's Profile</h4>
  -->
  <h2>Account Information</h2>
  <div id="account-info" class="form-horizontal info-box">
    <div class="form-group">
      <label class="col-xs-2 control-label">Username</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="authenticatedUsername"/></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Password</label>
      <div class="col-xs-10">
        <span class="form-control">********</span>
      </div>
    </div>
    <div class="form-group form-buttons">
      <div class="col-xs-12">
        <s:url id="updatePasswordUrl" action="updatePassword" method="input"
          includeParams="none"/>
        <s:a href="%{updatePasswordUrl}" cssClass="mc-replace btn btn-primary">Change Password</s:a>
      </div>
    </div>
  </div>

  <h2>Profile</h2>
  <div id="personal-info" class="form-horizontal info-box">
    <div class="form-group">
      <label class="col-xs-2 control-label">First Name</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="authenticatedUser.firstName"/>
        </span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Last Name</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="authenticatedUser.lastName"/>
        </span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Email</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="authenticatedUser.email"/>
        </span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Institution</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="authenticatedUser.institution"/>
        </span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Country</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="authenticatedUser.country"/>
        </span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">XSEDE Allocation</label>
      <div class="col-xs-10">
        <span class="form-control">
          <s:property value="%{authenticatedUser.getAccount('teragrid')}"/>
        </span>
      </div>
    </div>
    <div class="form-group form-buttons">
      <div class="col-xs-12">
        <s:url id="updateProfileUrl" action="updateProfile" method="input"
          includeParams="none"/>
        <s:a href="%{updateProfileUrl}" cssClass="mc-replace btn btn-primary">Update Profile</s:a>
      </div>
    </div>
  </div>
  
  <s:if test="%{needProfileInfo() == true}">
	<script>gotoUpdateProfile();</script>
  </s:if>

</body>
