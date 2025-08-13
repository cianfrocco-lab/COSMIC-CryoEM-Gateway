package org.ngbw.sdk.common.util;

import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Nice string utilities beyond built-in String methods.
 *
 * @author eliotc, hannes
 * @version 1.1
 */
public class StringUtils
{
    private static final Log log = LogFactory.getLog(StringUtils.class.getName());
    
    public static final int MIN_USERNAME_LENGTH = 5; 
    public static final int MAX_USERNAME_LENGTH = 32; 
    public static final int MIN_PASSWORD_LENGTH = 7; 
      
    public static final String USERNAME_PATTERN = String.format("^[a-zA-Z0-9_-]{%d,%d}$", MIN_USERNAME_LENGTH, MAX_USERNAME_LENGTH);
    
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*\\W).*$";
    
    public static final String EMAIL_USER_ACCOUNT_PATTERN = "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*";
    
    private static final String EMAIL_TOP_LEVEL_DOMAINS = "(\\w{2})|(com|edu|org|gov|net|mil|biz)";
    public static final String EMAIL_DOMAIN_NAME_PATTERN = 
        "^([a-zA-Z]{1})[\\w-_]*\\.{1}(([a-zA-Z]{1})[\\w-_]*\\.{1})*" +
        "(" + EMAIL_TOP_LEVEL_DOMAINS + "([\\.a-zA-Z]{3}){0,1})$";
    
    private static SimpleDateFormat sdfDash = new SimpleDateFormat("MM-dd-yyyy");
    private static SimpleDateFormat sdfDashShort = new SimpleDateFormat("MM-dd-yy");
    private static SimpleDateFormat sdfSlash = new SimpleDateFormat("MM/dd/yyyy");
    private static SimpleDateFormat sdfSlashShort = new SimpleDateFormat("MM/dd/yy");
    private static SimpleDateFormat sdfColon = new SimpleDateFormat("MM:dd:yyyy");
    private static SimpleDateFormat sdfColonShort = new SimpleDateFormat("MM:dd:yy");
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss");


    public static String getMD5HexString ( byte[] bytes )
    {
        if (bytes == null)
        {
            return null;
        }
        
        MessageDigest digestAlgorithm = null;
        
        try
        {
            digestAlgorithm = MessageDigest.getInstance("MD5");
        }
        catch ( Throwable e )
        {
            // probably nosuchalgorithm exception
            log.error(e.getMessage(), e);
            return null;
        }
        
        digestAlgorithm.reset();
        digestAlgorithm.update(bytes);
        byte[] messageDigest = digestAlgorithm.digest();
        StringBuffer hexString = new StringBuffer();
        
        for (int i = 0; i < messageDigest.length; i++)
        {
            String hex = Integer.toHexString(0xff & messageDigest[i]);
            if (hex.length() == 1)
            {
                hexString.append('0'); // Terri: I think this is wrong.  0 should go before the byte.
            }
            hexString.append(hex);
        }
        
        digestAlgorithm.reset();
        return hexString.toString();
    }


    public static String getMD5HexString ( String str ) //throws UnsupportedEncodingException
    {
        if (str == null)
        {
            return null;
        }
        
        return getMD5HexString(str.getBytes());
    }


    public static Boolean string2Boolean ( String string )
    {
        if (string == null || string.trim().isEmpty())
        {
            return null;
        }
        else if (string.trim().equals("1"))
        {
            return true;
        }
        else if (string.trim().equalsIgnoreCase("yes"))
        {
            return true;
        }
        else if (string.trim().equals("0"))
        {
            return false;
        }
        else if (string.trim().equalsIgnoreCase("no"))
        {
            return false;
        }
        else if (string.trim().equalsIgnoreCase("true"))
        {
            return true;
        }
        else if (string.trim().equalsIgnoreCase("false"))
        {
            return false;
        }
        else if (string.trim().equalsIgnoreCase("T"))
        {
            return true;
        }
        else if (string.trim().equalsIgnoreCase("F"))
        {
            return false;
        }
        else
        {
            return false;
        }
    }


    public static Long string2Long ( String string ) throws Exception
    {
        if (string == null || string.trim().isEmpty())
        {
            return null;
        }
        
        return Long.valueOf(string.trim());
    }


    public static Integer string2Int ( String string ) throws Exception
    {
        if (string == null || string.trim().isEmpty())
        {
            return null;
        }
        
        return Integer.valueOf(string.trim());
    }


    public static synchronized Date string2Date ( String dateString )
    {
        dateString = cleanString(dateString);
        if (dateString == null)
        {
            return null;
        }
        Date myDate = null;
        SimpleDateFormat sdf = null;
        if (dateString.contains("-"))
        {
            if (dateString.length() < 10)
            {
                if (sdfDashShort == null)
                {
                    sdfDashShort = new SimpleDateFormat("MM-dd-yy");
                }
                sdf = sdfDashShort;
            }
            else
            {
                if (sdfDash == null)
                {
                    sdfDash = new SimpleDateFormat("MM-dd-yyyy");
                }
                sdf = sdfDash;
            }
        }
        else if (dateString.contains("/"))
        {
            if (dateString.length() < 10)
            {
                if (sdfSlashShort == null)
                {
                    sdfSlashShort = new SimpleDateFormat("MM/dd/yy");
                }
                sdf = sdfSlashShort;
            }
            else
            {
                if (sdfSlash == null)
                {
                    sdfSlash = new SimpleDateFormat("MM/dd/yyyy");
                }
                sdf = sdfSlash;
            }
        }
        else if (dateString.contains(":"))
        {
            if (dateString.length() < 10)
            {
                if (dateString.length() < 10)
                {
                    if (sdfColonShort == null)
                    {
                        sdfColonShort = new SimpleDateFormat("MM:dd:yy");
                    }
                    sdf = sdfColonShort;
                }
                else
                {
                    if (sdfColon == null)
                    {
                        sdfColon = new SimpleDateFormat("MM:dd:yyyy");
                    }
                    sdf = sdfColon;
                }
            }
        }
        else
        {
            throw new RuntimeException("Unrecognized data format!");
        }
        try
        {
            myDate = sdf.parse(dateString);
        }
        catch ( java.text.ParseException e )
        {
            throw new RuntimeException("Parsing error for " + dateString, e);
        }
        return myDate;
    }


    public static synchronized String date2String ( Date date )
    {
        return date2String(date, "-");
    }


    public static synchronized String date2String ( Date date, String delimiter )
    {
        if (date == null)
        {
            return null;
        }
        if (sdfDash == null)
        {
            sdfDash = new SimpleDateFormat("MM-dd-yyyy");
        }
        if (sdfSlash == null)
        {
            sdfSlash = new SimpleDateFormat("MM/dd/yyyy");
        }
        if (sdfColon == null)
        {
            sdfColon = new SimpleDateFormat("MM:dd:yyyy");
        }
        if (delimiter == null)
        {
            return sdfDash.format(date);
        }
        else if (delimiter.trim().equals("-"))
        {
            return sdfDash.format(date);
        }
        else if (delimiter.trim().equals("/"))
        {
            return sdfSlash.format(date);
        }
        else if (delimiter.trim().equals(":"))
        {
            return sdfColon.format(date);
        }
        else
        {
            return sdfDash.format(date);
        }
    }


    public static synchronized String time2String ( Date time )
    {
        return timeFormat.format(time);
    }


    public static synchronized Date string2Time ( String time )
    {
        try
        {
            return timeFormat.parse(time);
        }
        catch ( ParseException e )
        {
            throw new RuntimeException("Parsing error for " + time, e);
        }
    }


    public static synchronized Calendar string2Calendar ( String dateString )
    {
        if (dateString == null)
        {
            return null;
        }
        Calendar myCal = Calendar.getInstance();
        int year = 0;
        int month = -1; //month is 0 based
        int day = 0;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        int milliseconds = 0;
        String date = null;
        String time = null;
        String[] fields = dateString.split(" ");
        date = fields[0];
        String[] dateFields = date.split("-");
        String[] timeFields = null;
        if (fields.length == 2)
        {
            time = fields[1];
            timeFields = time.split(":");
        }
        int nrOfDateFields = dateFields.length;
        if (nrOfDateFields == 1)
        {
            int stringLength = date.length();
            if (stringLength > 3)
            {
                year += Integer.parseInt(dateString.substring(0, 4));
            }
            if (stringLength > 5)
            {
                month += Integer.parseInt(dateString.substring(4, 6));
            }
            if (stringLength > 7)
            {
                day += Integer.parseInt(dateString.substring(6, 8));
            }
            if (stringLength > 9)
            {
                hours += Integer.parseInt(dateString.substring(8, 10));
            }
            if (stringLength > 11)
            {
                minutes += Integer.parseInt(dateString.substring(10, 12));
            }
            if (stringLength > 13)
            {
                seconds += Integer.parseInt(dateString.substring(12, 14));
            }
            if (stringLength > 16)
            {
                milliseconds += Integer.parseInt(dateString.substring(14, 17));
            }
        }
        else
        {
            if (nrOfDateFields > 0)
            {
                year += Integer.parseInt(dateFields[0]);
            }
            if (nrOfDateFields > 1)
            {
                month += Integer.parseInt(dateFields[1]);
            }
            if (nrOfDateFields > 2)
            {
                day += Integer.parseInt(dateFields[2]);
            }
            if (time != null)
            {
                if (nrOfDateFields > 0)
                {
                    hours += Integer.parseInt(timeFields[0]);
                }
                if (nrOfDateFields > 1)
                {
                    minutes += Integer.parseInt(timeFields[1]);
                }
                if (nrOfDateFields > 2)
                {
                    seconds += Integer.parseInt(timeFields[2]);
                }
                if (nrOfDateFields > 3)
                {
                    milliseconds += Integer.parseInt(timeFields[3]);
                }
            }
        }
        myCal.set(year, month, day, hours, minutes, seconds);
        myCal.set(Calendar.MILLISECOND, milliseconds);
        return myCal;
    }


    public static synchronized String fillString ( String s, int max, char c )
    {
        int len = s.length();
        String retVal = s;

        if (len >= max)
        {
            return s;
        }

        int dif = (max - len);

        for (int i = 0; i < dif; i++)
        {
            retVal += c;
        }
        return retVal;
    }


    /**
     * Escape the specified character. The input string will be trim all the leading/trailing white
     * space. If any specified character in the string will be escaped or prepend by "\"
     *
     * @param s     - Input string to process
     * @param aChar - A chraracter to search for escape
     *
     * @return A new String
     */
    public static synchronized String escapeSpecialChar ( String s, char aChar )
    {
        if (s == null || s.trim().length() == 0)
        {
            return "";
        }
        char[] a = s.toCharArray();
        char[] b = new char[2 * a.length];
        int outpos = 0;
        int curpos = 0;
        while (curpos < a.length)
        {
            if (a[curpos] == aChar)
            {
                b[outpos++] = '\\';
            }
            b[outpos++] = a[curpos++];
        }
        if (outpos > 0)
        {
            return new String(b, 0, outpos);
        }
        else
        {
            return "";
        }
    }


    /**
     * remove cr and lf
     *
     * @param s - Input string to process
     *
     * @return A new String
     *
     */
    public static synchronized String removeCrLf ( String s )
    {
        return removeCrLf(s, " ");
    }


    /**
     * remove cr and lf
     *
     * @param s            - Input string to process
     * @param replaceValue
     *
     * @return A new String
     */
    public static synchronized String removeCrLf ( String s, String replaceValue )
    {
        if (s == null || s.trim().length() == 0)
        {
            return "";
        }
        char[] a = s.toCharArray();
        char[] rv = null;
        if (replaceValue == null)
        {
            rv = new char[0];
        }
        else
        {
            rv = replaceValue.toCharArray();
        }
        char[] b = new char[a.length * (rv.length + 1)];
        int outpos = 0;
        int curpos = 0;
        int rvp = 0;
        while (curpos < a.length)
        {
            if (a[curpos] == '\n' || a[curpos] == '\r')
            {
                curpos++;
                for (rvp = 0; rvp < rv.length; rvp++)
                {
                    b[outpos++] = rv[rvp];
                }
                continue;
            }
            b[outpos++] = a[curpos++];
        }
        if (outpos > 0)
        {
            return new String(b, 0, outpos);
        }
        else
        {
            return "";
        }
    }


    /**
     * Escape the quoted string to the approriate string that can be processed by the Persistence
     * layer
     *
     * @param s
     *
     * @return escaped string
     */
    public static synchronized String escapeQuoteChar ( String s )
    {
        if (s == null || s.trim().length() == 0)
        {
            return "";
        }
        char[] a = s.toCharArray();
        char[] b = new char[2 * a.length];
        int outpos = 0;
        int curpos = 0;
        while (curpos < a.length)
        {
            if (a[curpos] == '\'')
            {
                b[outpos++] = '\'';
            }
            b[outpos++] = a[curpos++];
        }
        if (outpos > 0)
        {
            return new String(b, 0, outpos);
        }
        else
        {
            return "";
        }
    }


    /**
     * Escape the quoted string to the appropriate string that can be processed by the Persistence
     * layer
     *
     * @param s
     * @param c - escape character
     *
     * @return escaped string
     */
    public static synchronized String stripLeadingChar ( String s, char c )
    {
        if (s == null || s.trim().length() == 0)
        {
            return "";
        }
        char[] a = s.toCharArray();
        char[] b = new char[a.length];
        int outpos = 0;
        int curpos = 0;
        while (curpos < a.length)
        {
            if (a[curpos] == c)
            {
                curpos++;
                continue;
            }
            b[outpos++] = a[curpos++];
        }
        if (outpos > 0)
        {
            return new String(b, 0, outpos);
        }
        else
        {
            return "";
        }
    }


    /**
     * Convert a strong into an alternating form of each new word starting with a capital letter.
     *
     * @param string
     *
     * @return new string
     */
    public static synchronized String toAlternatingCase ( String string )
    {
        char tempChar[] = string.toLowerCase().toCharArray();

        boolean firstLetter = false;

        // The first character is always upper cased
        tempChar[0] = Character.toUpperCase(tempChar[0]);

        // Loop through the rest of them
        for (int i = 1; i < string.length(); i++)
        {

            if (firstLetter)
            {
                tempChar[i] = Character.toUpperCase(tempChar[i]);
                firstLetter = false;
            }
            else
            {
                // Check for space
                if (tempChar[i] == ' ')
                {
                    firstLetter = true;
                }
            }
        }
        return new String(tempChar);
    }


    /**
     * Convert a strong into an alternating form of each new word starting with a capital letter.
     *
     * @param string
     * @param separators
     *
     * @return new string
     */
    public static synchronized String toTitleCase2 ( String string, String separators )
    {
        char[] seps = separators.toCharArray();
        char[] tempChar = string.toLowerCase().toCharArray();
        boolean firstLetter = false;
        boolean hitSep = false;
        int i, j;

        // The first character is always upper cased
        tempChar[0] = Character.toUpperCase(tempChar[0]);

        // Loop through the rest of them
        for (i = 1; i < string.length(); i++)
        {
            if (firstLetter)
            {
                tempChar[i] = Character.toUpperCase(tempChar[i]);
                firstLetter = false;
            }
            else
            {
                // Check if we hit a separator
                for (j = 0; j < seps.length; j++)
                {
                    if (tempChar[i] == seps[j])
                    {
                        hitSep = true;
                        break;
                    }
                }
                if (hitSep)
                {
                    tempChar[i] = ' ';
                    firstLetter = true;
                    hitSep = false;
                }
            }

        }
        return new String(tempChar);
    }


    /**
     * @param string
     *
     * @return true or false
     */
    public static synchronized boolean isEmpty ( String string )
    {
        return (string == null || string.isEmpty());
    }


    /**
     * @param string
     *
     * @return true or false
     */
    public static synchronized boolean isNotEmpty ( String string )
    {
        return (string != null && !string.trim().isEmpty());
    }
    
    
    public static synchronized boolean isNullOrEmpty ( String string ) 
    {
        return isNullOrEmpty(string, false); 
    }
    
    
    public static synchronized boolean isNullOrEmpty ( String string, boolean trim ) 
    {
        if (trim) 
        {
            return (string == null || string.trim().isEmpty());
        }
        
        return (string == null || string.isEmpty());
    }
    
    
//    public static synchronized boolean 
//    isValidEmailFormat_ ( String email ) 
//    {
//        if (email == null || email.trim().isEmpty()) 
//        {
//            return false;
//        }
//        else if (!email.contains("@"))
//        {
//            return false;
//        }
//        
//        String strArr[] = email.split("@");
//
//        if (strArr.length < 2) 
//        {
//            return false;
//        }
//
////        String username = strArr[0]; // username portion 
////        String domain = strArr[1];   // domain portion 
////        
////        Pattern pattern = Pattern.compile(EMAIL_USER_ACCOUNT_PATTERN);
////        Matcher matcher = pattern.matcher(username);
////        boolean validUserName = matcher.matches();
////        
////        pattern = Pattern.compile(EMAIL_DOMAIN_NAME_PATTERN);
////        matcher = pattern.matcher(domain);
////        boolean validDomain = matcher.matches();
////
////        return validUserName && validDomain;
//        return true;
//    }
    
    
//    public static synchronized boolean 
//    isValidPasswordFormat_ ( String password ) 
//    {
//        if (password == null || password.trim().isEmpty())
//        {
//            return false; 
//        }
//        
//        Pattern pattern = Pattern.compile(PASSWORD_PATTERN);
//        Matcher matcher = pattern.matcher(password.trim());
//        return matcher.matches();
//        //return (password.length() >= MIN_PASSWORD_LENGTH); 
//    }
    
    
//    public static synchronized boolean 
//    isValidUsernameFormat_ ( String username ) 
//    {
//        if (username == null || username.trim().isEmpty())
//        {
//            return false; 
//        }
//        
////        Pattern pattern = Pattern.compile(USERNAME_PATTERN);
////        Matcher matcher = pattern.matcher(username.trim());
////        return matcher.matches();
////        return (username.trim().length() >= MIN_USERNAME_LENGTH && username.trim().length() <= MAX_USERNAME_LENGTH);
//        return true;
//    }
    
    
    public static synchronized boolean 
    isValidFormat ( String pattern, String string ) throws PatternSyntaxException 
    {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(string);
        return m.matches();
    }


    /**
     * @param input
     * @param maxTokenLength
     *
     * @return new string
     */
    public static synchronized String splitLongWords ( String input, int maxTokenLength )
    {
        String[] token = input.split(" ");
        StringBuffer output = new StringBuffer();
        int tokenCount = token.length;
        for (int i = 0; i < tokenCount; i++)
        {
            if (token[i].length() > maxTokenLength)
            {
                // indexOf returns -1 if the string is not found
                String[] chars = new String[]
                {
                    "-", "{", "}", "[", "]", "(",
                    ")"
                };
                int[] index = new int[chars.length];

                index[0] = token[i].indexOf(chars[0]);
                index[1] = token[i].indexOf(chars[1]);
                index[2] = token[i].indexOf(chars[2]);
                index[3] = token[i].indexOf(chars[3]);
                index[4] = token[i].indexOf(chars[4]);
                index[5] = token[i].indexOf(chars[5]);
                index[6] = token[i].indexOf(chars[6]);

                String bestChar = null;
                int bestDiff = maxTokenLength;
                int bestIndex = 0;

                for (int j = 0; j < chars.length; j++)
                {
                    //					System.err.println(chars[j] + ":" +index[j] );
                    if (index[j] == -1)
                    {
                        continue;
                    }
                    if (index[j] == maxTokenLength)
                    {
                        bestChar = chars[j];
                        break;
                    }
                    else
                    {
                        int diff = maxTokenLength - index[j];
                        if (diff > 0 && diff < bestDiff)
                        {
                            bestChar = chars[j];
                            bestDiff = diff;
                            bestIndex = index[j];
                        }
                    }
                }
                String prefix = token[i].substring(0, bestIndex);

                String suffix = token[i].substring(bestIndex + 1);
                if (suffix.length() > maxTokenLength)
                {
                    //					System.err.println(prefix + bestChar + suffix);
                    token[i] = prefix + bestChar + " "
                            + splitLongWords(suffix, maxTokenLength);
                }
                else
                {
                    token[i] = prefix + bestChar + " " + suffix;
                }

                //				System.err.println(bestChar + " at " + bestIndex + " actually used for suffix " + suffix);
            }
            output.append(token[i]);
            if (i < tokenCount)
            {
                output.append(" ");
            }
        }
        return output.toString();
    }


    /**
     * This method removes any number of leading characters given by the string in the argument
     *
     * @param symbol - symbol to be removed from the beginning of the string
     *
     * @return resulting string
     */
    public static synchronized String trimLeading ( String content, String symbol )
    {

        while (true)
        {
            if (content.startsWith(symbol))
            {
                content = content.substring(1);
            }
            else
            {
                return content;
            }
        }

    }


    /**
     * @param content
     * @param symbol
     *
     * @return new string
     */
    public static synchronized String trimTrailing ( String content, String symbol )
    {

        while (true)
        {
            if (content.endsWith(symbol))
            {
                content = content.substring(0, content.length() - 1);
            }
            else
            {
                return content;
            }
        }
    }


    /**
     * @param field
     *
     * @return new string
     */
    public static synchronized String cleanString ( String field )
    {
        if (field == null)
        {
            return null;
        }
        String cleanString = field.trim();
        if (cleanString.equalsIgnoreCase(""))
        {
            return null;
        }
        if (cleanString.equalsIgnoreCase("NULL"))
        {
            return null;
        }
        if (cleanString != null && cleanString.length() == 0)
        {
            return null;
        }
        return cleanString;
    }


    /**
     * @param date
     *
     * @return date as string
     */
    public static synchronized String reformatDateString ( String date )
    {
        if (date == null)
        {
            return null;
        }
        String[] fields = date.split("/");
        if (fields.length != 3)
        {
            throw new RuntimeException("This date is out of specs: " + date);
        }
        StringBuffer sb = new StringBuffer();
        if (fields[0].length() < 2)
        {
            sb.append("0");
        }
        sb.append(fields[0]);
        sb.append("-");
        if (fields[1].length() < 2)
        {
            sb.append("0");
        }
        sb.append(fields[1]);
        sb.append("-");
        if (fields[2].length() == 2 && Integer.parseInt(fields[2]) > 5)
        {
            sb.append(19);
            sb.append(fields[2]);
        }
        else if (fields[2].length() == 2 && Integer.parseInt(fields[2]) < 6)
        {
            sb.append(20);
            sb.append(fields[2]);
        }
        else
        {
            sb.append(fields[2]);
        }
        return sb.toString();
    }


    /**
     * Method compares 2 string values after trimming and returns either value if they are
     * identical, or the trimmed not null value if one of the value is null or it return "ambiguous"
     * if bother values are not null and differ from each other
     *
     * @param value1
     * @param value2
     *
     * @return String value
     */
    public static synchronized String matchStringProperty ( String value1, String value2 )
    {
        String cleanValue1 = cleanString(value1);
        String cleanValue2 = cleanString(value2);
        if (cleanValue1 == cleanValue2)
        {
            return (cleanValue1);
        }
        else if (cleanValue1 == null)
        {
            return cleanValue2;
        }
        else if (cleanValue2 == null)
        {
            return cleanValue1;
        }
        else if (cleanValue1.equals(cleanValue2))
        {
            return cleanValue1;
        }
        else
        {
            return "ambiguous";
        }
    }


    /**
     * @param value1
     * @param value2
     * @param message
     *
     * @return string
     */
    public static synchronized String matchStringProperty ( String value1, String value2,
                                               String message )
    {
        String cleanValue1 = cleanString(value1);
        String cleanValue2 = cleanString(value2);
        if (cleanValue1 == cleanValue2)
        {
            return (cleanValue1);
        }
        else if (cleanValue1 == null)
        {
            return cleanValue2;
        }
        else if (cleanValue2 == null)
        {
            return cleanValue1;
        }
        else if (cleanValue1.equals(cleanValue2))
        {
            return cleanValue1;
        }
        else
        {
            return "ambiguous";
        }
    }


    /**
     * Removes all spaces from the input string
     *
     * @param source
     *
     * @return input string less spaces
     */
    public static synchronized String removeSpaces ( String source )
    {
        String input = cleanString(source);
        if (input == null)
        {
            return null;
        }
        String output = input.replaceAll(" ", "");
        return output;
    }


    /**
     * Removes the first and last character of a trimmed input string if they are double quotes
     *
     * @param source
     * @param quote  - character
     *
     * @return input string w/o quotes
     */
    public static synchronized String removeQuotes ( String source, char quote )
    {
        String input = cleanString(source);
        if (input == null)
        {
            return null;
        }
        String meta = null;
        if (input.startsWith(String.valueOf(quote)))
        {
            meta = input.replaceFirst(String.valueOf(quote), "");
        }
        else
        {
            meta = input;
        }
        if (input.endsWith(String.valueOf(quote)))
        {
            return meta.substring(0, meta.lastIndexOf(quote));
        }
        return meta;
    }


    /**
     * Removes the outer double quotes from the input string
     *
     * @param source
     *
     * @return string
     */
    public static synchronized String removeDoubleQuotes ( String source )
    {
        return removeQuotes(source, '"');
    }


    /**
     * Removes the outer single quotes from the input string
     *
     * @param source
     *
     * @return string
     */
    public static synchronized String removeSingleQuotes ( String source )
    {
        return removeQuotes(source, '\'');
    }


    /**
     * Joins the the list elements to a string delimited by '\t'
     *
     * @param collection
     *
     * @return string joined array
     */
    public static synchronized String join ( Collection<?> collection )
    {
        return join(collection, '\t');
    }


    /**
     * Joins the the array elements to a string delimited by '\t'
     *
     * @param array
     *
     * @return string joined array
     */
    public static synchronized String join ( String[] array )
    {
        return join(array, '\t');
    }


    /**
     * Joins the the collection elements to a string delimited by the submitted delimiter
     *
     * @param collection
     * @param delimiter
     *
     * @return string joined array
     */
    public static synchronized String join ( Collection<?> collection, Character delimiter )
    {
        if (collection == null || collection.size() == 0)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = collection.iterator();
        sb.append(it.next());
        while (it.hasNext())
        {
            sb.append(delimiter);
            sb.append(it.next());
        }
        return sb.toString();
    }


    /**
     * Joins the the collection elements to a string delimited by the submitted delimiter
     *
     * @param collection
     * @param delimiter
     *
     * @return string joined array
     */
    public static synchronized String join ( Collection<?> collection, String delimiter )
    {
        if (collection == null || collection.size() == 0)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = collection.iterator();
        sb.append(it.next());
        while (it.hasNext())
        {
            sb.append(delimiter);
            sb.append(it.next());
        }
        return sb.toString();
    }


    /**
     * Joins the the array elements to a string delimited by the submitted delimiter
     *
     * @param array
     * @param delimiter
     *
     * @return string joined array
     */
    public static synchronized String join ( String[] array, Character delimiter )
    {
        if (array == null)
        {
            return null;
        }
        int length = array.length;
        if (length == 0)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(array[0]);
        for (int i = 1; i < length; i++)
        {
            sb.append(delimiter);
            sb.append(array[i]);
        }
        return sb.toString();
    }


    /**
     * Joins the the array elements to a string delimited by the submitted delimiter
     *
     * @param array
     * @param delimiter
     *
     * @return string joined array
     */
    public static synchronized String join ( String[] array, String delimiter )
    {
        if (array == null)
        {
            return null;
        }
        int length = array.length;
        if (length == 0)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(array[0]);
        for (int i = 1; i < length; i++)
        {
            sb.append(delimiter);
            sb.append(array[i]);
        }
        return sb.toString();
    }


    /**
     * Removes all characters apar from 0-9.,>< It also removes the last . (dot) if there is more
     * then one @param value @return cleaned value
     */
    public static synchronized String cleanNumericValue ( String value )
    {
        if (value == null)
        {
            return null;
        }
        int firstIndex = value.indexOf('.');
        int lastIndex = value.lastIndexOf('.');
        if (firstIndex != lastIndex)
        {
            value = new StringBuffer(value).deleteCharAt(lastIndex).toString();
        }
        return cleanString(value.replaceAll("[^0-9\\.\\,\\>\\<]", ""));
    }


    /**
     * Indents all lines of the argument string by the specified number of spaces.
     *
     * @param value		The string whose lines are to be indented
     * @param spaces	The number of spaces by which to indent each line
     *
     * @return	The resulting indented string. Each line will be terminated by a single newline
     *         character, regardless of the line terminators used in the original string.
     */
    public static synchronized String indent ( String value, int spaces )
    {
        if (spaces < 0)
        {
            spaces = 0;
        }
        String indent = "";
        for (int i = 0; i < spaces; i++)
        {
            indent += " ";
        }
        String[] lines = value.split("\r\n|\r|\n");
        String output = "";
        for (int i = 0; i < lines.length; i++)
        {
            output += indent + lines[i] + "\n";
        }
        return output;
    }


    public static synchronized String map2String ( Map<String, String> p )
    {
        String s = "";
        String v;

        for (String k : p.keySet())
        {
            v = p.get(k);
            s += (k + "=" + v + "; ");
        }

        return s;
    }
    
    public static synchronized boolean
    hasPattern ( String text, Pattern pattern ) throws PatternSyntaxException
    {       
        if (null == pattern)
        {   
            throw new RuntimeException("Pattern is null.");
        }   
            
        return pattern.matcher(text).matches();
    }         
    
    public static void main ( String[] args )
    {
        
    }
    
}
