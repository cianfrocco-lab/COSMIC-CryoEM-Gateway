<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>Data</title>
  <content tag="menu">Home</content>
</head>
<body>
<s:if test="%{hasFolderData()}">
  <h2>All Data</h2>
  <div>
    <s:url id="uploadDataUrl" action="pasteData" method="upload"
        includeParams="none"/>
    <s:a cssClass="btn btn-primary mc-replace" href="%{uploadDataUrl}">
        Upload Data</s:a>
  </div>

  <div class="callout">
    <s:if test="%{currentTabSize != 1}">
      There are currently <s:property value="%{currentTabSize}"/>
      data items in this folder.
      (Items <s:property value="%{thisPageFirstElementNumber + 1}"/> -
      <s:property value="%{thisPageLastElementNumber + 1}"/> are shown here.)
    </s:if>
    <s:else>
      There is currently 1 data item in this folder.
    </s:else>
  </div>
  <s:url id="firstPageUrl" action="data" method="setPage" includeParams="none">
    <s:param name="page" value="%{'0'}"/>
  </s:url>
  <s:url id="previousPageUrl" action="data" method="setPage" includeParams="none">
    <s:param name="page" value="%{previousPageNumber}"/>
  </s:url>
  <s:url id="nextPageUrl" action="data" method="setPage" includeParams="none">
    <s:param name="page" value="%{nextPageNumber}"/>
  </s:url>
  <s:url id="lastPageUrl" action="data" method="setPage" includeParams="none">
    <s:param name="page" value="%{lastPageNumber}"/>
  </s:url>


  <%@ include file="/pages/common/pagination.jsp" %>
  <s:form action="paginateData" theme="simple">
    <s:select name="pageSize" list="#{ 20:'20', 40:'40', 100:'100', 200:'200' }"
      onchange="reload(this.form)" value="pageSizeString"/>
    records on each page
  </s:form>
  <s:form name="selectData" action="data" theme="simple">
    <h4>Use Data</h4>
    <table class="table table-striped">
      <!-- Field Headers -->
      <thead>
        <th>
          <s:checkbox cssClass="table-data-checkbox" name="allChecked"/>
          Select all
        </th>
        <th>User Data ID</th>
        <s:if test="%{isCurrentTabUnknown()}">
          <th>Label</th>
          <s:set name="action" value="top"/>
          <s:iterator value="currentTabFields" id="field">
            <th><s:property value="%{#action.getRecordField(#field)}"/></th>
          </s:iterator>
          <th>Date Created</th>
        </s:if>
        <s:elseif test="%{isCurrentTabPhysical()}">
          <th>Name</th>
          <th>Bytes</th>
          <th>Data Type</th>
          <th>Date Created</th>
        </s:elseif>
      </thead>
      
      <!-- Data Item Rows -->
      <s:set name="action" value="top"/>
      <s:iterator value="currentDataTab" id="dataItem" status="status">
        <s:set name="dataId" value="%{#dataItem.userDataId}"/>
        <tr>
          <td>
            <s:checkbox cssClass="table-data-checkbox" name="selectedIds" fieldValue="%{#dataId}"
              value="%{selectedIds.{^ #this == #dataId}.size > 0}" theme="simple"/>
          </td>
          <td>
            <s:url id="dataUrl" action="data" method="display" includeParams="none">
              <s:param name="id" value="%{#dataId}"/>
            </s:url>
            <span class="simpleLink">
              <s:a href="%{dataUrl}" cssClass="mc-replace">
                <s:property value="%{#dataId}"/>
              </s:a>
            </span>
          </td>
          
          <!-- Row in the "All Data" tab -->
          <s:if test="%{isCurrentTabPhysical()}">
            <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
            <td><s:property value="%{#action.getDataLength(#dataItem)}"/></td>
            <td><s:property value="%{#action.getDataType(#dataItem)}"/></td>
            <td><s:property value="%{#action.getCreationDate(#dataItem)}"/></td>
          </s:if>
          
          <!-- Row for an unparsable data item, with no data records -->
          <s:elseif test="%{#action.hasDataRecords(#dataItem) == false}">
            <s:if test="%{isCurrentTabUnknown()}">
              <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
            </s:if>
            <!-- Generate blank table cells for each unrepresented column -->
            <s:iterator value="currentTabFields"><td/></s:iterator>
            <s:if test="%{isCurrentTabUnknown()}">
              <td><s:property value="%{#action.getCreationDate(#dataItem)}"/></td>
            </s:if>
          </s:elseif>
          
          <!-- Row for a parsable data item, with data records -->
          <s:else>
            <s:iterator value="%{#action.getDataRecordList(#dataItem)}" id="dataRecord"
              status="recordStatus">
              <s:if test="%{isCurrentTabUnknown()}">
                <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
              </s:if>
              <s:iterator value="%{#action.getCurrentTabFields()}" id="field">
                <td>
                  <s:property value="%{#action.getDataRecordField(#dataRecord, #field)}"/>
                </td>
              </s:iterator>
              <s:if test="%{isCurrentTabUnknown()}">
                <td><s:property value="%{#action.getCreationDate(#dataItem)}"/></td>
              </s:if>
              <s:if test="%{#action.canDisplay()}">
                <td>
                  <!-- TODO: externalize this URL -->
                  <s:url id="siriusUrl" value="http://8ball.sdsc.edu/siriuswb/siriuswb.jsp"
                    includeParams="none">
                    <s:param name="username" value="%{authenticatedUsername}"/>
                    <s:param name="password" value="%{authenticatedPassword}"/>
                    <s:param name="input" value="%{#dataId}"/>
                    <s:param name="pass" value="%{authenticatedPassword}"/>
                    <s:param name="type" value="%{siriusType}"/>
                  </s:url>
                  <s:a href="javascript:popitup('%{siriusUrl}')">
                    <img src="<s:url value="/images/newstructure.jpg"/>" border="0"/>
                  </s:a>
                </td>
              </s:if>
            </s:iterator>
          </s:else>
        </tr>
      </s:iterator>
    </table>
    <div class="form-group">
      <div class="action-group">
        <div class="action-des pull-left">
          <s:select name="dataAction" list="#{'Move':'Move', 'Copy':'Copy'}"/>
          &nbsp;selected to&nbsp;
          <s:select name="targetFolder" list="allFolders" listKey="folderId"
            listValue="label"/>&nbsp;
        </div>
        <s:submit cssClass="btn btn-primary" value="GO" method="cancel"/>
      </div>
    </div>
    <div class="form-group">
      <s:submit cssClass="btn btn-primary" value="Delete Selected" method="cancel" onclick="return confirm_form()"/>
    </div>
  </s:form>
</s:if>
<s:else>
  <div class="callout">
    There is currently no data in this folder.<br/>What would you like to do?
  </div>
  <div class="button-group">
    <s:url id="uploadDataUrl" action="pasteData" method="upload" includeParams="none"/>
    <s:a href="%{uploadDataUrl}" cssClass="btn btn-primary">Upload Data</s:a>
    <s:url id="createTaskUrl" action="createTask" method="create" includeParams="none"/>
    <s:a href="%{createTaskUrl}" cssClass="btn btn-primary">Create a Task</s:a>
  </div>
</s:else>

<s:if test="%{needProfileInfo() == true}">
	<script>gotoUpdateProfile();</script>
</s:if>

<script type="text/javascript">
var ids = new CheckBoxGroup();
ids.addToGroup("selectedIds");
ids.setControlBox("allChecked");
ids.setMasterBehavior("all");
$('.table-data-checkbox').on('click', function(e) {
  ids.check($(this)[0]);
});
</script>

</body>
