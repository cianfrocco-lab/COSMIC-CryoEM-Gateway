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

    <script type="text/javascript">
        $(document).ready(function() {

            $("#transfer-file-list").submit(function() {
                if(!$(':checkbox:checked').length) {
                    alert("Please select at least one file.");
                    //stop the form from submitting
                    return false;
                }
                return true;
            });

        <%--
        // Set a variable, we will fill later.
        var value = null;

        // On submit click, set the value
        $('input[type="submit"]').click(function(){
            value = $(this).val();
        });

        // On data-type submit click, set the value
        $('input[type="submit"][data-type]').click(function(){
            value = $(this).data('type');
        });

        // Use the set value in the submit function
        $('form').submit(function (event){
            //event.preventDefault();
            alert("vale: "+value);
            // do whatever you need to with the content
        });
        --%>
        });

        function validateForm(obj,event){
            var submitButton;

            if(typeof event.explicitOriginalTarget != 'undefined'){  //
                submitButton = event.explicitOriginalTarget;
            }else if(typeof document.activeElement.value != 'undefined'){  // IE
                submitButton = document.activeElement;
            };

            var sub_name = submitButton.name;
            var sub_value = submitButton.value;

            if (sub_name == "actionType" && sub_value == "List") {
                var ep = obj['endpointPath'].value;//$("#endpointPath").val();
                if (ep.startsWith("/")) {
                    return true;
                }
                alert("Please enter a valid path, starting with '/': "+ep);
                return false;

                //alert(ep + " : " + sub_name + ' = ' + sub_value)
                //return false;
            }

            //var ep = obj['endpointPath'].value;
            //alert(ep + " : " + sub_name + ' = ' + sub_value)
            //return false;
        }

    </script>
    <!-- Javascript -->
    <script type="text/javascript">
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
                    <!-- $( "#search_p" ).html( ui.item.value ); -->
                    return false;
                }
            });

            $("#myeplist").on('change', function() {
                var s_text = $("#myeplist option:selected").text();

                $( "#myendpointName" ).val( s_text );
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

            <%--
            $('[id^=endpoint-list-]').submit(function (e) {
                var ss = $(this);
                var en = ss["endpointName"].value;
                //var clicked = $("#actionType").
                alert("action type: "+$("#actionType").val()+en+e);
                return false;
            });
            --%>
        });

    </script>

</head>
<body>

<div class="container">
    <div class="row">
        <s:form id="endpoint-search-form" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
            <div class="col-sm-4" style="background-color:lavender;">
                <label for="search">Endpoint Search:</label>
                <s:textfield cssClass="form-control" id="search" name="searchLabel"/>
                <s:hidden id="searchValue" name="searchValue" value=""/><p></p>
                <s:submit id="ep-search" name="ep-search" value="Add" cssClass="btn btn-primary"/>
            </div>
        </s:form>
        <s:form id="my-endpoint-search-form" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
            <div class="col-sm-4 selectContainer" style="background-color:lavenderblush;">
                <label for="myeplist">My Endpoints</label>
                <s:select id="myeplist"
                          cssClass="form-control"
                          headerKey="-1" headerValue="Select your endpoint"
                          list="myendpoints"
                          name="myendpointValue"/>
                <s:hidden id="myendpointName" name="myendpointName" value=""/><p></p>
                <s:submit id="my-ep-select" name="my-ep-select" value="Add" cssClass="btn btn-primary"/>
            </div>
        </s:form>
    </div>
    <p></p>
</div>

<%-- <div class="container"> --%>
<h2>My Endpoints</h2>

<s:if test="bookmarklist.size() > 0">
    <div class="row">
        <div class="col-md-11">
            <table class="table">
                <th class="col-md-4 text-left">Endpoint</th>
                <th class="col-md-5 text-left">Path</th>
                <th class="col-md-1 text-center">List Files</th>
                <th class="col-md-1 text-center"></th>

                <s:iterator value="bookmarklist" var="data">
                    <tr>
                        <s:form id="endpoint-list-%{#data['id']}" cssClass="form-horizontal" action="transfer" method="GET" theme="simple" onsubmit="return validateForm(this,event);">
                            <s:hidden name="bookmarkId" value="%{#data['id']}" />
                            <s:hidden name="endpointId" value="%{#data['endpoint_id']}" />
                            <s:hidden id="endpointName" name="endpointName" value="%{#data['name']}" />

                            <td class="col-md-4 text-left">
                                <s:property value="#data['disp_name']" />
                            </td>

                            <td class="col-md-5 text-left">
                                <s:textfield cssClass="form-control" id="endpointPath" name="endpointPath" value="%{#data['path']}"/>
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

<h2>XSEDE Endpoint</h2>
<div class="row">
    <div class="col-md-7">
        <table class="table">
            <th class="col-md-5 text-left">Endpoint</th>
            <%-- <th class="col-md-10 text-left">Path</th> --%>
            <th class="col-md-1 text-center">List Files</th>
            <tr>
                <s:form id="server-endpoint" cssClass="form-horizontal" action="transfer" method="GET" theme="simple">
                    <s:hidden name="bookmarkId" value="XSERVER" />
                    <td class="col-md-6 text-left">
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

    <%-- <div class="page-header"> --%>
<div>
<h2>
    Globus Transfer &nbsp;&nbsp;&nbsp;
<s:if test="bookmarklist.size() > 0">
    <s:url var="transferUrl" action="transfer">
        <s:param name="transferLocation">true</s:param>
    </s:url>
    <s:a href="%{transferUrl}" cssClass="btn btn-primary">Switch Source and Destination</s:a>
</s:if>
</h2>
</div>

<s:if test="bookmarklist.size() > 0">
    <p><strong>Destination Endpoint:</strong> <s:property value="%{#session.dest_disp_name}"/></p>
    <p><strong>Source Endpoint:</strong> <s:property value="%{#session.src_disp_name}"/></p>
    <s:if test="files != null && files.size() > 0">
    <%-- <div class="form-group"> --%>
        <s:form id="transfer-file-list" cssClass="form-inline" action="transfer" method="POST" theme="simple">
            <s:hidden name="endpointId" value="%{#session.src_endpoint_id}"/>
            <s:hidden name="endpointPath" value="%{#session.src_endpoint_path}"/>
            <s:hidden name="endpointName" value="%{#session.src_endpoint_name}"/>
            <div class="row">
                <div class="col-md-10">
                    <table class="table">
                        <th class="col-md-3 text-left">File / Folder</th>
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
</s:if>
<s:else>
    <p>
        <strong>Please register your Globus Endpoints.</strong>
    </p>
</s:else>

</body>
</html>
