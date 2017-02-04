<%@ taglib prefix="s" uri="/struts-tags" %>

<head>
  <title>Data</title>
  <content tag="menu">Home</content>
</head>
<body>
  <h2>View Data Details</h2>
  <div class="form-horizontal">
    <div class="form-group">
      <label class="col-xs-2 control-label">Label</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="label"/></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Owner</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="owner" /></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Group</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="group" /></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Bytes</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="dataLength" /></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Date Created</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="creationDate" /></span>
      </div>
    </div>
    <div class="form-group">
      <label class="col-xs-2 control-label">Data Type</label>
      <div class="col-xs-10">
        <span class="form-control"><s:property value="dataType" /></span>
      </div>
    </div>
  </div>

  <s:if test="%{hasFields()}">
    <h2>Data Details</h2>
    <s:set name="action" value="top"/>
    <s:iterator value="fields" id="field" status="status">
      <div class="form-horizontal">
        <div class="form-group">
          <label class="col-xs-2 control-label"><s:property value="%{#action.getFieldLabel(#field)}"/></label>
          <div class="col-xs-10">
            <span class="form-control"><s:property value="%{#action.getFieldValue(#field)}"/></span>
          </div>
        </div>
      </div>
    </s:iterator>
  </s:if>

  <s:if test="%{hasDataRecords()}">
    <h2>Data Records</h2>
    <table class="table-striped">
      <!-- Field Headers -->
      <thead>
        <s:set name="action" value="top"/>
        <s:iterator value="dataRecordFields" id="field">
          <th><s:property value="%{#action.getFieldLabel(#field)}"/></th>
        </s:iterator>
      </thead>
      
      <!-- Data Item Rows -->
      <s:iterator value="dataRecordList" id="dataRecord" status="status">
        <tr>
          <%-- TODO: make clickable link to drill down into the data record's source --%>
          <s:iterator value="dataRecordFields" id="field">
            <td><s:property value="%{getFieldValue(#dataRecord, #field)}"/></td>
          </s:iterator>
        </tr>
      </s:iterator>
    </table>
  </s:if>

  <s:if test="%{hasSourceDocument()}">
    <div class="data-content section">
      <s:a id="show-content-toggle" cssClass="btn btn-link" href="#">Show/Hide Data Contents</s:a>
      <%-- s:if test="%{getDataType() == 'Phylogenetic Tree'}" --%>
      <s:if test="%{getDataFormat() == 'Newick (Phylip) Tree [.dnd]'}">
        <s:if test="%{isDrawConfigured()}">
          <s:url id="drawurl" action="draw" > </s:url>
          | <s:a cssClass="btn btn-link" href="javascript:popitup('%{drawurl}')">Draw Tree</s:a>
        </s:if>  
      </s:if>
      <s:elseif test="%{getDataType() == 'Image'}">
          Tianqi, start here
      </s:elseif>
      <div id="contents">
        <s:property value="formattedSourceData" escape="false"/>
      </div>
    </div>
  </s:if>

  <div class="section">
    <s:url id="listDataUrl" action="data" method="list" includeParams="none"/>
    <s:a href="%{listDataUrl}" cssClass="btn btn-primary mc-replace">Return To Data List</s:a>

    <s:url id="downloadDataUrl" action="data" method="download" includeParams="none"/>
    <s:a href="%{downloadDataUrl}" cssClass="btn btn-primary">Download Data</s:a>

    <s:url id="deleteDataUrl" action="data" method="delete" includeParams="none"/>
    <s:a href="javascript:confirm_delete('%{deleteDataUrl}')" cssClass="btn btn-primary">Delete Data</s:a>
  </div>
</body>
