<%--
  Created by IntelliJ IDEA.
  User: cyoun
  Date: 11/16/16
  Time: 4:48 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <title>Transfer</title>
    <content tag="menu">Transfer</content>
    <script src="https://code.jquery.com/jquery-1.12.4.min.js"
            integrity="sha256-ZosEbRLbNQzLpnKIkEdrPv7lOy9C27hHQ+Xp8a4MxAQ="
            crossorigin="anonymous"></script>
    <script type="text/javascript">
        $(document).ready(function() {

            $("#transfer-file-list").submit(function() {
                if(!$(':checkbox:checked').length) {
                    alert("Please select at least one file.");
                    //stop the form from submitting
                    return false;
                }
                <%--
                $(":checkbox").each(function(){
                    if(!this.checked){
                        this.checked=false;
                        alert("not checked")
                    }
                });
                --%>
                return true;
            });
        });
    </script>
</head>
<body>

<%-- <div class="container"> --%>

    <%-- <div class="page-header"> --%>
        <h2>Repository</h2>
    <%-- </div> --%>
    <%--
    <p><strong>Source Endpoint:</strong> <s:property value="%{#session.src_disp_name}"/> <strong>Path: </strong><s:property value="%{#session.src_endpoint_path}"/> </p>
    <p><strong>Destination Endpoint:</strong> <s:property value="%{#session.dest_disp_name}"/> <strong>Path: </strong><s:property value="%{#session.dest_endpoint_path}"/> </p>
    --%>
    <p><strong>Source Endpoint:</strong> <s:property value="%{#session.src_disp_name}"/></p>
    <p><strong>Destination Endpoint:</strong> <s:property value="%{#session.dest_disp_name}"/></p>
    <p>
        Select some dataset(s) to transfer.
    </p>
    <s:if test="files != null && files.size() > 0">
    <%-- <div class="form-group"> --%>
        <s:form id="transfer-file-list" cssClass="form-inline" action="transfer" method="POST" theme="simple">
            <s:hidden name="endpointId" value="%{#session.src_endpoint_id}"/>
            <s:hidden name="endpointPath" value="%{#session.src_endpoint_path}"/>
            <s:hidden name="endpointName" value="%{#session.src_endpoint_name}"/>
            <div class="row">
                <div class="col-md-10">
                    <table class="table">
                        <th class="col-md-3 text-left">Dataset Name</th>
                        <th class="col-md-1 text-left">Size</th>
                        <th class="col-md-1 text-center">Select</th>
                        <s:iterator value="files" status="stat">
                        <tr>
                            <s:if test='filetype == "dir"'>
                                <td class="col-md-3 text-left">
                                    <i class="fa fa-folder fa-lg"></i>&nbsp;
                                        <s:property value="filename"/>
                                </td>
                                <td class="col-md-1 text-left"><s:property value="filesize"/></td>
                                <td class="col-md-1 text-center"><s:checkbox name="directorynames" fieldValue="%{filename}"/></td>
                            </s:if>
                            <s:else>
                                <td class="col-md-3 text-left">
                                    <i class="fa fa-file fa-lg"></i>&nbsp;
                                        <s:property value="filename"/>
                                </td>
                                <td class="col-md-1 text-left"><s:property value="filesize"/></td>
                                <td class="col-md-1 text-center"><s:checkbox name="filenames" fieldValue="%{filename}"/></td>
                            </s:else>
                        </tr>
                        </s:iterator>
                    </table>
                </div>
            </div>

            <hr>
            <div class="form-group form-buttons">
                <div class="col-md-10 pull-right">
                    <s:submit name="transfer" value="Transfer" cssClass="btn btn-primary"/>
                </div>
            </div>
        </s:form>
    </s:if>
<s:else>
    <p>
        <strong>No File List</strong>
    </p>
</s:else>
    <%-- </div> --%> <!-- form -->

<%-- </div> --%> <!-- container -->

</body>
</html>
