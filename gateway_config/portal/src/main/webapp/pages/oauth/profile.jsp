<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
    <title>User Profile</title>
    <content tag="menu">My Profile</content>
</head>
<body>
<h2>Update Personal Information</h2>
<s:form id="update-personal-info" cssClass="form-horizontal" action="profile" method="POST" theme="simple">
    <div class="form-group">
        <label class="col-xs-2 control-label">First Name</label>
        <div class="col-xs-10">
            <s:textfield cssClass="form-control" name="profile.firstname" value="%{#session.first_name}"/>
        </div>
    </div>
    <div class="form-group">
        <label class="col-xs-2 control-label">Last Name</label>
        <div class="col-xs-10">
            <s:textfield cssClass="form-control" name="profile.lastname" value="%{#session.last_name}"/>
        </div>
    </div>
    <div class="form-group">
        <label class="col-xs-2 control-label">Email</label>
        <div class="col-xs-10">
            <s:textfield cssClass="form-control" name="profile.email" value="%{#session.email}"/>
        </div>
    </div>
    <div class="form-group">
        <label class="col-xs-2 control-label">Institution</label>
        <div class="col-xs-10">
            <s:textfield cssClass="form-control" name="profile.institution" value="%{#session.institution}"/>
        </div>
    </div>
    <div class="form-group form-buttons">
        <div class="col-xs-12">
            <s:submit value="Update Profile" cssClass="btn btn-primary"/>
            <s:reset value="Reset" cssClass="btn btn-primary"/>
        </div>
    </div>
</s:form>
</body>