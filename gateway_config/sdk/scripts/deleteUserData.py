#!/usr/bin/env python
"""
    Usage:
        deleteUserData.py  

    This should be run via cron, probably weekly, though exact frequency shouldn't matter.
    Need to run more frequently than monthly if sending more than one warning email. (Sending a 2nd 
    warning email is planned for but not implemented yet).

    Install:
        Build cipres as usual and copy deleteUserData.py, mailer.py and deleteWarning1.txt from 
        sdk/scripts to ~/scripts. 

        You may need to modify deleteWarning1.txt - this is the email that will be sent to users.  
        You may also need to modify deleteUserData.py to change the from and cc email addresses, the subject of 
        the email, the limit on how long a user can go without logging in, and the grace period.  

        The line in your crontab file should look like this (to run weekly):
        00 00 * * 1 source $HOME/.bashrc; cd $HOME/scripts; ./deleteUserData.py

            I source the .bashrc to set the PATH and PYTHONPATH 

        Or you may prefer to run this manually, every now and then, when you have the web site down
        for maintenance.  Though unlikely to occur, if this runs and deletes a user's data while he's 
        logged in, he'll see some strange and inconsistent behavior on the site.

    How it Works:

    Checks the last time a user logged in and if it's been more than a year sends the user
    a warning email, indicating that all his tasks and data will be deleted in 30 days 
    unless he logs in.

    If a user can't log in for some reason but notifies a site administrator, the admin
    can update the users record in the db setting the last_login field to any date desired, such 
    as now(), to prevent the deletion from ocurring.

    The db fields that are used are:
        users.last_login :updated on each login, register, password change, read by this script and nothing else
        at this point (1/1/2013).

        user_preferences : this script sets, checks and removes preferences that start with the prefix
        DELETE_USER.  No other code uses these preferences.  
            DELETE_USER_DATE gives the scheduled deletion date, i.e 30 days after the 1st warning was emailed.
            DELETE_USER_2ND_EMAIL gives the date that a 2nd warning email was sent. This script sends it when it
            notices that the current date is within 14 days of the scheduled removal.  NOT CURRENTLY IMPLEMENTED.

    For testing you can insert, update or delete User preferences with statements like:
        insert into user_preferences (user_id, preference, value) values (57200, 'DELETE_USERFOO', 'XXX')
        update user_preferences set value = '2013-01-30' where preference = 'DELETE_USER_DATE' and user_id = 7002
        delete from user_preferences where preference like 'DELETE_USER%'
    and you can change the user.last_login date for a record with a statement like:
        update users set last_login = '2011-06-30' where user_id = 57200
    If you want to force email to be sent, for testing purposes, you need to set user's last_login to > 1 year 
    and you need to delete his DELETE_USER_DATE flag.  User must have at least one folder.  The next time you run this,
    user should receive email.  If you then want to test data deletion, you can change the date of user DELETE_USER_DATE 
    preference to something prior to the current date.


    What to do if a user contacts you and says, "don't delete my stuff!":
        Set his last_login date to now().   Find his user_id based on username or email or whatever you've got, then
        "update users set last_login = now() where user_id = xxx"

    If an email is undeliverable, the sender will usually receive email notification that delivery failed.  The
    sender is specified by the fromaddr variable in deleteUserData.py.
    What to do if you find out an email address was bad, user wasn't notified, and you want to cancel the scheduled deletion:
        Set his the last_login date to now(). 

    TODO: would be nice if email message contained dynamic info like username, limit, gracePeriod portal url, etc .

"""
import sys
import os
import re
import string
import subprocess
import tempfile
import getopt
import pymysql
from pymysql.err import IntegrityError, Error
import csv
import mailer
import traceback

conn = None
preferencePrefix = "DELETE_USER"
preferenceDeleteUserDate = preferencePrefix + "_DATE"
limit = 365 
gracePeriod = 30 
emailSubject = "URGENT Usage Notice from ${portal.name}"
emailContentsFile= os.path.expandvars("${SDK_VERSIONS}/deleteWarning1.txt")
fromaddr = "${email.adminAddr}"
ccaddr = "${email.adminAddr}"


def clearWarningsForRecentLogins():
    """
        Clear all DELETE_USER preferences for users who have logged in recently.
    """

    # This query finds all users who have logged in within the last 365 days, and who have
    # DELETE_USER preference records.  Note that you need to use %% to get a literal percent
    # sign in a python string.
    query = """
        select 
        users.user_id,
        users.first_name,
        users.last_name,
        users.email,
        users.last_login
        from users, user_preferences
        where users.user_id = user_preferences.user_id AND
        (datediff(now(), users.last_login) < %s )
        AND user_preferences.preference like '%s%%' 
        group by users.user_id """ % (limit, preferencePrefix)

    # print "find recent logins query is '%s'" % query

    cur = conn.cursor()
    cur.execute(query)

    for row in cur:
        query2 = """
            delete from user_preferences
            where user_id = %s and preference like '%s%%' """ % (row[0], preferencePrefix)

        # print "delete DELETE_USER flags for recent logins statement is '%s'" % query2
        print "Clearing scheduled deletion for user_id %d, email %s because he logged in on %s" % (row[0],
            row[3], row[4])

        cur2 = conn.cursor()
        try:
            cur2.execute(query2)
            conn.commit()
        except Error, e:
            conn.rollback()
            print str(e)
            print "delete statement $s failed." % query2 
        cur2.close()    
    cur.close()

def issueWarningAndScheduleDeletion():

    # This query finds all users who haven't logged in within the last 365 days and don't already have
    # a DELETE_USER_DATE record. 
    query = """
        select 
        users.user_id,
        users.first_name,
        users.last_name,
        users.email,
        users.username
        from users
        where (datediff(now(), users.last_login) > %s ) AND
        not exists
            (select 1 from user_preferences where
            users.user_id = user_preferences.user_id AND
            user_preferences.preference = '%s')"""  % (limit, preferenceDeleteUserDate)
    # print "find users to warn query is '%s'" % query
    cur = conn.cursor()
    cur.execute(query)

    deleteDate = getDeleteDate()
    """
        It's important to only issue warnings for users who actually have data.  This is to avoid
        sending a warning email to user's who have just had their data deleted the last time this
        ran.  
    """
    for row in cur:
        userid = row[0]
        email = row[3]
        username = row[4]
        if not hasData(userid, username):
            # print "Not warning user_id %d, email %s, because he has no data or tasks." % (userid, email)
            continue
        try:
            # Guest accounts have a bogus email address, don't send to them.
            if username.startswith("Guest-") and not "@" in email:
                pass
            else:
                print "Sending email to user_id %d, email %s.  He/she must log in by %s." % (userid, email, deleteDate)
                mailer.sendMail(email, fromaddr, ccaddr, emailSubject, emailContentsFile)
        except Exception, e:
            # I'm not sure if mail exceptions should be fatal because they indicate a problem that will cause failures
            # on all emails, or if they may specific to an email address.  I suspect the former.  If you find otherwise, 
            # comment out the "raise" line and uncomment the "traceback", "print" and "continue"  lines and the program 
            # will continue after mail exceptions instead of terminating.

            print "Error sending email to %s.  Will try again next time this runs.  Deletion has not been scheduled." % email 

            # traceback.print_exc()
            # print
            # continue
            raise

        # inserting DELETE_USER_DATE flag
        query2 = "insert into user_preferences (user_id, preference, value) values (%d, '%s', '%s')" % (userid, 
            preferenceDeleteUserDate, deleteDate)
        # print "insert deletion date flag statement is " + query2

        cur2 = conn.cursor()
        try:
            cur2.execute(query2)
            conn.commit()
        except Error, e:
            conn.rollback()
            print str(e)
            print "insert statement $s failed." % query2 
            print """ 
                "If this is due to a duplicate key error, it is likely that more than one instance of this
                program is running at the same time or that another program is changing the user preferences
                records at the same time.  This should not be allowed to happen as users may receive duplicate
                and confusing emails as a result.
            """
        cur2.close()    
    cur.close()
        


def doDeletions():
    """
    Find all users with last_login > 365 days, that have a DELETE_USER_DATE preference
    where DELETE_USER_DATE > now(): 
        remove their data, 
        remove their DELETE_USER preferences.
    """
    query = """
        select 
        users.user_id,
        users.first_name,
        users.last_name,
        users.email,
        users.username
        from users
        where 
        (datediff(now(), users.last_login) > %s ) AND
        exists
            (select 1 from user_preferences where
            users.user_id = user_preferences.user_id AND
            user_preferences.preference = '%s' AND datediff(now(), user_preferences.value) > 1)
            """  % (limit, preferenceDeleteUserDate)

    # print "query for delete candidates is: " + query
    cur = conn.cursor()
    cur.execute(query)

    for row in cur:
        if not hasData(row[0], row[4]):
            print "User_id %d, email  %s, is scheduled for deletion but has no data to delete." % (row[0], row[3]) 
            removeFlags(row[0])
        elif sdkUserDelete(row[4], row[0]):
            removeFlags(row[0])
        # Otherwise there was an error removing the user's data so we'll leave the flogs set and try deleting
        # again the next time we run.
    cur.close()

def removeFlags(userid):
    query = """ 
        delete from user_preferences
        where user_id = %s and preference like '%s%%' """ % (userid, preferencePrefix)
    cur = conn.cursor()
    try:
        cur.execute(query)
        conn.commit()
    except Error, e:
        conn.rollback()
        print str(e)
        print "delete statement $s failed." % query 
    cur.close()    


def issue2ndWarnings():
    None

def sdkUserDelete(username, uid):
    """
        Let the sdk handle the deletion so that we defer to its concept of a user's "tasks" and "data" which
        consist of rows in multiple related tables AND flat files.  Execute the command: 
        "userDelete -i <uid> data" to delete the user's tasks and data, but not the account.
    """
    cmd = "userDelete -i %s data" % uid
    # print "Running %s" % cmd
    p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    outerr = p.communicate()
    if (p.returncode != 0):
        print
        print """
            stdout: %s 
            stderr: %s
            """ % (outerr[0], outerr[1])
        print """ 
            '%s' returned an error code.  
             %s's data may not have been deleted.  Will try again next time this runs.
             """ % (cmd, username)
        return False
    print "Deleted tasks and data for username %s, uid=%d"  % (username, uid)
    return True

def hasData(userId, username):
    query = "select folder_id from folders where user_id = %d and label != '%s'" % (userId, username)

    cur = conn.cursor()
    try:
        cur.execute(query)
        conn.commit()
    except Error, e:
        conn.rollback()
        print "hasData query failed. Query was: %s" % query 
        print str(e)
        return 0

    numberOfFolders = 0
    for row in cur:
        numberOfFolders += 1
    return numberOfFolders
    

def getDeleteDate():
    cur2 = conn.cursor()

    query2 = "select date(adddate(now(), %s))" % gracePeriod
    # print "query to compute deletion date is " + query2

    cur2.execute(query2)
    deleteDate = cur2.fetchone()[0]
    # print "deleteDate is " + str(deleteDate)
    cur2.close()

    return deleteDate



def main(argv=None):
    global conn

    if argv is None:
        argv = sys.argv

    passwordFile = os.path.expandvars("${SDK_VERSIONS}/db_password.txt")

    # Get the database name and password
    properties = {} 
    pf = open(passwordFile, "r");
    for line in iter(pf):
        s = line.split('=')
        properties[s[0].strip()] = s[1].strip()
    pf.close()
    
    conn = pymysql.connect(host=properties["host"], port=int(properties["port"]), user=properties["username"], 
        passwd=properties["password"], db=properties["db"])
    # print conn

    secondWarningPeriod = 14
    options, remainder = getopt.getopt(argv[1:], "h")
    for opt, arg in options:
        if opt in ("-h"):
            print __doc__
            return 0

    clearWarningsForRecentLogins()
    issueWarningAndScheduleDeletion()
    doDeletions()
    issue2ndWarnings()

if __name__ == "__main__":
    sys.exit(main())
