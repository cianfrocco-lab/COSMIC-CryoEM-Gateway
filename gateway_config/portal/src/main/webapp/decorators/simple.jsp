<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<%@ include file="/pages/common/taglibs.jsp" %>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<head>
	<%@ include file="/pages/common/meta.jsp" %>
	<title>Science Gateway | <decorator:title default="Welcome"/></title>
	<decorator:head/>
</head>
<body<decorator:getProperty property="body.id" writeEntireProperty="true"/><decorator:getProperty property="body.class" writeEntireProperty="true"/>>
<%@ include file="/pages/common/messages.jsp" %>
<decorator:body/>
</body>
</html>
