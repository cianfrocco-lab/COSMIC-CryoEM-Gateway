<%@ taglib prefix="s" uri="/struts-tags" %>

<h2>View Task Output Details</h2>

<!-- disabled for COSMIC2
<div class="section">
  <s:url id="saveOutputUrl" action="setTaskOutput" method="input" includeParams="none"/>
  <s:a cssClass="btn btn-primary" href="%{saveOutputUrl}">Save To Current Folder</s:a>
  <s:url id="outputUrl" action="setTaskOutput" method="display" includeParams="none"/>
  <s:a href="%{outputUrl}" cssClass="btn btn-primary">Return</s:a>
</div>
-->

<div class="section">
  <s:url id="saveFileUrl" action="setTaskOutput" method="displayOutputFile" includeParams="none"/>
  <s:a href="%{saveFileUrl}" cssClass="btn btn-primary">Download File</s:a>
  <s:a href="%{outputUrl}" cssClass="btn btn-primary">Return</s:a>
</div>

<div class="form-horizontal">
  <div class="form-group">
    <label class="col-xs-2 control-label">Tool</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="toolLabel"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">File Name</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="filename" /></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">File Size</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="outputFileSize" /> Bytes</span>
    </div>
  </div>
</div>

<s:if test="%{canRead()}">
  <table class="table table-striped">
    <!-- Field Headers -->
    <thead>
      <s:if test="%{canTransform()}">
        <th>Results</th>
      </s:if>
      <s:set name="action" value="top"/>
      <s:iterator value="recordFields" id="field">
        <th><s:property value="%{#action.getRecordField(#field)}"/></th>
      </s:iterator>
    </thead>
        
    <!-- Data Item Rows -->
    <s:set name="action" value="top"/>
    <s:iterator value="dataRecordList" id="dataRecord" status="status">
      <s:if test="%{#status.odd == true}">
        <s:set name="rowclass" value="%{'tableroww'}"/>
      </s:if>
      <s:else>
        <s:set name="rowclass" value="%{'tablerowb'}"/>
      </s:else>
      <tr class="<s:property value="#rowclass"/>">
        <s:if test="%{canTransform()}">
          <td>
            <s:url id="transformUrl" action="setTaskOutput" method="displayTransformedOutput"
              includeParams="none">
              <s:param name="index" value="#dataRecord.index"/>
            </s:url>
            <span class="simpleLink">
              <s:a href="%{transformUrl}">View</s:a>
            </span>
          </td>
        </s:if>
        <s:iterator value="%{#action.getRecordFields()}" id="field">
          <td>
            <s:property value="%{#action.getShortDataRecordField(#dataRecord, #field)}"/>
          </td>
        </s:iterator>
      </tr>
    </s:iterator>
  </table>
</s:if>

<s:if test="%{canDisplay()}">

  <div class="data-content section">
    <s:a href="#" id="show-content-toggle">Show/Hide Output Contents</s:a>
    <div id="contents">
      <s:property value="formattedOutput" escape="false"/>
    </div>
  </div>

</s:if>
