<%--
  Created by IntelliJ IDEA.
  User: cyoun
  Date: 11/30/16
  Time: 7:13 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <title>Endpoint List</title>
    <content tag="menu">Endpoints</content>
</head>
<body>

<h2>My Endpoints</h2>

<s:if test="bookmarklist.size() > 0">
    <div class="row">
        <div class="col-md-16">
            <table class="table">
                <th class="col-md-5 text-left">Endpoint</th>
                <th class="col-md-10 text-left">Path</th>
                <th class="col-md-1 text-center">List Files</th>

                <s:iterator value="bookmarklist" var="data">
                    <tr>
                        <s:form id="endpoint-list" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
                            <s:hidden name="bookmarkId" value="%{#data['id']}" />
                            <s:hidden name="endpointId" value="%{#data['endpoint_id']}" />
                            <s:hidden name="endpointName" value="%{#data['name']}" />

                            <td class="col-md-5 text-left">
                                <s:property value="#data['disp_name']" />
                            </td>

                            <td class="col-md-10 text-left">
                                <s:textfield cssClass="form-control" name="endpointPath" value="%{#data['path']}"/>
                            </td>
                            <td class="col-md-1 text-center">
                                <s:submit value="Open" cssClass="btn btn-primary"/>
                            </td>
                        </s:form>
                    </tr>
                </s:iterator>
            </table>
        </div>
    </div>
</s:if>
<s:else>
    <p>
        <strong>Please register your Globus Connect Personal Endpoints.</strong>
    </p>
</s:else>

<hr>
<h2>XSEDE Endpoint</h2>
<div class="row">
    <div class="col-md-6">
        <table class="table">
            <th class="col-md-5 text-left">Endpoint</th>
            <%-- <th class="col-md-10 text-left">Path</th> --%>
            <th class="col-md-1 text-center">List Files</th>
            <tr>
                <s:form id="server-endpoint" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
                    <s:hidden name="bookmarkId" value="XSERVER" />
                    <td class="col-md-5 text-left">
                        <s:property value="%{#session.dataset_endpoint_name}" />
                    </td>
                    <%--
                    <td class="col-md-10 text-left">
                        <s:property value="%{#session.dataset_endpoint_base}" />
                    </td>
                    --%>
                    <td class="col-md-1 text-center">
                        <s:submit value="Open" cssClass="btn btn-primary"/>
                    </td>
                </s:form>
            </tr>
        </table>
    </div>
</div>
</body>
</html>