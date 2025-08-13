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
        <s:if test="%{isAuthenticated()}">
           <!-- Only show this tab IF AND ONLY IF the user has 'Admin' role. -->
           <s:if test="%{isAdministrator()}">
              <li>
                <a href="<s:url action='administration.action'/>">
                  <span>Administration</span>
                 </a>
              </li>
           </s:if>
        </s:if>
        <li class="${current_menu == 'Toolkit' ? 'active': ''}">
          <a href="<s:url action='tools.action'/>"><span>Toolkit</span></a>
        </li>
        <s:if test="%{isAuthenticated()}">
          <!-- Start: Globus Auth & Transfer Service -->
          <li class="${current_menu == 'Transfer Status' ? 'active' : ''}">
            <a href="<s:url action='status.action'/>"><span>Transfer Status</span></a>
          </li>
          <%--
          <li class="${current_menu == 'Endpoints' ? 'active' : ''}">
            <a href="<s:url action='endpointlist.action'/>"><span>Data Endpoints</span></a>
          </li>
          --%>
          <!-- End: Globus Auth & Transfer Service -->
          <li class="${current_menu == 'My Profile' ? 'active' : ''}">
            <a href="<s:url action='profile.action'/>"><span>My Profile</span></a>
          </li>
        </s:if>
        <li>
          <a href="javascript:window.open('${staticSite}/docs/', 'cosmic2_help')"><span>Help</span></a>
        </li>
        <li>
          <a href="javascript:window.open('https://cosmic-cryoem.org/known-issues/', 'issues')"><span>Known Issues</span></a>
        </li>
		<li>
          <a href="javascript:popitup('${staticSite}/portal/cite_us')"><span>How to Cite Us</span></a>
        </li>
      </ul>

      <!-- Start: Globus Auth & Transfer Service -->
      <ul class="nav navbar-nav navbar-right">
        <s:if test="%{isAuthenticated()}">
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
        </s:if>
      </ul>
      <!-- End: Globus Auth & Transfer Service -->
    </div><!--/.nav-collapse -->
  </div>
</div>
