<%@ taglib prefix="s" uri="/struts-tags" %>

<head>
    <title>Administration Tasks</title>
    <content tag="menu">Administration</content>
</head>

<body>
    <h3>ADMIN TASKS</h3>
    <div id="folder-menu">
        <ul>
            <li>
                <s:url id="systemStatisticsUrl" action="userJobStatistics" method="getStatistics" includeParams="none" />
                <s:a href="%{systemStatisticsUrl}">System Statistics</s:a>
            </li>
            <li>
                <s:url id="multipleAccountUrl" action="adminTask" method="getMultipleAccounts" includeParams="none" />
                <s:a href="%{multipleAccountUrl}">Multiple accounts that belong to the same user</s:a>                
            </li>            
            <li>
                <s:url id="userSuUrl" action="userSuInfo" includeParams="none" />
                <s:a href="%{userSuUrl}">User SU Usages & Transactions</s:a>
            </li>
            <li>
                <s:url id="accountActivationUrl" action="adminTask" method="getAccountActivation" includeParams="none" />
                <s:a href="%{accountActivationUrl}">Account Activation</s:a>
            </li>
        </ul>
    </div>
            
</body>
