package org.ngbw.sdk.common.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * The <tt>BaseValidator</tt> is a simple Validator classe that 
 * can be used to validate untyped client input such as
 * web forms etc.
 * Of course the author encourages the potential user to check 
 * out whether more sophisticated projects such as
 * Stuts/Xworks or Hibernate or Apache validator are not
 * more appropriate for the intended use.<br />
 * 
 * @author Roland H. Niedner <br />
 */
public class BaseValidator {
	private static final Log log = LogFactory.getLog(BaseValidator.class);
	private static final int MAX_FILENAME=100;

	/**
	 * Method checks whether the specified value is not null.
	 * 
	 * @param value
	 * @return true if value is not null otherwise false
	 */
	public static boolean validateRequired(Object value) {
		if (value == null)
			return false;
		return true;
	}

	/*
		Only allow characters in the whitelist: letters, numbers, space, tab,
		underscore, hyphen, period, comma, equal sign, plus sign (minus is hyphen).
	*/
	public static boolean matchesWhitelist(Object value) 
	{
		if (value == null)
		{
			return true;
		}
		String string = toString(value);
		return string.matches("^[a-zA-Z0-9 \\t_\\-\\.,=+]*$");

	}

	/*
		Used for situations where user can specify the filename and we'll be using it as part
		of a command.  Since we don't always quote the filenames on the command line
		we can't allow any shell special characters.  We also want to make sure it's
		just a filename, no directory path.  TODO: could be much less restrictive if
		we quoted the filename on the command line.

		Only allowing letters, numbers, underscore, hyphen, period and comma.
		No longer than 100 characters.
	*/
	public static boolean isSimpleFilename(String filename)
	{
		if (filename == null || filename.length() > MAX_FILENAME)
		{
			return false;
		}
		return filename.matches("^[a-zA-Z0-9_\\-\\.,]*$"); 
	}

    /* Mona: since COSMIC2 now handles directories are data, we need to allow
     * the path separator character as long as it is NOT the first character!
     */
	public static boolean isSimplePathFilename(String filename)
	{
		if (filename == null || filename.length() > MAX_FILENAME)
		{
			return false;
		}
		return filename.matches("^(?!/)[a-zA-Z0-9/_\\-\\.,]*$"); 
	}

	/**
	 * Method checks whether the specified string value is not empty.
	 * Empty in this case means: null or "^\s*$".
	 * 
	 * @param value
	 * @return true if String is not empty otherwise false
	 */
	public static boolean validateString(Object value) {
		if (value == null)
			return false;
		if (toString(value).length() == 0)
			return false;
		return true;
	}

	/**
	 * Method checks whether the specified value is an integer.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if (not null) value can be parsed into an integer otherwise false
	 */
	public static boolean validateInteger(Object value) {
		if (validateString(value) == false)
		{
			return true;
		}
		if (!matchesWhitelist(toString(value)))
		{
			return false;
		}
		try {
			Integer.parseInt(toString(value));
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/**
	 * Method checks whether the specified value is equal to the specified integer.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param requiredValue
	 * @return true if (not null) value == requiredValue otherwise false
	 */
	public static boolean validateInteger(Object value, int requiredValue) {
		if (validateString(value) == false)
			return true;
		if (validateInteger(value) == false)
			return false;
		return Integer.valueOf(toString(value)) == requiredValue;
	}
	
	/**
	 * Method checks whether the submitted value is an integer and equal
	 * or greater than the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @return Integer.valueOf(toString(value)) >= minValue
	 */
	public static boolean validateIntegerMinValue(Object value, int minValue) {
		if (validateString(value) == false)
			return true;
		if (validateInteger(value) == false)
			return false;
		return Integer.valueOf(toString(value)) >= minValue;
	}
	
	/**
	 * Method checks whether the submitted value is an integer and equal
	 * or smaller that the specified maximum value.
	 * If submitted value is null or an empty string method returns true.
	 *  
	 * @param value
	 * @param maxValue
	 * @return Integer.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateIntegerMaxValue(Object value, int maxValue) {
		if (validateString(value) == false)
			return true;
		if (validateInteger(value) == false)
			return false;
		return Integer.valueOf(toString(value)) <= maxValue;
	}
	
	/**
	 * Method checks whether the submitted value is an integer and equal
	 * or smaller than the specified maximum value and equal or
	 * greater that the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @param maxValue
	 * @return minValue <= Integer.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateIntegerValueRange(Object value, int minValue, int maxValue) {
		if (validateString(value) == false)
			return true;
		return validateIntegerMinValue(value, minValue)
			&& validateIntegerMaxValue(value, maxValue);
	}

	/**
	 * Method checks whether the specified value is an positive integer.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value >= 0 otherwise false
	 */
	public static boolean validatePositiveInteger(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateInteger(value) == false)
			return false;
		return Integer.valueOf(toString(value)) >= 0;
	}

	/**
	 * Method checks whether the specified value is an negative integer.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value < 0 otherwise false
	 */
	public static boolean validateNegativeInteger(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateInteger(value) == false)
			if (validateInteger(value) == false)
				return false;
		return Integer.valueOf(toString(value)) < 0;
	}

	/**
	 * Method checks whether the specified value is a float.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value can be parsed into a float otherwise false
	 */
	public static boolean validateFloat(Object value) {
		if (validateString(value) == false)
		{
			return true;
		}
		if (!matchesWhitelist(toString(value)))
		{
			return false;
		}
		try {
			Float.parseFloat(toString(value));
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/**
	 * Method checks whether the specified value is equal to the specified float.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param requiredValue
	 * @return true if value == requiredValue otherwise false
	 */
	public static boolean validateFloat(Object value, float requiredValue) {
		if (validateString(value) == false)
			return true;
		if (validateFloat(value) == false)
			return false;
		return Float.valueOf(toString(value)) == requiredValue;
	}
	
	/**
	 * Method checks whether the submitted value is a float and equal
	 * or greater than the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @return Float.valueOf(toString(value)) >= minValue
	 */
	public static boolean validateFloatMinValue(Object value, float minValue) {
		if (validateString(value) == false)
			return true;
		if (validateFloat(value) == false)
			return false;
		return Float.valueOf(toString(value)) >= minValue;
	}
	
	/**
	 * Method checks whether the submitted value is a float and equal
	 * or smaller that the specified maximum value.
	 * If submitted value is null or an empty string method returns true.
	 *  
	 * @param value
	 * @param maxValue
	 * @return Float.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateFloatMaxValue(Object value, float maxValue) {
		if (validateString(value) == false)
			return true;
		if (validateFloat(value) == false)
			return false;
		return Float.valueOf(toString(value)) <= maxValue;
	}
	
	/**
	 * Method checks whether the submitted value is a float and equal
	 * or smaller than the specified maximum value and equal or
	 * greater that the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @param maxValue
	 * @return minValue <= Float.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateFloatValueRange(Object value, float minValue, float maxValue) {
		if (validateString(value) == false)
			return true;
		return validateFloatMinValue(value, minValue)
			&& validateFloatMaxValue(value, maxValue);
	}

	/**
	 * Method checks whether the specified value is an positive float.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value >= 0 otherwise false
	 */
	public static boolean validatePositiveFloat(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateFloat(value) == false)
			return false;
		return Float.valueOf(toString(value)) >= 0;
	}

	/**
	 * Method checks whether the specified value is an negative float.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value < 0 otherwise false
	 */
	public static boolean validateNegativeFloat(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateFloat(value) == false)
			return false;
		return Float.valueOf(toString(value)) < 0;
	}

	/**
	 * Method checks whether the specified value is a double.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value can be parsed into a double otherwise false
	 */
	public static boolean validateDouble(Object value) 
	{
		if (validateString(value) == false)
		{
			return true;
		}
		if (!matchesWhitelist(toString(value)))
		{
			return false;
		}
		try 
		{
			Double.parseDouble(toString(value));
		} catch (NumberFormatException e) 
		{
			//log.debug(e.toString());
			return false;
		}
		return true;
	}

	/**
	 * Method checks whether the specified value is equal to the specified double.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param requiredValue
	 * @return true if value == requiredValue otherwise false
	 */
	public static boolean validateDouble(Object value, double requiredValue) {
		if (validateString(value) == false)
			return true;
		if (validateDouble(value) == false)
			return false;
		return Double.valueOf(toString(value)) == requiredValue;
	}
	
	/**
	 * Method checks whether the submitted value is a double and equal
	 * or greater than the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @return Double.valueOf(toString(value)) >= minValue
	 */
	public static boolean validateDoubleMinValue(Object value, double minValue) {
		if (validateString(value) == false)
			return true;
		if (validateDouble(value) == false)
			return false;
		return Double.valueOf(toString(value)) >= minValue;
	}
	
	/**
	 * Method checks whether the submitted value is a double and equal
	 * or smaller that the specified maximum value.
	 * If submitted value is null or an empty string method returns true.
	 *  
	 * @param value
	 * @param maxValue
	 * @return Double.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateDoubleMaxValue(Object value, double maxValue) {
		if (validateString(value) == false)
			return true;
		if (validateDouble(value) == false)
			return false;
		return Double.valueOf(toString(value)) <= maxValue;
	}
	
	/**
	 * Method checks whether the submitted value is a double and equal
	 * or smaller than the specified maximum value and equal or
	 * greater that the specified minimum value.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minValue
	 * @param maxValue
	 * @return minValue <= Double.valueOf(toString(value)) <= maxValue
	 */
	public static boolean validateDoubleValueRange(Object value, double minValue, double maxValue) {
		if (validateString(value) == false)
			return true;
		return validateDoubleMinValue(value, minValue)
			&& validateDoubleMaxValue(value, maxValue);
	}

	/**
	 * Method checks whether the specified value is an positive double.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if value >= 0 otherwise false
	 */
	public static boolean validatePositiveDouble(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateDouble(value) == false)
			return false;
		return Double.valueOf(toString(value)) >= 0;
	}

	/**
	 * Method checks whether the specified value is an negative double.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if (not null) value < 0 otherwise false
	 */
	public static boolean validateNegativeDouble(Object value) {
		if (validateString(value) == false)
			return true;
		if (validateDouble(value) == false)
			return false;
		return Double.valueOf(toString(value)) < 0;
	}
	
	/**
	 * Method checks whether the specified value can be parsed into a valid date.
	 * If submitted value is null or an empty string method returns true.
	 * The submitted string may only contain digits and delimiting characters 
	 * (':', '/', '-'). Single digit month are to be prefixed with a 0.
	 * The following string are considered valid (M - month, d - day, y - year): 
	 * <ul>
	 * 	<li>MM-dd-yyyy</li>
	 * 	<li>MM-dd-yy</li>
	 * 	<li>MM/dd/yyyy</li>
	 * 	<li>MM/dd/yy</li>
	 * 	<li>MM:dd:yyyy</li>
	 * 	<li>MM:dd:yy</li>
	 * </ul>
	 * 
	 * @param value
	 * @return true if the (not null) value can be parsed into a valid date
	 */
	public static boolean validateDate(Object value) {
		if (validateString(value) == false)
			return true;
		String dateString = toString(value);
		try {
			toDate(dateString);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Method checks whether the submitted value can be parsed into a 
	 * valid Date that is equal or lies after the specified minimum date.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minDate
	 * @return minDate.equals(value) || minDate.before(value)
	 */
	public static boolean validateDateMinValue(Object value, Date minDate) {
		if (minDate == null)
			throw new RuntimeException("minDate argument must not be null!");
		if (validateString(value) == false)
			return true;
		if (validateDate(value) == false) return false;
		Date when = toDate(toString(value));
		return minDate.equals(when) || minDate.before(when);
	}

	/**
	 * Method checks whether the submitted value can be parsed into a 
	 * valid Date that is equal or lies before the specified maximum date.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param maxDate
	 * @return maxDate.equals(value) || maxDate.after(value)
	 */
	public static boolean validateDateMaxValue(Object value, Date maxDate) {
		if (maxDate == null)
			throw new RuntimeException("maxDate argument must not be null!");
		if (validateString(value) == false)
			return true;
		if (validateDate(value) == false) return false;
		Date when = toDate(toString(value));
		return maxDate.equals(when) || maxDate.after(when);
	}

	/**
	 * Method checks whether the submitted value can be parsed into a 
	 * valid Date that is equal or lies after the specified minimum date and
	 * that is equal or lies before the specified maximum date.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minDate
	 * @param maxDate
	 * @return (minDate.equals(value) || minDate.before(value)) && maxDate.equals(when) || maxDate.after(when)
	 */
	public static boolean validateDateRange(Object value, Date minDate, Date maxDate) {
		if (minDate == null || maxDate == null)
			throw new RuntimeException("minDate and maxDate arguments must not be null!");
		if (validateString(value) == false)
			return true;
		return validateDateMinValue(value, minDate)
			&& validateDateMaxValue(value, maxDate);
	}

	/**
	 * Method checks whether the specified value is correctly
	 * parsed by the java.net.URL(String url) constructor.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if no MalformedURLException is thrown otherwise false 
	 */
	public static boolean validateURL(Object value) {
		if (validateString(value) == false)
			return true;
		try {
			new URL(toString(value));
		} catch (MalformedURLException e) {
			return false;
		}
		return true;
	}

	/**
	 * Method checks the submitted value whether it (could) represent
	 * a valid email address.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @return true if valid email otherwise false
	 */
	public static boolean validateEmail(Object value) {
		if (validateString(value) == false)
			return true;
		//Checks for email addresses starting with
		//inappropriate symbols like dots or @ signs.
		Pattern p = Pattern.compile("^\\.|^\\@");
		String input = toString(value);
		Matcher m = p.matcher(input);
		if (m.find())
			//"Email addresses don't start with dots or @ signs."
			return false;

		//Checks for email addresses that start with www
		p = Pattern.compile("^www\\.");
		m = p.matcher(input);
		if (m.find())
			//"Email addresses don't start with \"www.\", only web pages do."
			return false;

		//search for illegal characters
		p = Pattern.compile("[^A-Za-z0-9\\.\\@_\\-~#]+");
		m = p.matcher(input);
		StringBuffer sb = new StringBuffer();
		boolean result = m.find();
		boolean deletedIllegalChars = false;

		while (result) {
			deletedIllegalChars = true;
			m.appendReplacement(sb, "");
			result = m.find();
		}

		if (deletedIllegalChars)
			//"It contained incorrect characters , such as spaces or commas."
			return false;

		return true;
	}

	/**
	 * Method checks whether the specified value is a String of the 
	 * specified length. The method will NOT trim any whitespace 
	 * leading or trailing the string! If this is desired you'll have to do that
	 * before submitting it to this method.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param length
	 * @return true if String.valueOf(value).length() == length otherwise false
	 */
	public static boolean validateStringLength(Object value, int length) {
		if (validateString(value) == false)
			return true;
		return String.valueOf(value).length() == length;
	}

	/**
	 * Method checks whether the submitted value is a string of a length
	 * equal or greater than the specified minimum length.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minLength
	 * @return true if String.valueOf(value).length() >= minLength otherwise false
	 */
	public static boolean validateStringMinLength(Object value, int minLength) {
		if (validateString(value) == false)
			return true;
		return String.valueOf(value).length() >= minLength;
	}

	/**
	 * Method checks whether the submitted value is a string of a length
	 * equal or smaller than the specified maximum length.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param maxLength
	 * @return true if String.valueOf(value).length() <= maxLength otherwise false
	 */
	public static boolean validateStringMaxLength(Object value, int maxLength) {
		if (validateString(value) == false)
			return true;
		return String.valueOf(value).length() <= maxLength;
	}

	/**
	 * Method checks whether the submitted value is a string with a length
	 * within the specified range.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param minLength
	 * @param maxLength
	 * @return true if minLength <= String.valueOf(value).length() <= maxLength otherwise false
	 */
	public static boolean validateStringLengthRange(Object value,
			int minLength, int maxLength) {
		if (validateString(value) == false)
			return true;
		return validateStringMinLength(value, minLength) 
			&& validateStringMaxLength(value, maxLength);
	}

	/**
	 * Method tests whether the submitted value is a string that
	 * matches the submitted regular expression.
	 * If submitted value is null or an empty string method returns true.
	 * 
	 * @param value
	 * @param regex
	 * @return true if the regex matches the value otherwise false 
	 */
	public static boolean validateRegex(Object value, String regex) {
		if (validateString(value) == false)
			return true;
		return toString(value).matches(regex);
	}

	// Private Methods //
	
	private static String toString(Object value) {
		return String.valueOf(value).trim();
	}
	
	private static SimpleDateFormat sdfDash = new SimpleDateFormat("MM-dd-yyyy");
	private static SimpleDateFormat sdfDashShort = new SimpleDateFormat("MM-dd-yy");
	private static SimpleDateFormat sdfSlash = new SimpleDateFormat("MM/dd/yyyy");
	private static SimpleDateFormat sdfSlashShort = new SimpleDateFormat("MM/dd/yy");
	private static SimpleDateFormat sdfColon = new SimpleDateFormat("MM:dd:yyyy");
	private static SimpleDateFormat sdfColonShort = new SimpleDateFormat("MM:dd:yy");
	
	private static Date toDate(String dateString) {
		SimpleDateFormat sdf = null;
		if (dateString.contains("-")) {
			if (dateString.length() < 10) {
				if (sdfDashShort == null)
					sdfDashShort = new SimpleDateFormat("MM-dd-yy");
				sdf = sdfDashShort;
			} else {
				if (sdfDash == null)
					sdfDash = new SimpleDateFormat("MM-dd-yyyy");
				sdf = sdfDash;
			}
		} else if (dateString.contains("/")) {
			if (dateString.length() < 10) {
				if (sdfSlashShort == null)
					sdfSlashShort = new SimpleDateFormat("MM/dd/yy");
				sdf = sdfSlashShort;
			} else {
				if (sdfSlash == null)
					sdfSlash = new SimpleDateFormat("MM/dd/yyyy");
				sdf = sdfSlash;
			}
		} else if (dateString.contains(":")) {
			if (dateString.length() < 10) {
				if (dateString.length() < 10) {
					if (sdfColonShort == null)
						sdfColonShort = new SimpleDateFormat("MM:dd:yy");
					sdf = sdfColonShort;
				} else {
					if (sdfColon == null)
						sdfColon = new SimpleDateFormat("MM:dd:yyyy");
					sdf = sdfColon;
				}
			}
		} else {
			throw new RuntimeException("Unrecognized data format!");
		}
		Date myDate = null;
		try {
			myDate = sdf.parse(dateString);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return myDate;
	}
}
