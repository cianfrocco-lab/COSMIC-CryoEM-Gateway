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
  <s:if test="#userDataSize >= #maxDataSize">
  	<s:set name="removeSize" value="#userDataSize - #maxDataSize"/>
  	<s:set name="removeSizeDisplay" value="%{#action.getDataSize4Display(#removeSize)}"/>
  	<s:if test="#removeSize < 1">
  	  <s:set name="removeSize" value="1"/>
  	  <span class="dataSizeStop" title='Warning, your current total storage size (<s:property value="#userDataSize"/>) is at our maximum limit of <s:property value="#maxDataSizeDisplay"/>.  Please delete at least <s:property value="#removeSizeDisplay"/> as soon as you can.'>
  	</s:if>
  	<s:else>
  	  <span class="dataSizeStop" title='Warning, your current total storage size (<s:property value="#userDataSize"/>) exceeds our maximum limit of <s:property value="#maxDataSizeDisplay"/>.  Please delete at least <s:property value="#removeSizeDisplay"/> as soon as you can.'>
 	</s:else>
  </s:if>
  <s:else>
    <span title='Your current total storage size = <s:property value="#userDataSize"/> bytes.'>
  </s:else>

  <s:property value="#userDataSizeDisplay"/>
  </span>
  
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
</script>
