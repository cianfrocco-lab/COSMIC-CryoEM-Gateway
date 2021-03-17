<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ include file="/pages/common/taglibs.jsp"%>
<%@ page isELIgnored='false'%>
<!---->
<div class="callout">
  You may edit your task using the tabs above.<br>
  Current SU Hr Usage: <s:property value="%{getCPUHours()}"/>
  <s:a href="javascript:popitup('%{staticSite}/help/cpu_help')">Explain this?</s:a>
</div>
<s:form action="createTask" theme="simple" cssClass="form-horizontal" role="form">
  <s:token/>
  <div class="form-group">
    <label class="col-xs-2 control-label">Description</label>
    <div class="col-xs-10">
      <s:textfield cssClass="form-control" name="label" value="%{currentTaskLabel}" placeholder="Description" onChange="submitform()"/>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Input</label>
    <div class="col-xs-10">
      <s:a href="%{dataTabUrl}" onclick="submitform()" cssClass="btn btn-primary">
        <s:if test="%{hasInput()}">
          <s:property value="inputCount"/> Input Set
        </s:if>
        <s:else>Select Input Data</s:else>
      </s:a>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Tool</label>
    <div class="col-xs-10">
      <s:a href="%{toolTabUrl}" onclick="submitform()" cssClass="btn btn-primary">
        <s:if test="%{hasTool()}">
          <span style="font-weight: bold">
            <s:property value="toolLabel"/>
          </span>
        </s:if>
        <s:else>Select Tool</s:else>
      </s:a>
      <s:if test="%{hasTool()}">
		<s:a href="javascript:popitup('%{staticSite}/tools/%{getTool().toLowerCase()}')">Click for more info</s:a>
      </s:if>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Input Parameters</label>
    <div class="col-xs-10">
      <s:a href="%{parametersTabUrl}" onclick="submitform()" cssClass="btn btn-primary">
        <s:if test="%{hasParameters() && hasTool()}">
          <s:property value="%{getParameterCount()}"/> Parameters Set
        </s:if>
        <s:else>Set Parameters</s:else>
      </s:a>
    </div>
  </div>
  <div class="form-group">
    <div class="col-xs-offset-2 col-xs-10">
      <s:submit value="Save Task" method="execute" cssClass="btn btn-success"/>
      <s:submit value="Save and Run Task" method="execute" onclick="warnAboutDelay()" cssClass="btn btn-success"/>
      <s:submit value="Discard Task" method="cancel" cssClass="btn btn-primary"/>
    </div>
  </div>
</s:form>
<div class="callout">
  Saved tasks can be run later from the task list<br>
  XSEDE tasks are limited to 168 hours. Non-XSEDE tasks are limited to 72 hours.
</div>

<SCRIPT language="JavaScript">
function submitform()
{
  /*
  alert(document.forms[0].label.value);
  alert(document.forms[0].action);
  alert(document.forms[0].param.value);
  var target = document.forms[0].action + "?tab=" + document.forms[0].param.value;
  document.forms[0].action = "http://localhost:8080/portal2/createTask!changeTab.action?tab=Select+Data";*/
  document.forms[0].submit();
}

function warnAboutDelay()
{
  alert("Submitting a job to run is a time consuming process. " +  
  "Pressing the back button, page reload or cancel, or opening new tabs, while your job is being submitted can " +
  "cause data corruption.  Please be patient.");
}
</SCRIPT> 
