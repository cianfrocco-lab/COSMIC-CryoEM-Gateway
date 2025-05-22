<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
<%@ taglib prefix="s" uri="/struts-tags" %>

<script type="text/javascript">
  let refreshIntervalId;

  function refreshElement() {
    const hiddenElement1 = document.getElementById("taskStage");
    if (hiddenElement1 == null)
      return;
    const hiddenValue1 = hiddenElement1.value;
    if (hiddenValue1 != "SUBMITTED (in waiting queue...)" && hiddenValue1 != "LOAD_RESULTS" && hiddenValue1 != "QUEUE" && hiddenValue1 != "INPUTSTAGING" && hiddenValue1 != "COMMANDRENDERING")
      return;
    $.ajax({
      type: 'POST',
      url: '<s:url action="refreshStatus"/>',
      success: function(dataitem) {
        var data = dataitem.currentTaskStageDivide;
        var task_log_messages = dataitem.taskMessagesDivide;
        var new_td = "NONE";
        var inter1 = document.getElementById("inter1");;
        var inter2 = document.getElementById("inter2");;

        new_td = data;
 
        if (new_td  == "SUBMITTED (running...)")
           new_td += "<p><b>**Please click the 'Refresh Task' button to check whether the job is completed**</b></p>";
        else if (data == "QUEUE" || data == "COMMANDRENDERING" || data == "INPUTSTAGING" || data == "SUBMITTED (in waiting queue...)" || data == "LOAD_RESULTS")
           new_td += "&nbsp;<i class=\"fa fa-spinner fa-pulse fa-lg fa-fw\"></i>";

        new_td += " <input type=\"hidden\" id=\"taskStage\" name=\"taskStage\" value=\"" + data +  "\">";

        if (data != "QUEUE" && data != "COMMANDRENDERING" && data != "INPUTSTAGING" && data != "LOAD_RESULTS" && data != "COMPLETED")
        {
           if (inter1 != null)
             inter1.hidden = true;
           if (inter2 != null)
             inter2.hidden = false;
        }
        $('#contentToRefresh').html(new_td);
        $('#contentToRefresh2').html(task_log_messages);
      }
    });
  }

  refreshIntervalId = setInterval(refreshElement, 5000);

</script>

<s:if test="%{isRefreshable()}">
  <s:url id="refreshTaskUrl" action="task" method="display"
         includeParams="none">
    <s:param name="id" value="currentTaskId"/>
  </s:url>
  <s:a href="%{refreshTaskUrl}" cssClass="btn btn-default pull-right">
    <span class="glyphicon glyphicon-refresh"></span> Refresh Task
  </s:a>
</s:if>
<h2>Task Details</h2>
<div class="form-horizontal">
  <div class="form-group">
    <label class="col-xs-2 control-label">Task</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="currentTaskLabel"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Owner</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="currentTaskOwner"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Group</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="currentTaskGroup"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Date Created</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="currentTaskCreationDate"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Tool</label>
    <div class="col-xs-10">
      <span class="form-control"><s:property value="toolLabel"/></span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Input</label>
    <div class="col-xs-10">
      <span class="form-control">
        <s:if test="%{hasMainInput()}">
          <s:url id="inputUrl" action="task" method="displayInput" includeParams="none"/>
          <s:a href="javascript:popitup('%{inputUrl}')">
            View (<s:property value="mainInputCount"/>)
          </s:a>
        </s:if>
        <s:else>None</s:else>
      </span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Parameters</label>
    <div class="col-xs-10">
      <span class="form-control">
        <s:if test="%{hasParameters()}">
          <s:url id="parametersUrl" action="task" method="displayParameters"
            includeParams="none"/>
          <s:a href="javascript:popitup('%{parametersUrl}')">
            View (<s:property value="parameterCount"/>)
          </s:a>
        </s:if>
        <s:else>None</s:else>
      </span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Output</label>
    <div class="col-xs-10">
      <span class="form-control">
        <s:if test="%{hasOutput()}">
          <s:url id="outputUrl" action="setTaskOutput" method="displayOutput"
            includeParams="none"/>
          <s:a href="%{outputUrl}">
            View (<s:property value="outputCount"/>)
          </s:a>
        </s:if>
        <s:else>None</s:else>
      </span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Intermediate Results</label>
    <div class="col-xs-10">
      <span class="form-control">
        <s:url id="workingDirectoryUrl" action="task" method="displayWorkingDirectory"
               includeParams="none"/>
        <s:if test="%{hasWorkingDirectory()}">
          <s:a href="javascript:popitup('%{workingDirectoryUrl}')">List</s:a>
        </s:if>
        <s:else><div id="inter1">None</div><div id="inter2" hidden><s:a href="javascript:popitup('%{workingDirectoryUrl}')">List</s:a></div></s:else>
      </span>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Status</label>
    <div class="col-xs-10">
      <span class="form-control">
            <div id="contentToRefresh">
                <s:if test="currentTaskStage=='SUBMITTED (in waiting queue...)'">
                  <s:property value="currentTaskStage"/>&nbsp;<i class="fas fa-hourglass-half"></i>
                </s:if>
                <s:elseif test="currentTaskStage=='SUBMITTED (running...)'">
                  <s:property value="currentTaskStage"/><p><b>**Please click the 'Refresh Task' button to check whether the job is completed**</b></p>
                </s:elseif>
                <s:elseif test="%{currentTaskStage=='QUEUE' || currentTaskStage=='INPUTSTAGING' || currentTaskStage=='COMMANDRENDERING' || currentTaskStage=='LOAD_RESULTS'}">
                  <s:property value="currentTaskStage"/>&nbsp;<i class="fa fa-spinner fa-pulse fa-lg fa-fw"></i>
                </s:elseif>
                <s:else>
                  <s:property value="currentTaskStage"/>
                </s:else>
                <input type="hidden" id="taskStage" name="taskStage" value="<s:property value='currentTaskStage'/>">
            </div>
      </span>
    </div>
  </div>
</div>

<s:if test="%{hasTaskMessages()}">
  <h3>Task Messages</h3>
  <div class="message-box">
    <div id="contentToRefresh2">
    <s:iterator value="taskMessages" id="message">
      <s:property value="#message"/><br/>
    </s:iterator>
    </div>
  </div>
</s:if>
  
<div class="section">
  <s:url id="listTasksUrl" action="task" method="list" includeParams="none"/>
  <s:a cssClass="btn btn-primary" href="%{listTasksUrl}">Return To Task List</s:a>

  <s:url id="deleteTaskUrl" action="task" method="delete" includeParams="none"/>
  <s:a cssClass="btn btn-primary" href="javascript:confirm_delete('%{deleteTaskUrl}')">Delete Task</s:a>
</div>
