<%@ taglib prefix="s" uri="/struts-tags" %>


<s:form id="search-user-jobs" cssClass="form-horizontal" action="adminTaskMA" theme="simple">
    <div class="form-group">
        <div class="col-xs-10">
            Please enter a date to search for a multiple account report:
            <!--label class="col-xs-2 control-label">Please enter a user name/email to search for his/her job:</label-->
        </div>
        <div class="col-xs-10">
            <s:textfield cssClass="form-control" name="searchMAStr" placeholder="yyyy-MM-dd or MM-dd-yyyy or yyyy/MM/dd or MM/dd/yyyy"/>
        </div>
    </div>

    <div class="form-group form-buttons">
        <div class="col-xs-12">
            <!--
            <s:url id="searchMAStrUrl" action="adminTask" method="searchMultipleAccountReport">
                <s:param name="searchMAStr"><s:property value="%{searchMAStr}"/></s:param>
            </s:url>
            <s:a href="%{searchMAStrUrl}" cssClass="btn btn-primary">Search</s:a>
                -->
            <s:submit value="Search" cssClass="btn btn-primary"/>
        </div>
    </div>    
</s:form>

<s:if test="%{anyReportFound()}">
    <s:form id="s=multiple-accounts-header" cssClass="form-horizontal" action="adminTask" theme="simple">
        <h2>Report: users who logged in multiple accounts from the same ip on <s:property value="%{reportDate}"/></h2>
        <s:if test="%{hasBlockedAccounts()}">
            <div class="callout">
                Below account(s) have been blocked:<br><s:property value="%{blockedAccounts}"/>
            </div>
        </s:if>
        <s:if test="%{hasUnblockedAccounts()}">
            <div class="callout">
                Below account has been unblocked:<br><s:property value="%{unblockedAccounts}"/>
            </div>
        </s:if>
        <s:else>
            <div class="callout">
                This is a list of users who logged in multiple accounts from the same ip on <s:property value="%{reportDate}"/>. If you feel that anyone in the list is having multiple accounts, 
                you can block that user from submitting jobs in all of his/her accounts except one.
            </div>    
        </s:else>
    </s:form>
    <s:form id="multiple-accounts-report" action="adminTaskBlock" theme="simple">
        <div class="col-xs-12">
            <!--
            <s:url id="activateAccountUrl" action="adminTask" method="activateAccountOnBehalfOfUser">
                <s:param name="searchStr"><s:property value="%{searchStr}"/></s:param>
            </s:url>
            <s:a href="%{activateAccountUrl}" cssClass="btn btn-primary">Block Selected Account(s)</s:a>
                -->
            <s:submit value="Block Selected Account(s)" cssClass="btn btn-primary"/>
        </div>
        <s:hidden name="previousReportDate" value="%{reportDate}" />
        <table class="table table-striped" >
            <thead>
            <th>&nbsp;</th>
        </thead>
        <s:iterator value="multipleAccountsList" id="account">
            <tr><td><h5><s:property value="%{#account.ip}"/></h5></td></tr>
            <tr><td><s:property value="%{#account.owner}"/></td></tr>
            <tr><td><s:property value="%{#account.location}"/></td></tr>
            <s:iterator value="%{#account.users}" id="user">
                <s:if test="%{#user.blocked}">
                    <s:if test="%{#user.exist}">
                        <s:url id="unblockAccountUrl" action="adminTask" method="unblockUser">
                            <s:param name="pureUser"><s:property value="%{#user.pureUser}"/></s:param>
                            <s:param name="previousReportDate"><s:property value="%{reportDate}"/></s:param>
                        </s:url>
                        <s:if test="%{#user.paid}">
                            <tr><td style="background-color:lightsalmon">&nbsp;&nbsp;<s:checkbox name="selectedIdsX" fieldValue="false"
                                        disabled="true" theme="simple"/>&nbsp;<s:property value="%{#user.user}"/>&nbsp;&nbsp;<s:a href="%{unblockAccountUrl}" cssClass="btn btn-primary">Unblock</s:a>&nbsp;&nbsp;<b>(* Paid Subscription, user_id:&nbsp;<s:property value="%{#user.userId}"/>, can_submit:&nbsp;false)</b></td></tr> 
                                </s:if>
                                <s:else>
                            <tr><td style="background-color:lightsalmon">&nbsp;&nbsp;<s:checkbox name="selectedIdsX" fieldValue="false"
                                        disabled="true" theme="simple"/>&nbsp;<s:property value="%{#user.user}"/>&nbsp;&nbsp;<s:a href="%{unblockAccountUrl}" cssClass="btn btn-primary">Unblock</s:a></td></tr> 
                                </s:else>
                            </s:if>
                            <s:else>
                        <tr><td>&nbsp;&nbsp;<s:checkbox name="selectedIdsX" fieldValue="false"
                                    disabled="true" theme="simple"/>&nbsp;<s:property value="%{#user.user}"/></td></tr>
                            </s:else>
                        </s:if>
                        <s:else>
                            <s:if test="%{#user.paid}">
                        <tr><td style="background-color:lightgreen">&nbsp;&nbsp;<s:checkbox name="selectedIdsX" fieldValue="false"
                                    disabled="true" theme="simple"/>&nbsp;<s:property value="%{#user.user}"/>&nbsp;&nbsp;<b>(* Paid Subscription, user_id:&nbsp;<s:property value="%{#user.userId}"/>, can_submit:&nbsp;true)</b></td></tr>                          
                            </s:if>
                            <s:else>
                        <tr><td style="background-color:lightgreen">&nbsp;&nbsp;<s:checkbox name="buser_%{#user.pureUser}" id="buser_%{#user.pureUser}"
                                    disabled="false" theme="simple"/>&nbsp;<s:property value="%{#user.user}"/></td></tr>
                            </s:else>               
                        </s:else>
                    </s:iterator>
            <tr style="background-color: #2d6ca2"><td style="background-color: #2d6ca2"></td></tr>
            <!--tr style="background-color:#FF0000"><td></td></tr-->
        </s:iterator>
    </table>
    <div class="col-xs-12">
        <s:submit value="Block Selected Account(s)" cssClass="btn btn-primary"/>
    </div>    
</s:form>

</s:if>
<s:else>
    <s:if test="%{anyReportExist()}">
        <s:if test="%{isSearchMAStrNotEmpty()}">
            <div class="col-xs-10">
                Report that matches the specified date: <s:property value="%{searchMAStr}"/> has no content. No user who logged in multiple accounts on that day is found. 
            </div>     
        </s:if>
    </s:if>
    <s:else>
        <div class="col-xs-10">
            No report is found that matches the specified date: <s:property value="%{searchMAStr}"/>. Please make sure you enter the date in correct format.
        </div>     
    </s:else>
</s:else>
