<%--
  Created by IntelliJ IDEA.
  User: cyoun
  Date: 06/20/17
  Time: 7:13 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <title>Endpoint List</title>
    <content tag="menu">Endpoints</content>
    <link href = "https://code.jquery.com/ui/1.10.4/themes/ui-lightness/jquery-ui.css"
          rel = "stylesheet">
    <script src = "https://code.jquery.com/jquery-1.10.2.js"></script>
    <script src = "https://code.jquery.com/ui/1.10.4/jquery-ui.js"></script>

    <style type="text/css">
        #bootstrapSelectForm .selectContainer .form-control-feedback {
            /* Adjust feedback icon position */
            right: -15px;
        }
    </style>

    <!-- Javascript -->
    <script>
        $(function() {
            $("#search").autocomplete({
                source: function (request, response) {
                    $.getJSON("endpointlistJSON.action?fulltext=" + encodeURIComponent($("#search").val()),
                        function (data) {
                            response($.map(data, function (value, key) {
                                return {
                                    label: value,
                                    value: key
                                };
                            }));
                    });
                },
                minLength: 2,
                focus: function( event, ui ) {
                    $( "#search" ).val( ui.item.label );
                    $( "#searchValue" ).val( ui.item.value );
                    $( "#search_p" ).html( ui.item.value );
                    return false;
                },
                select: function( event, ui ) {
                    $( "#search" ).val( ui.item.label );
                    $( "#searchValue" ).val( ui.item.value );
                    $( "#search_p" ).html( ui.item.value );
                    return false;
                }
            });

            $("#myeplist").on('change', function() {
                var s_text = $("#myeplist option:selected").text();

                $( "#myendpointName" ).val( s_text );
                $( "#myendpoint_p" ).html( s_text );
            });

            $("#endpoint-search-form").submit(function() {

                var s_text = $( "#search" ).val();
                if (s_text.length === 0) {
                    alert("Please search the endpoint to add.");
                    return false;
                };
                $( "#myendpointName" ).val("");
                return true;
            });

            $("#my-endpoint-search-form").submit(function() {
                var s_value = $("#myeplist").val();
                if ( s_value == "-1") {
                    alert("Please select your endpoint to add.")
                    return false;
                }
                $( "#searchValue" ).val("");
                return true;
            });
        });

    </script>

</head>
<body>

<div class="container">
        <div class="row">
            <s:form id="endpoint-search-form" cssClass="form-horizontal" action="endpointlist" method="POST" theme="simple">
            <div class="col-sm-4" style="background-color:lavender;">
                <label for="search">Endpoint Search:</label>
                <s:textfield cssClass="form-control" id="search" name="searchLabel"/>
                <s:hidden id="searchValue" name="searchValue" value=""/>
                <p id="search_p"></p>
                <s:submit id="ep-search" name="ep-search" value="Add" cssClass="btn btn-primary"/>
            </div>
            </s:form>
            <s:form id="my-endpoint-search-form" cssClass="form-horizontal" action="endpointlist" method="POST" theme="simple">
            <div class="col-sm-4 selectContainer" style="background-color:lavenderblush;">
                <label for="myeplist">My Endpoints</label>
                <s:select id="myeplist"
                          cssClass="form-control"
                          headerKey="-1" headerValue="Select your endpoint"
                          list="myendpoints"
                          name="myendpointValue"/>
                    <s:hidden id="myendpointName" name="myendpointName" value=""/>
                    <p id="myendpoint_p"></p>
                <s:submit id="my-ep-select" name="my-ep-select" value="Add" cssClass="btn btn-primary"/>
            </div>
            </s:form>
        </div>
</div>

<h2>My Endpoints</h2>

<s:if test="bookmarklist.size() > 0">
    <div class="row">
        <div class="col-md-14">
            <table class="table">
                <th class="col-md-5 text-left">Endpoint</th>
                <th class="col-md-8 text-left">Path</th>
                <th class="col-md-1 text-center">List Files</th>
                <th class="col-md-1 text-center"></th>

                <s:iterator value="bookmarklist" var="data">
                    <tr>
                        <s:form id="endpoint-list-%{#data['id']}" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
                            <s:hidden name="bookmarkId" value="%{#data['id']}" />
                            <s:hidden name="endpointId" value="%{#data['endpoint_id']}" />
                            <s:hidden name="endpointName" value="%{#data['name']}" />

                            <td class="col-md-4 text-left">
                                <s:property value="#data['disp_name']" />
                            </td>

                            <td class="col-md-8 text-left">
                                <s:textfield cssClass="form-control" name="endpointPath" value="%{#data['path']}"/>
                            </td>
                            <td class="col-md-1 text-center">
                                <s:submit id="actionType" name="actionType" value="List" cssClass="btn btn-primary"/>
                            </td>
                            <td class="col-md-1 text-center">
                                <s:submit id="actionType" name="actionType" value="Delete" cssClass="btn btn-primary"/>
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
                    <td class="col-md-1 text-center">
                        <s:submit id="actionType" name="actionType" value="List" cssClass="btn btn-primary"/>
                    </td>
                </s:form>
            </tr>
        </table>
    </div>
</div>
</body>
</html>