<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>Data</title>
  <content tag="menu">Home</content>
</head>
<body>
<s:if test="%{hasFolderData()}">
  <h2>All Data</h2>
  <div>
    <b>Upload Relion directories, particle stacks, and 3D volumes using
    the Globus data transfer service.<br>
    Upload 3D volume and other small files (&lt; 200 MB) using your
    browser.</b><br><br>
    <s:url var="transferUrl" action="transfer"/>
    <s:a href="%{transferUrl}" cssClass="btn btn-primary">
        Globus upload / download</s:a>
    <s:url id="uploadDataUrl" action="pasteData" method="upload"
        includeParams="none"/>
    <s:a cssClass="btn btn-primary mc-replace" href="%{uploadDataUrl}">
        Browser upload</s:a>
    <!-- https://getbootstrap.com/docs/3.3/javascript/
    <a class="btn btn-primary" role="button" data-toggle="collapse"
        href="#collapseExample" aria-expanded="false"
        aria-controls="collapseExample"> Collapse example</a>
    -->
    <!--
    <button class="btn btn-primary" type="button" data-toggle="collapse"
        data-target="#collapseExample" aria-expanded="false"
        aria-controls="collapseExample"> Collapse example </button>
        <div class="collapse" id="collapseExample">
            We can put the browser upload page here?!
        </div>
    -->
  </div>

  <div class="callout">
    <s:if test="%{currentTabSize == 1}">
      There is currently 1 data item in this folder.
    </s:if>
    <s:else>
      There are currently <s:property value="%{currentTabSize}"/>
      data items in this folder.
      (Items <s:property value="%{thisPageFirstElementNumber + 1}"/> -
      <s:property value="%{thisPageLastElementNumber + 1}"/> are shown here.)
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
    <s:select name="pageSize" list="#{ 2000:'2000' }"
      onchange="reload(this.form)" value="pageSizeString"/>
    records on each page
  </s:form>
  <s:form name="selectData" action="data" theme="simple">
    <h4>Data</h4>
    <table class="table table-striped">
      <!-- Field Headers -->
      <thead>
        <th>
          <s:checkbox cssClass="table-data-checkbox" name="allChecked"/>
          Select all
        </th>
        <!-- Data ID is not useful and no longer valid since we have 2 different data types now
        <th>Data ID</th>
        -->
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
          <th>Format</th>
          <th>Date Created</th>
        </s:elseif>
      </thead>
      
      <!-- Data Item Rows -->
      <s:set name="action" value="top"/>
      <s:iterator value="currentAllDataTab" id="dataItem" status="status">
        <s:set name="dataId" value="%{#dataItem.userDataId}"/>
        <tr>
          <td>
            <!--
            <s:checkbox cssClass="table-data-checkbox" name="selectedIds" fieldValue="%{#dataId}"
              value="%{selectedIds.{^ #this == #dataId}.size > 0}" theme="simple"/>
            -->
            <!-- Mona: included classname to distinguish between files and
            directories -->
            <s:checkbox cssClass="table-data-checkbox" name="selectedIds"
                fieldValue="%{#action.getClassName(#dataItem)}-%{#dataId}"
                value="%{selectedIds.{^ #this == #dataId}.size > 0}"
                theme="simple"/>
          </td>
          <!-- See above comment about Data ID column.  Keeping the following code in case we
               want to add a link to show row item details or content...
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
          --> 
          <!-- Row in the "All Data" tab -->
          <s:if test="%{isCurrentTabPhysical()}">
            <td><s:property value="%{#action.getLabel(#dataItem)}"/></td>
              <!--<td style="word-wrap: break-word"><s:property value="%{#action.getLabel(#dataItem)}"/></td>-->
            <td><s:property value="%{#action.getDataLength(#dataItem)}"/></td>
            <td><s:property value="%{#action.getDataFormat(#dataItem)}"/></td>
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
    <!-- if the following code gets re-enabled, update DataManager.java cancel()
    <div class="form-group">
      <div class="action-group">
        <div class="action-des pull-left">
          <s:select name="dataAction" list="#{'Copy':'Copy'}"/>
          &nbsp;selected to&nbsp;
          <s:select name="targetFolder" list="allFolders" listKey="folderId"
            listValue="label"/>&nbsp;
        </div>
        <s:submit cssClass="btn btn-primary" value="GO" method="cancel"/>
      </div>
    </div>
    -->
    <div class="form-group">
      <s:submit cssClass="btn btn-primary" value="Delete Selected" method="cancel" onclick="return confirm_form_data()"/>
    </div>
  </s:form>
</s:if>
<s:else>
  <div class="callout">
    There is currently no data in this folder.<br/>What would you like to do?
  </div>
  <div class="button-group">
    <s:url var="transferUrl" action="transfer"/>
    <s:a href="%{transferUrl}" cssClass="btn btn-primary">Upload Data via
        Globus</s:a>
    <s:url id="uploadDataUrl" action="pasteData" method="upload"
        includeParams="none"/>
    <s:a href="%{uploadDataUrl}" cssClass="btn btn-primary">Upload Data via
        browser</s:a>
    <s:url id="createTaskUrl" action="createTask" method="create"
        includeParams="none"/>
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
