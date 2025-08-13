<!DOCTYPE html>
<%@ include file="/pages/common/taglibs.jsp" %>
<html lang="en">
<head>
  <%@ include file="/pages/common/meta.jsp" %>
  <title>Science Gateway | <decorator:title default="Welcome"/></title>
  <%-- Framework CSS --%>

  <%--
  <link rel="stylesheet" href="//netdna.bootstrapcdn.com/bootstrap/3.1.0/css/bootstrap.min.css">
  --%>
  <link rel="stylesheet" href="<c:url value='/bootstrap-3.1.1-dist/css/bootstrap.min.css'/> ">

  <%-- boostrap-formhelpers for country code dropdown --%>
  <link rel="stylesheet" href="<c:url value='/bootstrap-form-helpers/dist/css/bootstrap-formhelpers.css'/>" />

  <%-- Custom CSS --%>
  <link rel="stylesheet" href="<c:url value='/css/gateway.css'/>" type="text/css" media="screen, projection"/>
  <!--[if IE 8]>
  <link rel="stylesheet" href="<c:url value='/css/cipres-ie8.css'/>" type="text/css" media="screen, projection"/>
  <![endif]-->
  <link rel="stylesheet" href="<c:url value='/css/messages.css'/>" type="text/css" media="screen, projection"/>
  <link rel="stylesheet" href="<c:url value='/js/themes/default/style.css'/>" type="text/css" media="screen, projection"/>
  <link rel="stylesheet" href="<c:url value='/js/themes/apple/style.css'/>" type="text/css" media="screen, projection"/>
  <link href="//netdna.bootstrapcdn.com/font-awesome/4.0.3/css/font-awesome.css" rel="stylesheet">
  <%-- javascript --%>
  <%-- added for the whats this? start --%>
  <script type="text/javascript" src="<c:url value='https://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/qtip/jquery.qtip.min.js'/>"></script>

  <%--
  <script src="//netdna.bootstrapcdn.com/bootstrap/3.1.0/js/bootstrap.min.js"></script>
  --%>
  <script type="text/javascript" src="<c:url value='/bootstrap-3.1.1-dist/js/bootstrap.min.js'/>"></script>

  <%-- boostrap-formhelpers for country code dropdown --%>
  <script src="https://code.jquery.com/jquery-latest.min.js"></script>
  <script src="<c:url value='/bootstrap-form-helpers/dist/js/bootstrap-formhelpers.min.js'/>"> </script>

  <script type="text/javascript" src="<c:url value='/js/site/core.js'/>"></script>
  <%-- added for the whats this? end --%>
  <script type="text/javascript" src="<c:url value='/js/ngbw.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/swami.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/checkboxgroup.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/switchcontent.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/animatedcollapse.js'/>"></script>
  <script type="text/javascript" src="<c:url value='/js/jquery.jstree.js'/>"></script>
    
  <%-- added for the whats this? one line --%>
  <script type="text/javascript" src="<c:url value='/js/qtip/qtip.custom.js'/>"></script>

  <%-- pick up any header info from *.jsp if any --%>
  <decorator:head/>
</head>

<body onload="<decorator:getProperty property='body.onload'/>" <decorator:getProperty property="body.id" writeEntireProperty="true"/><decorator:getProperty property="body.class" writeEntireProperty="true"/>>
  <div class="header">  
    <%-- BEGIN HEADER CONTAINER --%>
    <div class="container">
      <%-- BEGIN PAGE HEADER --%>
      <div id="page-head" class="row">
        <a class="ca"  href="home.action"></a><a class="cb" href="https://access-ci.org/"></a>
      </div>
      <%-- END PAGE HEADER --%>
    </div>

	  <%-- BEGIN MAIN NAVIGATION --%>
	  <%@ include file="/pages/common/menu.jsp" %>
	  <%-- END MAIN NAVIGATION --%>

  </div>
  <%-- END HEADER CONTAINER --%>

  <%-- BEGIN CONTAINER --%>
  <div class="container main">
    <div class="row">

      <s:if test="%{isAuthenticated()}">
        <s:include value="/pages/user/folder/folderList.jsp"/>
      </s:if>
    
      <%-- BEGIN MAIN CONTENT AREA (condition 1, logged in)--%>  
      <s:if test="%{isAuthenticated()}">
        <div class="col-xs-9 main-content shadow-box">
      </s:if>
      <s:else>
        <div class="col-xs-10 col-xs-offset-1 main-content shadow-box">
      </s:else>
        <%@ include file="/pages/common/messages.jsp" %>
        <decorator:usePage id="myPage" />
        <decorator:body/>
      </div>
    </div>
  </div>
  <%-- END CONTAINER --%>

  <%-- BEGIN FOOTER CONTENT --%>
  <%@ include file="/pages/common/footer.jsp" %>
  <%-- END FOOTER CONTENT --%>

</body>
</html>
