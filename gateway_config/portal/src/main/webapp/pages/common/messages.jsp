<%@ taglib prefix="s" uri="/struts-tags" %>

<s:if test="hasActionMessages()">
  <div class="alert alert-success alert-dismissable">
    <button type="button" class="close" data-dismiss="alert" aria-hidden="true">&times;</button>
    <ul class="message">
      <s:iterator value="actionMessages">
        <li><s:property escape="false"/></li>
      </s:iterator>
    </ul>
  </div>
</s:if>
<s:if test="%{hasActionErrors() or hasFieldErrors()}">
  <div class="alert alert-danger alert-dismissable">
    <button type="button" class="close" data-dismiss="alert" aria-hidden="true">&times;</button>
    <ul class="message">
      <s:iterator value="actionErrors">
        <li><s:property/></li>
      </s:iterator>
      <s:iterator value="fieldErrors">
        <s:iterator value="value">
          <li><s:property/></li>
        </s:iterator>
      </s:iterator>
    </ul>
  </div>
</s:if>
