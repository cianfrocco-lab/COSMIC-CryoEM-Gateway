#!/usr/bin/env python
# /projects/cosmic2/gateway/globus_transfers
# 
#from build.properties:
#
#user.data.size.max=107374182400
#user.data.size.warn=53687091200
#
#Should these be parsed out of build.properties?  That would help
#keep things consistent...

import os
import string
import time

GTD = "${database.globusRoot}"
WARNBYTES = ${user.data.size.warn}
MAILLIST = "${email.adminAddr}"
MAILX = os.popen('which mailx').read().strip()
ECHO = os.popen('which echo').read().strip()
DU = os.popen('which du').read().strip()
# check to see if MAILX, ECHO and DU got populated.
# would like to check to see if they are valid commands.
# how to deal with shell aliases? aliases seem to not get
# picked up by which...
# in warn(), there will probably be stderr output that
# would get emailed by cron...

def warn(message, subject, recipient) :
    cmd = ECHO + ' ' + "'%s'" % message + ' | ' + MAILX + ' -s ' + '"%s"' % subject + " %s" % recipient
    os.system(cmd)

entry_list = os.listdir(GTD)
dir_list = []
warn_list = []
for entry_item in entry_list:
    fullpath = GTD + '/' + entry_item
    if os.path.isdir(fullpath):
        dir_list.append(fullpath)
        usage_raw = os.popen("du -sb %s" % fullpath).read()
        #usage_bytes = int(string.split(usage_raw)[0])
        usage_bytes = int(usage_raw.split()[0])
        #print "usage_raw (%s) usage_bytes (%s)" % (usage_raw, usage_bytes)
        if usage_bytes > WARNBYTES:
            warn_list.append(
                "high disk usage (%s) for (%s)\n" %
                   (usage_bytes, fullpath)) 

# check each user for exceeding upload limit size
# get list of users from database USER_ID, EMAIL, MAX_UPLOAD_SIZE_GB
import pymysql.cursors
# Get the database name and password
passwordFile = os.path.expandvars("${SDK_VERSIONS}/db_password.txt")
properties = {}
pf = open(passwordFile, "r");
for line in iter(pf):
    s = line.split('=')
    properties[s[0].strip()] = s[1].strip()
conn = pymysql.connect(host=properties["host"], port=int(properties["port"]), user=properties["username"],
        passwd=properties["password"], db=properties["db"])
try:
    with conn.cursor() as cursor:
        sql = 'select USER_ID, EMAIL, MAX_UPLOAD_SIZE_GB from users'
        cursor.execute(sql)
        result = cursor.fetchone()
        user_id = result[0]
        email = result[1]
        maxsize = result[2]
        print(result)
        # I think userdata_dir.SIZE is in bytes.  It looks like the
        # particles.star file is indicated in the db, but SIZE reflects
        # all the uploaded files.  If the same directory is uploaded
        # multiple times, each upload gets an entry so the SUM will
        # be inflated...
        sql = """
            SELECT
                users.EMAIL,
                SUM(userdata_dir.SIZE)
            FROM users
            INNER JOIN userdata_dir ON users.USER_ID = userdata_dir.USER_ID
            WHERE
                users.USER_ID = %s
            GROUP BY users.USER_ID
        """ % (user_id)
        cursor.execute(sql)
        result = cursor.fetchall()
        if len(result) > 1:
            print('len(result) ({}) for result ({}) greater than one!\n'.format(len(result), result))
            raise Error
        datasize = result[0][1]
        if datasize > maxsize * 1024 * 1024 * 1024:
           print('datasize ({}) exceeds maxsize ({})\n'.format(datasize, maxsize * 1024 * 1024 * 1024))
           warn_list.append(
               "individual datadir max size exceeded with total (%s) bytes for (%s)\n" % (datasize, email)) 
        else:
           print('datasize ({}) does not exceed maxsize ({})\n'.format(datasize, maxsize * 1024 * 1024 * 1024))
finally:
    conn.close()

if len(warn_list) > 0:
    warntext = ''.join(warn_list)
    warn(warntext, 
         'high disk usage',
         MAILLIST)
else:
    #print "not warning"
    if time.localtime()[3] == 9:
        # 9am hour localtime, so email that datawarn ran with no warnings
        warn('no warnings\n', 'datawarn.py no warnings', MAILLIST)
    pass

