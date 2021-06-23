<%@ taglib prefix="s" uri="/struts-tags" %>

<div id="sidebar" class="sidebar shadow-box">
  <s:url id="homeUrl" action="folder" method="list" includeParams="none"/>
  <s:a href="%{homeUrl}" cssClass="mc-replace"><h3>Folders</h3></s:a>
  <s:set name="action" value="top"/>
  
  <!-- Display user data size with size-dependent color-coding. -->
  <s:set name="maxDataSize" value="%{#action.getMaxDataSize('b')}"/>
  <s:set name="maxDataSizeDisplay" value="%{#action.getDataSize4Display(#maxDataSize)}"/>
  <s:set name="userDataSize" value="%{#action.getUserDataSize('b')}"/>
  <s:set name="userDataSizeDisplay" value="%{#action.getDataSize4Display(#userDataSize)}"/>
  <b>Total data upload storage:</b>
  <div id="currenttotalsize"><s:property value="#userDataSizeDisplay"/></div>
  <s:if test="#userDataSize >= #maxDataSize">
  	<s:set name="removeSize" value="#userDataSize - #maxDataSize"/>
  	<s:set name="removeSizeDisplay" value="%{#action.getDataSize4Display(#removeSize)}"/>
  	<s:if test="#removeSize < 1">
  	  <s:set name="removeSize" value="1"/>
  	  <span id="dataSizeStop" class="dataSizeStop">Warning, your current total storage size (<s:property value="#userDataSize"/>) is at our maximum limit of <s:property value="#maxDataSizeDisplay"/>.  Please delete at least <s:property value="#removeSizeDisplay"/> as soon as you can.</span>
  	</s:if>
  	<s:else>
  	  <span id="dataSizeStop" class="dataSizeStop">Warning, your current total storage size (<s:property value="#userDataSizeDisplay"/>) exceeds our maximum limit of <s:property value="#maxDataSizeDisplay"/>.  Please delete at least <s:property value="#removeSizeDisplay"/> as soon as you can.</span>
 	</s:else>
  </s:if>
  <s:else>
    <span id="dataSizeStop" class="dataSizeStop" title='Placeholder?'></span>
  </s:else>

  
  <br><br>
    
  <div id="folder-menu">
    <ul>
      <s:iterator value="folders" id="folder">
        <s:if test="%{#action.isExpanded(#folder)}">
          <li id="folder-<s:property value='#folder.folderId'/>" class="expanded">
        </s:if>
        <s:else>
          <li id="folder-<s:property value='#folder.folderId'/>">
        </s:else>
          <s:url id="displayUrl" action="folder" method="display" includeParams="none">
            <s:param name="id" value="%{#folder.folderId}"/>
          </s:url>
          <s:a cssClass="mc-replace folder-a" href="%{displayUrl}"><s:property value="#folder.label"/></s:a>
          <ul>
            <li>
              <s:url id="listDataUrl" action="data" method="list" includeParams="none">
                <s:param name="id" value="%{#folder.folderId}"/>
              </s:url>
              <s:a cssClass="mc-replace" href="%{listDataUrl}">Data (<s:property value="%{#action.getDataItemCount(#folder)}"/>)</s:a>
            </li>
            <li>
              <s:url id="listTasksUrl" action="task" method="list" includeParams="none">
                <s:param name="id" value="%{#folder.folderId}"/>
              </s:url>
              <s:a cssClass="mc-replace" href="%{listTasksUrl}">Tasks (<s:property value="%{#action.getTaskCount(#folder)}"/>)</s:a>
            </li>
            <s:iterator value="%{#action.getSubfolders(#folder)}" id="subfolder">
              <s:if test="%{#action.isExpanded(#subfolder)}">
                <li id="folder-<s:property value='#subfolder.folderId'/>" class="expanded">
              </s:if>
              <s:else>
                <li id="folder-<s:property value='#subfolder.folderId'/>">
              </s:else>
                <s:url id="displayUrl" action="folder" method="display" includeParams="none">
                  <s:param name="id" value="%{#subfolder.folderId}"/>
                </s:url>
                <s:a cssClass="mc-replace folder-a" href="%{displayUrl}"><s:property value="#subfolder.label"/></s:a>
                <ul>
                  <li>
                    <s:url id="listDataUrl" action="data" method="list" includeParams="none">
                      <s:param name="id" value="%{#subfolder.folderId}"/>
                    </s:url>
                    <s:a cssClass="mc-replace" href="%{listDataUrl}">
                      Data (<s:property value="%{#action.getDataItemCount(#subfolder)}"/>)
                    </s:a>
                  </li>
                  <li>
                    <s:url id="listTasksUrl" action="task" method="list" includeParams="none">
                      <s:param name="id" value="%{#subfolder.folderId}"/>
                    </s:url>
                    <s:a cssClass="mc-replace" href="%{listTasksUrl}">
                      Tasks (<s:property value="%{#action.getTaskCount(#subfolder)}"/>)
                    </s:a>
                  </li>
                </ul>
              </li>
            </s:iterator>
          </ul>
        </li>
      </s:iterator>
    </ul>
  </div>
</div>
<%--
  https://www.w3schools.com/jaquery/jquery_get_started.asp
<script src = "https://code.jquery.com/jquery-1.10.2.js"></script>
//https://stackoverflow.com/questions/20370569/auto-refresh-a-page-div-in-struts2
 --%>
<script>
var b;
b = setInterval(ajaxCall,15000);
function sizedisplay(bytes) {
  var KB = 1024;
  var MB = 1048576;
  var GB = 1073741824;
  var quot;
  var sizestring;
  quot = bytes/GB;
  if (quot < 1){
    quot = bytes/MB;
    if (quot < 1){
      quot = bytes/KB;
      if (quot < 1){
        sizestring = (bytes) + " bytes";
      } else {
        sizestring = (Math.floor(quot)) + " KB";
      }
    } else {
      sizestring = (Math.floor(quot)) + " MB";
    }
  } else {
      sizestring = (Math.floor(quot)) + " GB";
  }
  return sizestring;
}
function ajaxCall() {
  testobject = { url:'<s:url action="callSizeAction"/>',
                 type: 'POST',
                 dataType: 'text',
    success:function(inputvar,successcode,jqxhr){
      var newuds;
      newuds = parseInt(inputvar);
      <s:set name="maxDataSize" value="%{#action.getMaxDataSize('b')}"/>
      var mds;
      mds = parseInt("<s:property value="#maxDataSize"/>");
      <s:set name="maxDataSizeDisplay" value="%{#action.getDataSize4Display(#maxDataSize)}"/>
  if (newuds >= mds) {
        var remsize;
        remsize = newuds - mds;
        if (remsize < 1){
          remsize = 1;
          $('#dataSizeStop').text("Warning, your current total storage size " + sizedisplay(newuds) + " is at our maximum limit of " + sizedisplay(mds) + ".  Please delete at least " + sizedisplay(remsize) + " as soon as you can.");
        } else {
          $('#dataSizeStop').text("Warning, your current total storage size " + sizedisplay(newuds) + " exceeds our maximum limit of " + sizedisplay(mds) + ".  Please delete at least " + sizedisplay(remsize) + " as soon as you can.");
        }
  } else {
        //alert("newuds < mds");
    $('#dataSizeStop').text("");
  }
      $('#currenttotalsize').text(sizedisplay(newuds));
      //https://stackoverflow.com/questions/42936549...
    },
    error: function(xhr, status, thrownError){
      console.log("An error occurred!");
      console.log("with xhr: " + xhr);
      console.log("with status: " + status);
      console.log("with thrownError: " + thrownError);
    }
  };
  // https://api.jquery.com/jQuery.ajax/#jqXHR
  $.ajax(testobject);
}
</script>

<script type="text/javascript">
var expanded = [];
$('.expanded').each(function(){
  expanded.push($(this).attr('id'));
});
$('#folder-menu').jstree({
  "plugins" : ["themes","html_data","ui","crrm"]
  , 'themes': {
    "theme" : "apple"
    , "icons" : true
    , "dots" : false
  }
  , "core" : { "initially_open" : expanded }
});
$(document).on('click', '#folder-menu a.folder-a', function(e) {
  var eId = "#" + $(this).parent().attr('id');
  $.jstree._reference(eId).open_node(eId);
});
<%--
https://stackoverflow.com/questions/20370569/auto-refresh-a-page-div-in-struts2
 --%>
</script>
