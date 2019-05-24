<#-- ======================== Start of Main() Code Generation ============= -->
	<#-- remove superfluous white-space -->
<#compress>
<#recurse doc>	<#-- Strart recursively processing the document -->

<#macro pise>    <#-- A macro name matched with the label of the node in the doc-->
                 <#-- when this node is found execute the following commands-->

<#assign Toolname=.node.command>  <#-- Declaration of a varialbe Toolname-->

 <#-- set value to "" if node does not exist -->
<#assign title = .node.head.title[0]?default("")>
<#assign description = .node.head.description[0]?default("")>
<#assign authors = .node.head.authors[0]?default("")>
<#assign reference = .node.head.reference[0]?default("")>
<#assign command = .node.command?default("")>

<%@ taglib prefix="s" uri="/struts-tags" %>
	
	<title>${title}</title>
	<h2>${title}: ${description} <#if authors?length gt 0>(<a href="${reference}">${authors}</a>)</#if></h2>
		
<s:form action="${Toolname}" theme="simple">

<!-- Begin Simple Parameters -->
<a href="javascript:simple.slideit()" class="panel">Simple Parameters</a>
<div id="simple" style="width: 100%; background-color: #FFF;">
<div style="padding: 0.75em 0 0 0">

<#include "pise2JSPSimple.ftl">

</div>
</div>
<script type="text/javascript">
var simple=new animatedcollapse("simple", 800, false, "block")
</script>
<!--End Simple Parameters -->

   <br/><hr/><br/>

<!--Begin Advanced Parameters -->
<a href="javascript:advanced.slideit()" class="panel">Advanced Parameters</a>
<div id="advanced" style="width: 100%; background-color: #FFF;">
<div style="padding: 0.75em 0 0 0">

	<#include "pise2JSPAdvanced.ftl">    
	  <#-- Permit to recursively process the nodes, FreeMaker will look for a 
	       macro that matches the label of the node in the doc. In this case
	       it will find 'parameters' -->	
	
</div>
</div>
<script type="text/javascript">
var advanced=new animatedcollapse("advanced", 800, true)
</script>
<!--End Advanced Parameters -->	
	
   <br/><hr/><br/>
    <s:submit value="Save Parameters" onclick="return validateControl()"/>   
    <s:submit value="Reset" method="resetPage"/>
    <s:submit value="Cancel" method="cancel"/>
    <#-- command Section -->
 
	<hr></hr>
<!--Begin Advanced Help -->
<a href="javascript:help.slideit()" class="panel">Advanced Help</a>
<div id="help" style="width: 100%; background-color: #FFF;">
<div style="padding: 0.75em 0 0 0">

	<#include "pise2help.ftl">	<#-- help section -->

</div>
</div>
<script type="text/javascript">
var help=new animatedcollapse("help", 800, true)
</script>
<!--End Advanced Help -->

</s:form>

<#-- Additng the Java Script -->
<script type="text/javascript">

function resolveParameters() {
	<#include "pise2JavaScript.ftl">	<#-- a  section of parameters dependency-->
}

function validateControl() {
	<#include "pise2JavaScriptControl.ftl">	<#-- a  section of parameters dependency-->
	return issueWarning();
}

function issueWarning() {
	<#include "pise2JavaScriptWarning.ftl">	<#-- a  section of parameters dependency-->
	addSpecialField();
	return true;
}

function messageSplit(str)
{
	var tokens = str.split(" ");
	var newStr = "" 
	var tmp;
	for (i = 0; i < tokens.length; i++)        
	{               
		if ((tokens[i].indexOf("getValue(") == 0))
		{                   
			tmp = tokens[i];
			var tmp1, tmp2;
			var closeParen = tmp.indexOf(")");
			tmp1 = tmp.substring(0, closeParen + 1);
			if ((closeParen + 1) == tmp.length)
			{                    
				tmp = tmp1 +  " + ' '";
			} else
			{
				tmp2=tmp.substring(closeParen + 1);
				tmp = tmp1 + " + '" + tmp2 + "'";
				tmp = tmp +  " + ' '";
			}
		} else      
		{
			tmp = "'" + tokens[i] + " '";
		}                       
		if (newStr.length > 0)            
		{                   
			newStr = newStr + " + " + tmp;
		} else              
		{   
			newStr = tmp;
		}           
	}               
	return eval(newStr);
}










// For multiple selection list, pise "List" type, this only returns the 1st value selected.
// To get all values you need to get all element[i].value where element[i].selected = true.
function getValue(parameter) 
{
	var element = document.forms['${Toolname}'].elements[parameter];
	if (element == null)
	{
		return null;
	}
	if (isDisabled(parameter))
	{
		if (element.type == 'checkbox')
		{
			return false;
		}
		return "";
	}
	else if (element.length != null) 
	{
		// if the element has a value, it's a drop-down list
		if (element.value != null)
		{
			return element.value;
		} else 
		{
			for (i=0; element.length>i; i++) 
			{
				if (element[i].checked)
				{
					return element[i].value;
				}
			}
			return null;
		}
	} else if (element.type == 'checkbox')
	{
		return element.checked;
	} else 
	{
		return element.value;
	}
}
		
function enable(parameter) 
{
	var element = document.forms['${Toolname}'].elements[parameter];
	if (element == null)
	{
		return;
	}
	if (element.length != null) 
	{
		for (i=0; element.length>i; i++) 
		{
			element[i].disabled = false;
		}
	} 
	element.disabled = false;
}

function disable(parameter) 
{
	var element = document.forms['${Toolname}'].elements[parameter];
	if (element == null)
	{
		return;
	}
	if (element.length != null) 
	{
		for (i=0; element.length>i; i++) 
		{
			element[i].disabled = true;
		}
	}
	element.disabled = true;
}  

function isDisabled(parameter)
{
	var element = document.forms['${Toolname}'].elements[parameter];
	if (element == null)
	{
		return true;
	}
	// radio button is special case
	if (element.length != null )
	{
		return element[0].disabled;
	}
	return element.disabled;
}

function allDisabledFields() 
{
	var str = '';
	var element = document.forms['${Toolname}'].elements;
	for(var i = 0; i < element.length; i++)
	{
		if (isDisabled(element[i].name))
		{
			str += element[i].name + ",";
		}
	}
	return str;
}

function addSpecialField()
{
	var str = allDisabledFields();
	var input = document.createElement('input');
	input.type = 'hidden';
	input.name = 'disabledFields__';
	input.value = str;
	document.forms['${Toolname}'].appendChild(input);
}



</script>	
              
</#macro>
</#compress>
<#-- ======================== End of Main() Code Generation ============= -->


<#-- ======================== Macros ============= -->
<#macro @element></#macro>   
<#-- this macro is needed because we call for <#include...> in the Main() -->
