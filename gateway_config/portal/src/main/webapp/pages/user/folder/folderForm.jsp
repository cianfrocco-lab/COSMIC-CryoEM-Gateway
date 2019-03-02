<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>Create Folder</title>
  <content tag="menu">Home</content>
</head>
<body>
  <h2>Enter Folder Detail</h2>
  <s:form id="save-folder" cssClass="form-horizontal" action="saveFolder" theme="simple" role="form">
    <div class="form-group">
      <label class="col-xs-2 control-label">Label</label>
      <div class="col-xs-10">
        <s:textfield cssClass="form-control" name="label" placeholder="Label"/>
        <div class="help-block">
            Only alpha-numeric, underscore, hyphen, and spaces are allowed
            as a folder label.
        </div>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Description</label>
      <div class="col-xs-10">
        <s:textarea cssClass="form-control" name="description" placeholder="Description"/>
      </div>
    </div>
    <div class="form-group form-buttons">
      <div class="col-xs-12">
        <s:submit value="Save" cssClass="btn btn-success"/>
        <s:submit value="Cancel" method="cancel" cssClass="btn btn-primary"/>
      </div>
    </div>
  </s:form>
</body>
