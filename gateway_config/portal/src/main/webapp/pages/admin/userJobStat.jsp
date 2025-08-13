<%@taglib prefix="s" uri="/struts-tags" %>
<%--<%@taglib prefix="sx" uri="/struts-dojo-tags" %>--%>

<head>
    <title>Administration Tasks</title>
    <content tag="menu">Administration</content>
    <%--<sx:head />--%>
</head>
<body>
<h3>System Statistics</h3>
<s:url id="refreshStatsUrl" action="refreshStatistics" method="refresh" includeParams="none"/>
<s:a href="%{refreshStatsUrl}" cssClass="btn btn-default pull-right mc-refresh">
    <span class="glyphicon glyphicon-refresh"></span> Refresh
</s:a>

<table class="table">
            <!-- Field Headers -->
    <thead>
        <th></th>
        <th>Number of Jobs in Queue</th>
        <th>Number of Jobs Running</th>
    </thead>
    <tr>
        <td>Expanse</td>        
        <s:url id="crjUrl10" action="jobDetailsExpanseInQueue" method="getJobDetailsExpanseInQueue" includeParams="none"/>       
        <td><s:a href="%{crjUrl10}"><s:property value="%{expanseInQueueCount}"/></s:a></td>
        
        <s:url id="crjUrl20" action="jobDetailsExpanseRunning" method="getJobDetailsExpanseRunning" includeParams="none"/>
        <td><s:a href="%{crjUrl20}"><s:property value="%{expanseRunningCount}"/></s:a></td>
    </tr>    
</table>
    <s:url id="outputUrl" action="administration" method="listTasks" includeParams="none"/>
    <s:a href="%{outputUrl}" cssClass="btn btn-primary">Return</s:a>
</body>
