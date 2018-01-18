<%@ taglib prefix="s" uri="/struts-tags" %>
<s:if test="%{hasInput()}">
  <s:if test="%{hasMappedInput()}">
    <div class="callout">
      You have selected the following data items to be set as input to the current task:
    </div>
    <table class="table table-striped">
      <!-- Field Headers -->
      <thead>
        <th>Label</th>
        <th>Bytes</th>
        <th>Data Format</th>
        <th>Date Created</th>
      </thead>
      <!-- Data Item Rows -->
      <s:set name="action" value="top"/>
      <s:iterator value="mappedInput" id="dataItem" status="status">
        <tr>
          <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
          <td><s:property value="%{#action.getDataLength(#dataItem)}"/></td>
          <td><s:property value="%{#action.getDataFormat(#dataItem)}"/></td>
          <td><s:property value="%{#action.getCreationDate(#dataItem)}"/></td>
        </tr>
      </s:iterator>
    </table>
  </s:if>
  <s:if test="%{hasUnmappedInput()}">
    <div class="callout">
        You have selected <s:property value="unmappedInputCount"/> data
        <s:property value="%{pluralize('item', getUnmappedInputCount())}"/>
        to be set as input to the current task that are no longer present in your user data.
    </div>
  </s:if>
  <div class="callout">
    If you would like to select new input, you may do so below:
  </div>
</s:if>

<s:if test="%{hasInputData()}">
  <div class="callout">
    You can choose the following data.
  </div>
  <s:form name="selectData" action="createTask" theme="simple">
  	<s:token/>
    <table class="table table-striped">
      <!-- Field Headers -->
      <thead>
        <th>Select One</th>
        <th>Label</th>
        <th>Bytes</th>
        <th>Data Format</th>
        <th>Date Created</th>
      </thead>
        
      <!-- Data Item Rows -->
      <s:set name="action" value="top"/>
      <s:iterator value="inputData" id="dataItem" status="status">
        <tr>
          <td>
            <!-- previous
            <s:checkbox name="selectedIds" fieldValue="%{#dataItem.userDataId}"
              value="%{selectedIds.{^ #this == #dataItem.userDataId}.size > 0}" theme="simple"
              onclick="ids.check(this)"/>
            -->
            <!-- Mona: included classname to distinguish between files and
            directories -->
            <s:checkbox name="selectedIds"
                fieldValue="%{#action.getClassName(#dataItem)}-%{#dataItem.userDataId}"
                value="%{selectedIds.{^ #this == #dataItem.userDataId}.size > 0}"
                theme="simple" onclick="ids.check(this)"/>
          </td>
          <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
          <td><s:property value="%{#action.getDataLength(#dataItem)}"/></td>
          <td><s:property value="%{#action.getDataFormat(#dataItem)}"/></td>
          <td><s:property value="%{#action.getCreationDate(#dataItem)}"/></td>
        </tr>
      </s:iterator>
    </table>
    <s:submit value="Select Data" method="execute" cssClass="btn btn-success"/>
    <s:submit value="Cancel" method="cancel" cssClass="btn btn-primary"/>
  </s:form>
</s:if>
<s:else>
  <div class="callout">
    There is currently no data available.<br/>What would you like to do?
  </div>
  <s:url id="uploadDataUrl" action="pasteData" method="upload" includeParams="none"/>
  <s:a href="%{uploadDataUrl}" cssClass="btn btn-primary">Upload Data</s:a>
</s:else>

<script type="text/javascript">
var ids = new CheckBoxGroup();
ids.addToGroup("selectedIds");
//ids.setControlBox("allChecked");
//ids.setMasterBehavior("all");
ids.setMaxAllowed(1, "You must uncheck the current selection to make a new selection.");
</script>
