<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
    <title>Administration Tasks</title>
    <content tag="menu">Administration</content>
    <%--<sx:head />--%>
</head>

<body>
<h2>Expanse Running Jobs</h2>

    <div>
        <h6> </h6>
    </div>
    <div class="data-content section">
        <div id="contents">
            <s:property value="expanseRunningJobs" escape="false"/>
        </div>
    </div> 
    <s:url id="outputUrl" action="userJobStatistics" method="getStatistics" includeParams="none"/>
    <s:a href="%{outputUrl}" cssClass="btn btn-primary">Return</s:a>
</body>
