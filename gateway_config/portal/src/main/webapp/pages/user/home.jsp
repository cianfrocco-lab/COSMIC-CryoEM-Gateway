<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ page isELIgnored='false'%>
<html>
<head>
  <title>Home</title>
  <content tag="menu">Home</content>
</head>
<body>
<div class="callout">
  <h5>
    Welcome
  </h5>
</div>

<s:if test="%{isRegistered()}">
  <div class="button-group">
    <s:url id="createFolderUrl" action="folder" method="create" includeParams="none"/>
    <s:a href="%{createFolderUrl}" cssClass="btn btn-primary mc-replace">Create New Folder</s:a>
  </div>
</s:if>

<s:if test="%{needProfileInfo() == true}">
	<script>gotoUpdateProfile();</script>
</s:if>

<s:if test="%{hasCurrentFolder()}">
  <s:include value="/pages/user/folder/displayFolder.jsp"/>
</s:if>
<s:elseif test="%{hasFolders()}">
  <div class="callout">
    <p>Click on a folder in the left panel to manage its contents.</p>
  </div>
</s:elseif>
</body>
</html>
