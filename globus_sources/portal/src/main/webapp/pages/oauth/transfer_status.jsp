<%--
  Created by IntelliJ IDEA.
  User: cyoun
  Date: 11/29/16
  Time: 2:33 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <title>Transfer Status</title>
    <content tag="menu">Transfer Status</content>
</head>
<body>
<%--<div class="container">
    <div class="page-header"> --%>
        <h2>Transfer Status</h2>
    <%--</div> --%>
    <s:if test="statuslist.size() > 0">
    <s:iterator value="statuslist" var="data">
    <p>
        <strong>Task ID</strong>: <s:property value="#data['task_id']" />
    </p>
    <p>
        <strong>Source endpoint</strong>: <s:property value="#data['source_endpoint_display_name']" />
    </p>
    <p>
        <strong>Destination Endpoint</strong>: <s:property value="#data['destination_endpoint_display_name']" />
    </p>
    <p>
        <strong>Request Time</strong>: <s:property value="#data['request_time']" />
    </p>
        <p>
            <strong>Completion Time</strong>: <s:property value="#data['completion_time']" />
        </p>
    <p>
        <strong>Status</strong>: <s:property value="#data['status']" />
    </p>
    <p>
        <strong>Files transferred</strong>: <s:property value="#data['files_transferred']" />
    </p>
        <p>
            <strong>Files skipped</strong>: <s:property value="#data['files_skipped']" />
        </p>
        <p>
            <strong>Bytes transferred</strong>: <s:property value="#data['bytes_transferred']" />
        </p>
    <p>
        <strong>Faults</strong>: <s:property value="#data['faults']" />
    </p>
    <s:if test="#data['status'] == 'ACTIVE' || #data['status'] == 'INACTIVE'">
    <div>
        <s:url var="statusUrl" action="status">
            <s:param name="taskId"><s:property value="#data['task_id']" /></s:param>
        </s:url>
        <s:a href="%{statusUrl}" cssClass="btn btn-primary">Refresh</s:a>
    </div>
    </s:if>
        <hr>
    </s:iterator>
    </s:if>
    <s:else>
        <p>
            <strong>No transfer activity</strong>
        </p>
    </s:else>
<%--</div>--%>
</body>
</html>
