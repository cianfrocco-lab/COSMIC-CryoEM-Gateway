<%@ include file="/pages/common/taglibs.jsp" %>
<%@ page isELIgnored='false'%>

<%-- determine the current menu tab to highlight --%>
<c:set var="current_menu" scope="request">
  <decorator:getProperty property="page.menu" default="My Workbench"/>
</c:set>

<div class="navbar navbar-inverse bs-docs-nav" role="navigation">
  <div class="container">
    <div class="navbar-header">
      <a class="navbar-brand" href="#">Science Gateway</a>
    </div>
    <div class="navbar-collapse collapse">
      <ul class="nav navbar-nav">
        <li class="${current_menu == 'Home' ? 'active' : ''}">
          <a href="<s:url action='home.action'/>"><span>Home</span></a>
        </li>
        <li class="${current_menu == 'Toolkit' ? 'active': ''}">
          <a href="<s:url action='tools.action'/>"><span>Toolkit</span></a>
        </li>
        <s:if test="%{isAuthenticated()}">
          <li class="${current_menu == 'My Profile' ? 'active' : ''}">
            <a href="<s:url action='profile.action'/>"><span>My Profile</span></a>
          </li>
        </s:if>
        <li>
          <a href="javascript:popitup('${staticSite}/help/')"><span>Help</span></a>
        </li>
        <li>
          <a href="javascript:popitup('${staticSite}/portal/cite_us')"><span>How to Cite Us</span></a>
        </li>
      </ul>
      <s:if test="%{isAuthenticated()}">
        <ul class="nav navbar-nav navbar-right">
          <li class="dropdown">
            <a href="#" class="dropdown-toggle" data-toggle="dropdown">XSEDE Status<b class="caret"></b></a>
            <ul class="dropdown-menu">
              <li>
                <a href=javascript:popitup("https://portal.xsede.org/scheduled-downtimes")>
                  <i class="fa fa-calendar"></i>
                  Calendar
                </a>
              </li>
              <li>
                <a href=javascript:popitup("https://portal.xsede.org/resource-monitor")>
                  <i class="fa fa-road"></i>
                  Traffic
                </a>
              </li>
            </ul>
          </li>
          <li>
            <a href="<s:url action='logout.action'/>"><span>Logout</span></a>
          </li>
        </ul>
      </s:if>
    </div><!--/.nav-collapse -->
  </div>
</div>
