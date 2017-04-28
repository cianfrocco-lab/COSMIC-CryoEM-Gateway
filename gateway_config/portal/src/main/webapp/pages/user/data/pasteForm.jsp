<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>Upload Data</title>
  <content tag="menu">Home</content>
</head>
<body>
<h2>Upload File</h2>
<div class="form-group">
  <div class="col-xs-10">
    Transfer files usng globus file transfer service.
    <s:url var="transferUrl" action="transfer"/>
    <s:a href="%{transferUrl}" cssClass="btn btn-primary">Transfer</s:a>
  </div>
</div>
<br><br>
<s:form action="pasteData" theme="simple" method="POST" enctype="multipart/form-data" cssClass="form-horizontal" role="form">
  <!--
  <div class="form-group">
    <label class="col-xs-2 control-label">Label</label>
    <div class="col-xs-10">
      <s:textfield cssClass="form-control" name="label" placeholder="Label"/>
    </div>
  </div>
  -->
  <div class="form-group">
    <label class="col-xs-2 control-label">Upload your files</label>
    <div class="col-xs-10">
      <s:file name="upload" multiple="multiple"/>
      <br>
      You can select multiple files.<br><br>
      MSIE 9 and below support single uploads only.
      <!--
      For Multiple Uploads, Click 
      <s:url id="uploadDataUrl" action="uploadData" method="upload"
        includeParams="none"/>
      <s:a cssClass="btn btn-link" href="%{uploadDataUrl}">Here</s:a>
      -->
    </div>
  </div>
  <hr class="hr-bluedots">
  <b>You can also enter your data manually below</b><p>
  <div class="form-group">
    <label class="col-xs-2 control-label">Label (required)</label>
    <div class="col-xs-10">
      <s:textfield cssClass="form-control" name="label" placeholder="Label"/>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Data:</label>
    <div class="col-xs-10">
      <s:textarea cssClass="form-control" name="source" placeholder="Enter your data"/>
    </div>
  </div>
  <!-- not using the Entity Type, Data Type, and Data Format fields anymore;
  also apparently don't need to close this comment because the include line
  will close it!
  <s:include value="/pages/common/dataUploadLists.jsp"/>
  <div class="form-group">
    <div class="col-xs-offset-2 col-xs-10">
      <s:submit value="Save" cssClass="btn btn-success" method="executePaste"/>
      <s:submit value="Cancel" cssClass="btn btn-primary" method="cancel"/>
    </div>
  </div>
</s:form>

</body>
