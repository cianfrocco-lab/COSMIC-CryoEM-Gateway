<%--
  Created by IntelliJ IDEA.
  User: cyoun
  Date: 11/16/16
  Time: 4:48 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>

<%--
<%@ taglib prefix="sj" uri="/struts-jquery-tags"%>
<%@ taglib prefix="sjt" uri="/struts-jquery-tree-tags"%>
--%>

<html>
<head>
    <title>Transfer</title>
    <content tag="menu">Transfer</content>
    <link href = "https://code.jquery.com/ui/1.10.4/themes/ui-lightness/jquery-ui.css"
          rel = "stylesheet">
    <script src = "https://code.jquery.com/jquery-1.10.2.js"></script>
    <!-- <script src = "https://code.jquery.com/ui/1.10.4/jquery-ui.js"></script> -->

    <style type="text/css">
        #bootstrapSelectForm .selectContainer .form-control-feedback {
            /* Adjust feedback icon position */
            right: -15px;
        }
        #user-file-tree-container .file {
            background: url('dist/themes/default/32px.png') no-repeat -102px -69px;
            }
        #user-file-tree-container .folder {
            background: url('dist/themes/default/32px.png') no-repeat -260px -4px;
            }
    </style>

    <link rel="stylesheet" href="dist/themes/default/style.min.css" />
    <!--<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script> -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.12.1/jquery-ui.min.js"></script>
    <script src="dist/jstree.min.js"></script>

    <script type="text/javascript">
        $(document).ready(function() {
            <%--
            $("#transfer-file-list").submit(function() {
                if(!$(':checkbox:checked').length) {
                    alert("Please select at least one file.");
                    //stop the form from submitting
                    return false;
                }
                return true;
            });
            --%>

            $("#transfer-file-list").submit(function() {
                var selectedElmsIds = [];
                var selectedElmsTexts = [];
                var selectedElmsTypes = [];
                var selectedElms = $('#user-file-tree-container').jstree("get_selected", true);
                $.each(selectedElms, function () {
                    selectedElmsIds.push(this.type);
                    selectedElmsIds.push(this.id);
                    selectedElmsTexts.push(this.text);
                    selectedElmsTypes.push(this.type);
                });
                //alert('Get Selected Ids: ' + selectedElmsIds.join(', '));
                //alert('Get Selected Texts: ' + selectedElmsTexts.join(', '));
                //alert('Get Selected Types: ' + selectedElmsTypes.join(', '));
                if (selectedElmsIds.length > 0) {
                    //setting to hidden field
                    //document.getElementById('selectedFiles').value = selectedElmsIds.join(",");
                    $('#selectedFiles').val(selectedElmsIds);
                    return true;
                }
                alert("Please select at least one file.");
                return false;
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

                var epid = obj['endpointId'].value;
                if (epid === 'de463f97-6d04-11e5-ba46-22000b92c6ec') {
                    var epname = obj['endpointName'].value;
                    alert("XSEDE Comet is managed by the COSMIC2 gateway and in order to protect all users' data, it cannot be used as your endpoint.");
                    return false;
                };

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

        function get_file_list() {
            var selectedElmsIds = [];
            var selectedElmsTexts = [];
            var selectedElmsTypes = [];
            var selectedElms = $('#user-file-tree-container').jstree("get_selected", true);
            $.each(selectedElms, function () {
                selectedElmsIds.push(this.id);
                selectedElmsTexts.push(this.text);
                selectedElmsTypes.push(this.type);
            });
            alert('Get Selected Ids: ' + selectedElmsIds.join(', '));
            alert('Get Selected Texts: ' + selectedElmsTexts.join(', '));
            alert('Get Selected Types: ' + selectedElmsTypes.join(', '));
            //setting to hidden field
            document.getElementById('selectedFiles').value = selectedElmsIds.join(",");
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
                } else {
                    var s_value = $( "#searchValue" ).val();
                    //XSEDE Comet endpoint is blocked to add
                    if ( s_value === 'de463f97-6d04-11e5-ba46-22000b92c6ec') {
                        alert("XSEDE Comet is managed by the COSMIC2 gateway and in order to protect all users' data, it cannot be added as your endpoint.");
                        return false;
                    };
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

    <script type="text/javascript">
        $(function () {

            $('#user-file-tree-container').jstree({
                'plugins':["checkbox","types"],
                'core': {
                    'data': {
                        'url': function (node) {
                            //alert('url node id:' + node.id);
                            //return node.id === '#' ?
                            //    'filetree.action' :
                            //    'filetree.action?nodeId='+node.id;
                            return 'filetree.action';
                        },
                        'data': function (node) {
                            //alert('data node id:' + node.id);
                            return {'id': node.id};
                        }
                    }
                },
                'types' : {
                    'folder' : { 'icon' : 'folder' },
                    'file' : { 'valid_children' : [], 'icon' : 'file' }
                }
            });
            <%--
                .on('changed.jstree', function (e, data) {
                var i, j, r = [];
                for (i = 0, j = data.selected.length; i < j; i++) {
                    r.push(data.instance.get_node(data.selected[i]).type.trim());
                }
                alert('Selected: ' + r.join(', '));
            });
            --%>

        });

    </script>

    <%--<sj:head jqueryui="true" jquerytheme="redmond"/>--%>
</head>
<body>

<%@ include file="/pages/common/messages.jsp" %>

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
    <s:if test="filecount > 0">
        <s:form id="transfer-file-list" cssClass="form-inline" action="transfer" method="POST" theme="simple">
            <s:hidden id="selectedFiles" name="selectedFiles" value=""/>
            <div id="user-file-tree-container" class="demo" style="margin-top:2em;"></div>
            <p></p>
            <s:if test="%{userCanTransfer()}">
            	<div class="form-group form-buttons">
	                <div class="col-md-10 pull-right">
	                    <s:submit name="transfer" value="Transfer" cssClass="btn btn-primary"/>
	                </div>
            	</div>
            </s:if>
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
