<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
  <title>
    Tail
  </title>
  <body>
<h2><s:property value="toolLabel"/> - Output</h2>
<div class="callout">
 Last few lines of <s:property value="contentDisposition"/>...
</div>
    <% response.setIntHeader("Refresh",5); %>
    <pre>
    <br><s:property value="inputLine"/>
    </pre>
  </body>
</html>
