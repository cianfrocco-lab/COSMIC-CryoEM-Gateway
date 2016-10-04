<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ page isELIgnored='false'%>
<%-- Note that if more then one column is needed, we should change the
     css toollist's width
--%>
<head>
	<title>List of Available Tools</title>
	<!-- meta name="heading" content="Login"/ -->
  <content tag="menu">Toolkit</content>
</head>
<h2>Cryo-EM Image Processing Tools</h2>
<s:if test="%{trackerUrl != null && trackerUrl != ''}">
  <div class="callout">
    If there is a  tool or a feature you need, please <s:a href="javascript:popupWithSizes('%{trackerUrl}',800,600,'1')">let us know</s:a>.
  </div>
</s:if>
<ul class="toollist">
  <s:set name="action" value="top"/>
  <s:iterator value="%{splitFirstColumn(getToolsOfType(#type))}" id="tool">
    <li>
      <s:url id="selectToolUrl" action="createTask" method="selectTool" includeParams="none">
        <s:param name="selectedTool" value="%{#tool}"/>
      </s:url>
      <strong>
        <s:a href="%{selectToolUrl}">
          <s:property value="%{#action.getToolLabel(#tool)}"/>
        </s:a>
      </strong>
      <s:if test="%{#action.hasToolVersion(#tool)}">
        (<s:property value="%{#action.getToolVersion(#tool)}"/>)
      </s:if>
      <span class="simpleLink">
        <s:a href="javascript:popitup('%{staticSite}/tools/%{#tool.toLowerCase()}.html')">
          <img src="<s:url value="/images/info.png"/>" border="0"/>
        </s:a>
      </span>
      - <s:property value="%{#action.getToolDescription(#tool)}"/>
    </li>
  </s:iterator>
  <s:set name="action" value="top"/>
  <s:iterator value="%{splitSecondColumn(getToolsOfType(#type))}" id="tool">
    <li>
      <s:url id="selectToolUrl" action="createTask" method="selectTool" includeParams="none">
        <s:param name="selectedTool" value="%{#tool}"/>
      </s:url>
      <strong>
        <s:a href="%{selectToolUrl}">
          <s:property value="%{#action.getToolLabel(#tool)}"/>
        </s:a>
      </strong>
      <s:if test="%{#action.hasToolVersion(#tool)}">
        (<s:property value="%{#action.getToolVersion(#tool)}"/>)
      </s:if>
      <span class="simpleLink">
        <s:a href="javascript:popitup('%{staticSite}/tools/%{#tool.toLowerCase()}.html')">
          <img src="<s:url value="/images/info.png"/>" border="0"/>
        </s:a>
      </span>
      - <s:property value="%{#action.getToolDescription(#tool)}"/>
    </li>
  </s:iterator>
</ul>
