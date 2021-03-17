<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ page isELIgnored='false'%>

<s:if test="%{hasFolderTasks()}">
  <s:url id="refreshTasksUrl" action="task" method="refresh"
         includeParams="none"/>
  <s:a href="%{refreshTasksUrl}" cssClass="btn btn-default pull-right mc-replace">
    <span class="glyphicon glyphicon-refresh"></span> Refresh Tasks
  </s:a>
  <h2>Tasks</h2>
  <div class="callout">
    <b>Current SU Hr Usage: <span class="red"><s:property value="%{getCPUHours()}"/></span></b>
    <s:a cssClass="btn btn-link" href="javascript:popitup('%{staticSite}/help/cpu_help')">Explain this?</s:a>
    <br>
    <s:if test="%{currentTabSize != 1}">
      There are currently <s:property value="%{currentTabSize}"/>
      tasks in this tab.
      (Items <s:property value="%{thisPageFirstElementNumber + 1}"/> -
      <s:property value="%{thisPageLastElementNumber + 1}"/> are shown here.)
    </s:if>
    <s:else>
      There is currently 1 data item in this tab.
    </s:else>
  </div>

  <div class="section">
    <s:url id="createTaskUrl" action="createTask" method="create" includeParams="none"/>
    <s:a href="%{createTaskUrl}" cssClass="mc-replace btn btn-primary">Create New Task</s:a>
    <s:if test="%{hasNewTask()}">
      <s:url id="continueTaskUrl" action="createTask" method="edit" includeParams="none"/>
      <s:a href="%{continueTaskUrl}" cssClass="mc-replace btn btn-primary">Continue Editing Current Task</s:a>
    </s:if>
  </div>
  
  <s:url id="firstPageUrl" action="task" method="setPage" includeParams="none">
    <s:param name="page" value="%{'0'}"/>
  </s:url>
  <s:url id="previousPageUrl" action="task" method="setPage" includeParams="none">
    <s:param name="page" value="%{previousPageNumber}"/>
  </s:url>
  <s:url id="nextPageUrl" action="task" method="setPage" includeParams="none">
    <s:param name="page" value="%{nextPageNumber}"/>
  </s:url>
  <s:url id="lastPageUrl" action="task" method="setPage" includeParams="none">
    <s:param name="page" value="%{lastPageNumber}"/>
  </s:url>


  <%@ include file="/pages/common/pagination.jsp" %>
  <s:form action="paginateTasks" theme="simple">
      Show
      <s:select name="pageSize" list="#{ 20:'20', 40:'40', 100:'100', 200:'200' }"
        onchange="reload(this.form)" value="pageSizeString"/>
      records on each page
  </s:form>

  <s:form name="selectTasks" action="task" theme="simple">
    <table class="table">
      <!-- Field Headers -->
      <thead>
        <th>
          <s:checkbox name="allChecked" onclick="ids.check(this)"/>&nbsp;Select&nbsp;All
        </th>
        <th></th>
        <th>Label</th>
        <th>Tool</th>
        <th>Input</th>
        <th>Parameters</th>
        <th>Date Created</th>
        <th>Action</th>
      </thead>
      
      <!-- Task Rows -->
      <s:set name="action" value="top"/>
      <s:iterator value="currentTaskTab" id="task" status="status">
        <s:if test="%{#action.hasError(#task)}">
          <tr class="danger">
        </s:if>
        <s:else>
          <tr>
        </s:else>
          <td>
            <s:checkbox name="selectedIds" fieldValue="%{#task.taskId}"
              value="%{selectedIds.{^ #this == #task.taskId}.size > 0}" theme="simple"
              onclick="ids.check(this)"/>
          </td>
          <td>
            <s:url id="cloneTaskUrl" action="createTask" method="clone"
              includeParams="none">
              <s:param name="task" value="%{#task.taskId}"/>
            </s:url>
            <s:a href="%{cloneTaskUrl}" cssClass="mc-replace btn btn-default">Clone</s:a>
          </td>
          <s:url id="taskUrl" action="task" method="display" includeParams="none">
            <s:param name="id" value="%{#task.taskId}"/>
          </s:url>
          <td>
			<!--
            <s:a cssClass="btn btn-link mc-replace" href="%{taskUrl}" title="%{#task.label}">
              <s:property value="%{#action.getLabel(#task)}"/>
            </s:a>
			-->
            <s:a cssClass="btn btn-link mc-replace" href="%{taskUrl}" title="%{#task.jobHandle}">
              <s:property value="%{#action.getLabel(#task)}"/>
            </s:a>
          </td>
          <td><span class="task-list-td"><s:property value="%{#action.getToolLabel(#task)}"/></span></td>
          <td>
            <s:if test="%{#action.hasMainInput(#task)}">
              <s:url id="inputUrl" action="task" method="displayInput" includeParams="none">
                <s:param name="id" value="%{#task.taskId}"/>
              </s:url>
              <s:a cssClass="btn btn-link" href="javascript:popitup('%{inputUrl}')">
                View (<s:property value="%{#action.getMainInputCount(#task)}"/>)
              </s:a>
            </s:if>
            <s:else><span class="task-list-td">None</span></s:else>
          </td>
          <td>
            <s:if test="%{#action.hasParameters(#task)}">
              <s:url id="parametersUrl" action="task" method="displayParameters"
                includeParams="none">
                <s:param name="id" value="%{#task.taskId}"/>
              </s:url>
              <s:a cssClass="btn btn-link" href="javascript:popitup('%{parametersUrl}')">
                View (<s:property value="%{#action.getParameterCount(#task)}"/>)
              </s:a>
            </s:if>
            <s:else><span class="task-list-td">None</span></s:else>
          </td>
          <td><span class="task-list-td"><s:property value="%{#action.getCreationDate(#task)}"/></span></td>
          <td>
            <s:if test="%{#action.isReady(#task)}">
              <s:url id="runTaskUrl" action="createTask" method="run"
                includeParams="none">
                <s:param name="id" value="%{#task.taskId}"/>
              </s:url>
              <s:a href="%{runTaskUrl}" cssClass="btn btn-default" onclick="warnAboutDelay()">Run Task</s:a>
            </s:if>
            <s:elseif test="%{#action.isCompleted(#task)}">
              <s:url id="outputUrl" action="setTaskOutput" method="displayOutput"
                includeParams="none">
                <s:param name="id" value="%{#task.taskId}"/>
              </s:url>
              <s:if test="%{#action.hasOutput(#task)}">
                <s:a cssClass="btn btn-default mc-replace" href="%{outputUrl}">View Output</s:a>
              </s:if>
              <s:else><a href="%{outputUrl}" disabled="true" class="btn btn-default">View Output</a></s:else>
            </s:elseif>
            <s:elseif test="%{#action.hasError(#task)}">
              <s:a href="%{taskUrl}" cssClass="btn btn-default mc-replace">View Error</s:a>
            </s:elseif>
            <s:else>
              <s:a href="%{taskUrl}" cssClass="btn btn-default mc-replace">View Status</s:a>
            </s:else>
          </td>
        </tr>
      </s:iterator>
    </table>
    <%--<div class="form-group">
      <div class="action-group">
        <div class="action-des pull-left">
          <s:hidden name="taskAction" value="%{'Move'}"/>
          Move selected to
          <s:select name="targetFolder" list="allFolders" listKey="folderId"
            listValue="label"/>
        </div>
        <s:submit value="GO" method="cancel" cssClass="btn btn-primary"/>
      </div>
    </div>
	--%>
    <div class="form-group">
      <!-- not functional yet
      <s:submit cssClass="btn btn-primary" value="Download Selected" method="downloadSelected"/>
      -->
      <s:submit cssClass="btn btn-primary" value="Kill and Delete Selected" method="deleteSelected" onclick="return confirm_form()"/>
    </div>
  </s:form>
</s:if>
<s:else>
  <div class="callout">
    There are currently no tasks in this folder.
  </div>
  <div class="section">
    <s:url id="createTaskUrl" action="createTask" method="create" includeParams="none"/>
    <s:a href="%{createTaskUrl}" cssClass="mc-replace btn btn-primary">Create New Task</s:a>
    <s:if test="%{hasNewTask()}">
      <s:url id="continueTaskUrl" action="createTask" method="edit" includeParams="none"/>
      <s:a href="%{continueTaskUrl}" cssClass="mc-replace btn btn-primary">Continue Editing Current Task</s:a>
    </s:if>
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

function warnAboutDelay()
{
  alert("Submitting a job to run is a time consuming process. " +  
  "Pressing the back button, page reload or cancel, or opening new tabs, while your job is being submitted can " +
  "cause data corruption.  Please be patient.");
}
</script>
