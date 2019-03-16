<%@ taglib prefix="s" uri="/struts-tags" %>
<h2>Current Folder Details</h2>
<div class="form-horizontal">
  <div class="form-group">
    <label class="col-xs-2 control-label">Label</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="currentFolder.label"/></span>
    </div>
  </div>
  <s:if test="%{currentFolderHasDescription()}">
    <div class="form-group">
      <label class="col-xs-2 control-label">Description</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="currentFolderDescription"/></span>
      </div>
    </div>
  </s:if>
</div>

<s:if test="%{isRegistered()}">
<div class="button-group">
  <s:if test="%{currentFolderHasParent() == false}">
    <s:url id="createSubfolderUrl" action="folder" method="create" includeParams="none">
      <s:param name="parentFolder" value="%{currentFolder.folderId}"/>
    </s:url>
    <s:a href="%{createSubfolderUrl}" cssClass="btn btn-primary mc-replace">Create Subfolder</s:a>
  </s:if>
  <!-- disable folder editing for now: need to be able to rename directory in the globus transfer directory if user renames folder!
  <s:url id="editFolderUrl" action="folder" method="edit" includeParams="none"/>
  <s:a href="%{editFolderUrl}" cssClass="btn btn-primary mc-replace">Edit Folder</s:a>
  -->

  <s:url id="deleteFolderUrl" action="folder" method="delete" includeParams="none"/>
  <s:a cssClass="btn btn-primary" href="javascript:confirm_delete('%{deleteFolderUrl}')">Delete Folder</s:a>
</div>
</s:if>
