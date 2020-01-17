<#-- ======================== Start of Main() Code Generation ============= -->
<#compress>		<#-- remove superfluous white-space -->

<#recurse doc>	<#-- Strart recursively processing the document -->

<#macro pise>    <#-- A macro name matched with the label of the node in the doc-->
                 <#-- when this node is found execute the following commands-->

<#assign Toolname=.node.command>  <#-- Declaration of a varialbe Toolname-->

<#global step_1=true>

package org.ngbw.web.model.impl.tool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ngbw.sdk.api.tool.ParameterValidator;
import org.ngbw.sdk.common.util.BaseValidator;
import org.ngbw.sdk.database.TaskInputSourceDocument;

public class ${Toolname}Validator implements ParameterValidator
{
	private Set<String> requiredParameters;
	private Set<String> requiredInput;
	private Set<String> multipleAllowed;
	
	public ${Toolname}Validator() {
		requiredParameters = new HashSet<String>();
		requiredInput = new HashSet<String>();
		multipleAllowed = new HashSet<String>();
		<#recurse .node.parameters>     
		<#-- Permit to recursively process the nodes, FreeMaker will look for a 
	       macro that matches the label of the node in the doc. In this case
	       it will find 'parameters' -->
	}
	
	public Map<String, String> validateParameters(Map<String, String> parameters) {
		Map<String, String> errors = new HashMap<String, String>();
		Set<String> missingRequired = validateRequiredParameters(parameters);
		for (String missing : missingRequired) {
			errors.put(missing, "You must enter a value for \"" + missing + "\"");
		}
		if (parameters != null){
			for (String param : parameters.keySet()) {
				String error = validate(param, parameters.get(param));
				if (error != null)
					errors.put(param, error);
			}
		}
		return errors;
	}
	
	public Map<String, String> validateInput(Map<String, List<TaskInputSourceDocument>> input) {
		Map<String, String> errors = new HashMap<String, String>();
		Set<String> missingRequired = validateRequiredInput(input);
		for (String missing : missingRequired) {
			errors.put(missing, "You must enter a value for \"" + missing + "\"");
		}
		for (String param : input.keySet()) {
			String error = validate(param, input.get(param));
			if (error != null)
				errors.put(param, error);
		}
		return errors;
	}
	
	<#global step_1=false>
	
	public String validate(String parameter, Object value) {
	  <#recurse .node.parameters>     
	  <#-- Permit to recursively process the nodes, FreeMaker will look for a 
	       macro that matches the label of the node in the doc. In this case
	       it will find 'parameters' -->	
		return null;
	}
	private Set<String> validateRequiredParameters(Map<String, String> parameters) {
		Set<String> required = new HashSet<String>(requiredParameters.size());
		required.addAll(requiredParameters);
		if (parameters != null){
			for (String parameter : parameters.keySet()) {
				if (required.contains(parameter))
					required.remove(parameter);
			}
		}
		return required;
	}
	
	private Set<String> validateRequiredInput(Map<String, List<TaskInputSourceDocument>> input) {
		Set<String> required = new HashSet<String>(requiredInput.size());
		required.addAll(requiredInput);
		for (String parameter : input.keySet()) {
			if (required.contains(parameter))
				required.remove(parameter);
		}
		return required;
	}
}
</#macro>
</#compress>
<#-- ======================== End of Main() Code Generation ============= -->


<#-- ======================== Macros ============= -->

<#macro parameters><#recurse></#macro>

<#macro parameter>

<#--  change "-" to "_" since Java does not recognize '-' as valid name 
	  change " " to "_" since space is not allowed in a Java varname 
	  add "_" to name to avoid using common methods names as parent class -->
	<#assign name = .node.name[0]?default("")>
	<#assign name = name?replace("[ |-]","_","r")+"_">

 
<#-- skip parameter when "ishidden" is "1" -->
	<#if .node.@ishidden[0]?exists && .node.@ishidden[0]?contains("1")><#return></#if>

<#-- Treat a parameter validation -->

   <#if .globals.step_1==true>
		<#switch .node.@type>
	
		<#case "Paragraph">
				<#-- Take care of the precond for a paragraph-->
		    <#recurse .node.paragraph.parameters> 
		<#break>
		
		<#case "InFile">
		<#case "Sequence">
		 <#if .node.@ismandatory[0]?exists && .node.@ismandatory[0]?contains("1") && !.node.attributes.precond?is_node>
		  <#if !.node.@isinput[0]?exists || !.node.@isinput[0]?contains("1")>
		   requiredInput.add("${name}");
		   
			</#if>
		 </#if>

		<#if .node.@multipleAllowed[0]?exists && .node.@multipleAllowed[0]?contains("1")>
			multipleAllowed.add("${name}");
		</#if>
		<#break>
		
		<#case "List">
		<#case "Excl">
    	<#case "String">
		<#case "Integer">
		<#case "Float">
		 <#if .node.@ismandatory[0]?exists && .node.@ismandatory[0]?contains("1") && !.node.attributes.precond?is_node>
		   requiredParameters.add("${name}");
		   
			</#if>
		<#break>
		
	</#switch>		
 </#if>	 
	 
<#-- Treat a parameter validation -->

<#if !.globals.step_1==true>   
		<#switch .node.@type>
	
		<#case "Paragraph">
				<#-- Take care of the precond for a paragraph-->
		    <#recurse .node.paragraph.parameters> 
		<#break>

		<#case "InFile">
		<#case "Sequence">

		if (parameter.equals("${name}")) {
			if (((List<?>)value).size() > 1 && !multipleAllowed.contains(parameter))
				return parameter + " has multiple values";
		}

		<#break>

		<#case "List">
		<#case "Excl">
    	<#case "String">	

		 <#if .node.@type == "String">
		 if (parameter.equals("${name}")) {   
		 	if (String.valueOf(value).indexOf("\n") > 0)
			{
				return("\"" + parameter + "\" contains invalid characters");
			}
		}
		</#if>
		 <#if .node.@ismandatory[0]?exists && .node.@ismandatory[0]?contains("1") && !.node.attributes.precond?is_node>  
		 if (parameter.equals("${name}")) {   
		  if (BaseValidator.validateString(value) == false)
   			return "You must enter a value for \"" + parameter + "\"";	  
   			return null;
		     }  
		     
			</#if>
		<#break>
		
		<#case "Integer">
		if (parameter.equals("${name}")) {   
		if (BaseValidator.validateInteger(value) == false)
            return "\"" + parameter + "\" must be an integer.";
		
		  <#if .node.@ismandatory[0]?exists && .node.@ismandatory[0]?contains("1") && !.node.attributes.precond?is_node> 
		   if (BaseValidator.validateString(value) == false)
   			return "You must enter a value for \"" + parameter + "\"";	
   			
          <#else>
		    
			    <#-- Min && Max -->
			   <#if (.node.attributes.scalemin?is_node  && .node.attributes.scalemin.value?is_node) 
						 && (.node.attributes.scalemax?is_node  && .node.attributes.scalemax.value?is_node)>
			   
			    if (BaseValidator.validateIntegerValueRange(value, ${.node.attributes.scalemin.value}, ${.node.attributes.scalemax.value}) == false)
	    			return "\"" + parameter + "\" must be an integer within the range " +
	                 "${.node.attributes.scalemin.value} to ${.node.attributes.scalemax.value}";
			   </#if>
			    <#-- only Min-->
				<#if (.node.attributes.scalemin?is_node  && .node.attributes.scalemin.value?is_node)
				      && ( !.node.attributes.scalemax?is_node)>	
				    if (BaseValidator.validateIntegerMinValue(value,  ${.node.attributes.scalemin.value}) == false)
						return "\"" + parameter + "\" must be an integer greater than " +
	 							"or equal to  ${.node.attributes.scalemin.value}";
				</#if>
				 <#-- only Max-->
    			<#if .node.attributes.scalemax?is_node  && .node.attributes.scalemax.value?is_node>	
    			   if (BaseValidator.validateIntegerMaxValue(value, ${.node.attributes.scalemax.value}) == false)
						return "\"" + parameter + "\" must be an integer less than " +
	 							"or equal to  ${.node.attributes.scalemax.value}";
    			</#if>
             
            </#if>
            return null;
		     }  
		<#break>
		
		<#case "Float">
		if (parameter.equals("${name}")) {    		
		if (BaseValidator.validateDouble(value) == false)
            return "\"" + parameter + "\" must be a Double.";
		
		  <#if .node.@ismandatory[0]?exists && .node.@ismandatory[0]?contains("1") && !.node.attributes.precond?is_node> 
		   if (BaseValidator.validateString(value) == false)
   			return "You must enter a value for \"" + parameter + "\"";	
   			
          <#else>
		    
			    <#-- Min && Max -->
			   <#if (.node.attributes.scalemin?is_node  && .node.attributes.scalemin.value?is_node) 
						 && (.node.attributes.scalemax?is_node  && .node.attributes.scalemax.value?is_node)>
			   
			    if (BaseValidator.validateDoubleValueRange(value, ${.node.attributes.scalemin.value}, ${.node.attributes.scalemax.value}) == false)
	    			return "\"" + parameter + "\" must be a Double within the range " +
	                 "${.node.attributes.scalemin.value} to ${.node.attributes.scalemax.value}";
			   </#if>
			    <#-- only Min-->
				<#if (.node.attributes.scalemin?is_node  && .node.attributes.scalemin.value?is_node)
				      && ( !.node.attributes.scalemax?is_node)>	
				    if (BaseValidator.validateDoubleMinValue(value,  ${.node.attributes.scalemin.value}) == false)
						return "\"" + parameter + "\" must be a Double greater than " +
	 							"or equal to  ${.node.attributes.scalemin.value}";
				</#if>
				 <#-- only Max-->
    			<#if .node.attributes.scalemax?is_node  && .node.attributes.scalemax.value?is_node>	
    			   if (BaseValidator.validateDoubleMaxValue(value, ${.node.attributes.scalemax.value}) == false)
						return "\"" + parameter + "\" must be a Double less than " +
	 							"or equal to  ${.node.attributes.scalemax.value}";
    			</#if>
             
            </#if>
            return null;
		     }  
		<#break>
		  
		<#break>
		
	</#switch>	
 </#if>	
		

	  
</#macro>
