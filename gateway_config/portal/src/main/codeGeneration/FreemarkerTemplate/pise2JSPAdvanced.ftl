<#-- ======================== Start of Main() Code Generation ============= -->
<#compress>		<#-- remove superfluous white-space -->

<#recurse doc>	<#-- Strart recursively processing the document -->

<#macro pise>    <#-- A macro name matched with the label of the node in the doc-->
                 <#-- when this node is found execute the following commands-->

<#assign Toolname=.node.command>  <#-- Declaration of a varialbe Toolname-->

<#assign command = .node.command?default("")>

	<#recurse .node.parameters>     
	  <#-- Permit to recursively process the nodes, FreeMaker will look for a 
	       macro that matches the label of the node in the doc. In this case
	       it will find 'parameters' -->	

              
</#macro>
</#compress>
<#-- ======================== End of Main() Code Generation ============= -->


<#-- ======================== Macros ============= -->

<#macro parameters><#recurse></#macro>

<#macro parameter>

	<#-- set defaults to the list of default values in <vdef> node -->
	<#assign defaults =.node.attributes.vdef.value>
	       
<#-- skip parameter when "ishidden" is "1" -->
	<#if .node.@ishidden[0]?exists && .node.@ishidden[0]?contains("1")><#return></#if>
	<#if .node.@issimple[0]?exists && .node.@issimple[0]?contains("1")><#return></#if>
	
<#-- skip parameter when "isinput" is "1" -->
	<#if .node.@isinput[0]?exists && .node.@isinput[0]?contains("1")><#return></#if>	
	
<#--  change "-" to "_" since Java does not recognize '-' as valid name 
	  change " " to "_" since space is not allowed in a Java varname 
	  add "_" to name to avoid using common methods names as parent class -->
	<#assign name = .node.name[0]?default("")>
	<#assign name = name?replace("[ |-]","_","r")+"_">
	
	<#assign prompt = .node.attributes.prompt[0]?default("")>

	<#-- process prompt: make a href link if there is comment  -->
	<#-- (simply replace (-xxx) with <a href=>xxx)) 		    -->
	<#-- replace the entire string if (-xxx) is not found       -->
	<#if (.node.attributes.comment?size >= 1 )>
		<#if prompt?contains("(-"+.node.name+")")>
			<#assign prompt = prompt?replace("(-"+.node.name+")", 
			"(-<A HREF=\"javascript:help.slidedownandjump('#${.node.name}')\">${.node.name}</A>)")>
		<#else>
			<#assign prompt = "<A HREF=\"javascript:help.slidedownandjump('#${.node.name}')\">${prompt}</A>">
		</#if>
	</#if>

	<#-- process parameter node according to its attribute "type" -->
	<#switch .node.@type>
	
		<#case "Paragraph">
		   <hr/>
	       
			<#assign p= .node.paragraph>
			<A name=${p.name}><h2><A HREF="javascript:help.slidedownandjump('#${p.name}_comment')">${p.prompt}</A></H2>
			<#recurse .node.paragraph.parameters> 
			<#--  recursive process child parameters -->
			
			
		<#break>
		
		<#case "OutFile">
		<#case "Results">
		<#break>
		
		<#case "Label">
			<br> </br>
			${name}
			<br></br>
		<#break>
		
		
		<#case "Float">
		<#case "Integer">
		<#case "String">
			${prompt}
			  <#if .node.@ismandatory[0]?exists && !.node.attributes.precond?is_node>
			  	<font color="red" size="3">*</font>
			  </#if>
			  			
			  <#if .node.@ismandatory[0]?exists && .node.attributes.precond?is_node>
			  	<font color="red" size="3">+</font>
			  </#if>
			  
			<s:textfield  name="${name}" size="10" maxlength="600" onchange="resolveParameters()"/>
		
			<#--    VALUE="${.node.attributes.vdef.value[0]?default("")}"/> 
			       is not needed since the default value are going to be assigned
			       through the action class -->
			       

			  	<br/>
		<#break>
		
		<#case "Sequence"> 
		<#case "InFile">
			${prompt}
			  <#if .node.@ismandatory[0]?exists && !.node.attributes.precond?is_node>
			  	<font color="red" size="3">*</font>
			  </#if>
			  			
			  <#if .node.@ismandatory[0]?exists && .node.attributes.precond?is_node>
			  	<font color="red" size="3">+</font>
			  </#if>
			
           <select name="${name}" id="${Toolname}_${name}" onchange="resolveParameters()">
	         <#if ! .node.@ismandatory[0]?exists>
               <option value=""></option>
		     </#if>
             <s:iterator value="%{getValueForParameterAsList('${name}')}" var="arr1" status="stat1">
               <s:iterator value="%{getLabelForParameterAsList('${name}')}" var="arr2" status="stat2">
                 <s:if test="#stat1.index == #stat2.index">
                   <option value="<s:property value='#arr1'/>"><s:property value='#arr2'/></option>
                 </s:if>
                </s:iterator>
             </s:iterator>
           </select>

			  	<br/>
		<#break>
		
		<#case "Switch">
			${prompt}
			
			  <#if .node.@ismandatory[0]?exists && !.node.attributes.precond?is_node>
			  	<font color="red" size="3">*</font>
			  </#if>
			  			
			  <#if .node.@ismandatory[0]?exists && .node.attributes.precond?is_node>
			  	<font color="red" size="3">+</font>
			  </#if>
			  			
			<#--
			<input type="checkbox" name="${name}" id="${Toolname}_${name}" onclick="resolveParameters()"/>
			-->
			<s:checkbox name="${name}" onclick="resolveParameters()"/>
			
			 <#--   <#if defaults?seq_contains("1")>selected="TRUE"</#if>/>	
			 		is not needed since the default value are going to be assigned
			       	through the action class -->

			  	<br/>			       	
		<#break>

	    <#case "List">	
			${prompt}
			  <#if .node.@ismandatory[0]?exists && !.node.attributes.precond?is_node>
			  	<font color="red" size="3">*</font>
			  </#if>
			  			
			  <#if .node.@ismandatory[0]?exists && .node.attributes.precond?is_node>
			  	<font color="red" size="3">+</font>
			  </#if>			
			
   			<#visit .node.attributes.vlist>	
   			

			  	<br/>   						    
		<#break>
		
		<#case "Excl">
			${prompt}
			  <#if .node.@ismandatory[0]?exists && !.node.attributes.precond?is_node>
			  	<font color="red" size="3">*</font>
			  </#if>
			  			
			  <#if .node.@ismandatory[0]?exists && .node.attributes.precond?is_node>
			  	<font color="red" size="3">+</font>
			  </#if>
			  			
			<#-- determine number of values in options -->
		    <#assign size=.node.attributes.vlist.value?size> 
			<#if size gt 0> <#-- make it a drop down list -->
				<#if .node.attributes.vlist?is_node>
					<#visit .node.attributes.vlist>
			 	</#if>		
			<#else> <#-- make it a radio button if fewer than 3 in vlist -->
					<#-- add [default] radio button if ismandatory is not present -->
					<#-- [default] has value="vdef" if defined, other value="" -->
					<#-- set value to vdef.value if there is one, otherwise, set it to "" -->
	
				<s:radio name="${name}" 
				<@compress single_line=true>
			        list="${r"#{"}
			     <#if ! .node.@ismandatory[0]?exists>
					'': '[Not Mandatory]',
				</#if>
			    <#assign size = .node.attributes.vlist.value?size>
				<#list .node.attributes.vlist.value as value> '${value}':'${.node.attributes.vlist.label[value_index]}'
					  <#if .node.attributes.vlist.value?seq_index_of(value) lt (size-1)>,</#if>
				</#list>
				}" onclick="resolveParameters()"/> 
				</@compress>		
			</#if>
			 

			  	<br/>  
		<#break> 


		<#default>
		<#break>

	</#switch>
	    
</#macro> 

<#-- make a drop down list from vlist;if type is "List", make it multiple -->
<#-- set default value(s) to <vdef></vdef>                                -->
<#macro vlist>	
	
	<#assign name = .node?parent?parent.name>
	<#assign name = name?replace("[ |-]","_","r")+"_">
    
    <@compress single_line=true>
    <select name="${name}"

        <#if .node?parent?parent.@type = "List">
          multiple="true"
          <#else>
            <#if (! .node?parent?parent.@ismandatory[0]?exists) > headerKey='' headerValue='' </#if>
        </#if>

        onchange="resolveParameters()">
	    <#if ! .node?parent?parent.@ismandatory[0]?exists>
          <option value=""></option>
		</#if>
        <#assign size = .node.value?size>
            <#list .node.value as value>
              <#if .node?parent?parent.attributes.vdef.value[0]?exists>
                <#if .node?parent?parent.attributes.vdef.value[0] == .node.value[value_index]>
                   <option value="${.node.value[value_index]}" selected >${.node.label[value_index]}</option>
                <#else>
                   <option value="${.node.value[value_index]}">${.node.label[value_index]}</option>
                </#if>
              <#else>
                   <option value="${.node.value[value_index]}">${.node.label[value_index]}</option>
              </#if>
            </#list> </select>
    </@compress>
		
		
		
	<br/>	
</#macro>
	    
