<%@taglib prefix="s" uri="/struts-tags" %>
<%--<%@taglib prefix="sx" uri="/struts-dojo-tags" %>--%>

<head>
    <title>Administration Tasks</title>
    <content tag="menu">Administration</content>
    <%--<sx:head />--%>
</head>
<body>
<h3>User Account Activation</h3>
<s:form id="search-user-account-activation" cssClass="form-horizontal" action="userAccountActivationSearch" theme="simple">
  <div class="form-group">
    <div class="col-xs-10">
      Please enter a user email to search for his/her account activation status:
      <!--label class="col-xs-2 control-label">Please enter a user name/email to search for his/her job:</label-->
    </div>
    <div class="col-xs-10">
      <s:textfield cssClass="form-control" name="searchStrAA" placeholder="User Email"/>
    </div>
  </div>
  <div class="form-group form-buttons">
    <div class="col-xs-12">
      <s:submit value="Search" cssClass="btn btn-primary"/>
      <s:url id="outputUrl" action="administration" method="listTasks" includeParams="none"/>
      <s:a href="%{outputUrl}" cssClass="btn btn-primary">Return</s:a>
    </div>
  </div>


<div class="form-group">
<s:if test="%{hasSearchStrAA()}">
    <s:if test="%{isUserFound()}">
        <div class="col-xs-10">
            <hr>
        </div>        
        <s:if test="%{hasAccountActivated()}">
            <div class="col-xs-10">
                The activation status of user account with email <s:property value="%{searchStrAA}"/> is: activated.
            </div>
        </s:if>
        <s:else>
            <div class="col-xs-10">
                The user account with email <s:property value="%{searchStrAA}"/> has not yet been activated.<br>
                Would you like to activate this account on behalf of the user? If yes, please click below button.
            </div>
            <div class="col-xs-10">
                &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            </div>
            <div class="col-xs-12">
                <!--
                <s:url id="activateAccountUrl" action="adminTask" method="activateAccountOnBehalfOfUser" includeParams="none"/>
                -->
                <s:url id="activateAccountUrl" action="adminTask" method="activateAccountOnBehalfOfUser">
                    <s:param name="searchStrAA"><s:property value="%{searchStrAA}"/></s:param>
                </s:url>
                <s:a href="%{activateAccountUrl}" cssClass="btn btn-primary">Activate This Account on behalf of User</s:a>
            </div>
        </s:else>
    </s:if>
    <!--
    <s:else>
        <div class="col-xs-10">
        No user with that email address can be found in the database. Please make sure you enter the correct email address.
        </div>     
    </s:else>
    -->
</s:if>
</div>
</s:form>
</body>

