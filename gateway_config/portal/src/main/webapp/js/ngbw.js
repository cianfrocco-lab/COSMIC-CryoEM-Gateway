function checkAll(allChecked, list) {
	if (allChecked.checked == true)
		for (i = 0; i < list.length; i++)
			list[i].checked = true;
			
	else for (i = 0; i < list.length; i++)
			list[i].checked = false;
}

function reload(form) {
	form.submit();
}

function popitup(url) {
	popitupwithsizes(url, 800, 700);
}
function popupWithSizes(url, width, height, toolbar) {
    if (newwindow) newwindow.close();
    newwindow=window.open(url,'CIPRES','width=' + width + ',height=' + height +',toolbar=' + toolbar + ',scrollbars=1,resizable=1,status=0');
}

function switchMenu(obj) {
	var el = document.getElementById(obj);
	if ( el.style.display != 'none' ) {
		el.style.display = 'none';
	}
	else {
		el.style.display = '';
	}
}

function confirm_delete ( url )
{
	var choice = confirm
        ( "Are you sure?  If you delete this item, you will not be able to recover it." );
	if ( choice )
		window.location=url;
}

function confirm_form()
{
	var agree = confirm
        ( "Are you sure?  If you delete these items, you will not be able to recover them." );
	if (agree)
		return true;
	else return false;
}

function confirm_form_data()
{
	var agree = confirm
        ( "Are you sure?  If you delete these items, you will not be able to recover them. Also, if you are deleting a STAR file data directory and it appears in multiple folders, ALL entries will be deleted as well!" );
	if (agree)
		return true;
	else return false;
}

function getAjaxRequest() {
	var request = null;
	try {
		// Firefox, Opera 8.0+, Safari
		request = new XMLHttpRequest();
		if (request.overrideMimeType) {
			request.overrideMimeType('text/xml');
		}
	} catch (e) {
		// Internet Explorer
		try {
			request = new ActiveXObject("Msxml2.XMLHTTP");
		} catch (e) {
			try {
				request=new ActiveXObject("Microsoft.XMLHTTP");
			} catch (e) {
				alert("No AJAX request object could be created!");
				return null;
			}
		}
	}
	return request;
}

function getCommaDelimitedString(array) {
	if (array == null || array.length == null || array.length < 1)
		return null;
	else {
		var list = "";
		for (i=0; i<array.length; i++) {
			list += array[i];
			if (i < array.length - 1)
				list += ",";
		}
		return list;
	}
}

function getDropdownSelectionsArray(form, value) {
	var dropdown = form.elements[value];
	if (dropdown == null)
		return null;
	else {
		var options = dropdown.options;
		if (options == null)
			return null;
		else {
			var selections = new Array();
			for (i=0; i<options.length; i++) {
				if (options[i].selected)
					selections[selections.length] = options[i].value;
			}
			if (selections.length < 1)
				return null;
			else return selections;
		}
	}
}

function getDropdownSelection(form, value) {
	var selections = getDropdownSelectionsArray(form, value);
	if (selections == null)
		return null;
	else return getCommaDelimitedString(selections);
}

function repopulateDropdown(dropdown, options) {
	// clear and then repopulate the drop-down list
	dropdown.length = 0;
	dropdown.length = options.length;
	for (i=0; i<options.length; i++) {
		dropdown[i].value = options[i].attributes.getNamedItem("value").nodeValue;
		dropdown[i].text = options[i].text;
		// if we encounter the selected option, select it
		var selected = options[i].attributes.getNamedItem("selected");
		if (selected != null && selected.nodeValue == "true")
			dropdown[i].selected = true;
	}
}

function getSubmitElements(form) {
	var submit = new Array();
	for (i=0; i<form.length; i++) {
		var widget = form.elements[i];
		if (widget.type.toLowerCase() == "submit")
			submit[submit.length] = widget;
	}
	if (submit.length < 1)
		return null;
	else return submit;
}


/*
 * This function will display a message dialog and then take user to the update
 * profile page.
 * 
 * @author - Mona Wong
 */
function gotoUpdateProfile()
{
	alert ( "Dear User, CIPRES must now collect information about the institution and the country of all users. This is only for NSF accounting purposes.\n\nWe will now take you to your profile page so you can provide this information." );
	window.location = "./updateProfile!input.action";
}


