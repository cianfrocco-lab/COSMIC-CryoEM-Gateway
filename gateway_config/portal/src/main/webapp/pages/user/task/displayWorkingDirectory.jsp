<%@ taglib prefix="s" uri="/struts-tags" %>

<h2><s:property value="toolLabel"/> - Intermediate Results</h2>
<div class="callout">
  This is a list of the files currently in the working directory of your job.  It may include input files, result files, and other files that are used internally
  by the portal.  Once your job completes the results will be stored in the database and the working directory and files will be removed.  
</div>

<table class="table table-striped" >
	<thead>
		<th>File</th>
		<th></th>
		<th>Bytes</th>
		<th>Last Modified</th>
	</thead>
	<s:iterator value="workingDirectoryList" id="file">
		<s:if test="%{#file.isDirectory==true}">
                        <tr>
                                <td>
                                     <s:property value="%{#file.filename}"/>
                                </td>
                                <td>
                                        <s:url id="durl" action="displaysubdir">
                                                <s:param name="inputPath" value="%{#file.filename}"/>
                                        </s:url>
                                        <s:a href="%{durl}">Enter Subdir</s:a>
                                </td>
                                <td><s:property value="%{#file.size}"/></td>
                                <td><s:date name="%{#file.mtime}"/></td>
                        </tr>
		</s:if>
		<s:else>
			<tr>
				<td>
					<s:url id="durl" action="download">
						<s:param name="inputPath" value="%{#file.filename}"/>
					</s:url>
          <s:a href="%{durl}">
						<s:property value="%{#file.filename}"/>
					</s:a>
				</td>
				<td>
					<s:url id="durl" action="displayremotetail">
						<s:param name="inputPath" value="%{#file.filename}"/>
					</s:url>
					<s:a href="%{durl}">Live Preview</s:a>
				</td>
				<td><s:property value="%{#file.size}"/></td>
				<td><s:date name="%{#file.mtime}"/></td>
			</tr>
		</s:else>
  </s:iterator>
</table>
