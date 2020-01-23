#!/projects/cosmic2/kennethtest/datawarn/python3venv/bin/python
# /projects/cosmic2/gateway/globus_transfers
# 
# https://github.com/cianfrocco-lab/COSMIC-CryoEM-Gateway/issues/222
# Daily email with
#
#SUs:
#
#    SU usage (cumulative)
#    SU usage (last 24 hours)
#    SU per user (last 24 hours)
#
#Users:
#
#    Number of new users (cumulative)
#    Number of new users (last 24 hours)
#
#Disk space
#
#    Disk space usage (cumulative)
#    Disk space per user (cumultative)
#
#Jobs
#
#    Number of jobs submitted (cumulative)
#    Number of jobs submitted (last 24 hours)
#

import os
import string
import time
import datetime

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

# startdate of form: startOfPeriod = "2016-07-01"

#Jobs
#
#Users:
#
#    Number of new users (cumulative)
#    Number of new users (last 24 hours)

#Disk space
#

#    Disk space usage (cumulative)
globususage_raw = os.popen("du --block-size=1G -s %s" % GTD).read()
globususage_bytes = int(globususage_raw.split()[0])
#print('globususage_bytes ({})\n'.format(globususage_bytes))
#    Disk space per user (cumultative)
entry_list = os.listdir(GTD)
dir_list = []
warn_list = []
userusage_dict = {}
for entry_item in entry_list:
    fullpath = GTD + '/' + entry_item
    if os.path.isdir(fullpath):
        dir_list.append(fullpath)
        usage_raw = os.popen("du --block-size=1G -s %s" % fullpath).read()
        #usage_bytes = int(string.split(usage_raw)[0])
        usage_bytes = int(usage_raw.split()[0])
        userusage_dict[fullpath] = usage_bytes
        #print "usage_raw (%s) usage_bytes (%s)" % (usage_raw, usage_bytes)
        if usage_bytes * 1024 * 1024 * 1024 > WARNBYTES:
            warn_list.append(
                "high disk usage (%s) for (%s)\n" %
                   (usage_bytes, fullpath)) 
#print(userusage_dict)

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
        #    Number of jobs submitted (cumulative)
        sql = "select COUNT(job_stats.JOBHANDLE) from job_stats where job_stats.DATE_SUBMITTED >= '2016-07-01';"
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            jobcountcum = result[0]
            #print(result)
        #print(jobcountcum)
        #    Number of jobs submitted (last 24 hours)
        #    maybe easier to do jobs since yesterday...
        #    just as easy to to 24 hour timedelta as a one day timedelta
        delta24h = datetime.timedelta(hours=24)
        yesterday = datetime.date.today() - delta24h
        #print(yesterday.isoformat())
        sql = "select COUNT(job_stats.JOBHANDLE) from job_stats where job_stats.DATE_SUBMITTED >= '{}';".format(yesterday.isoformat())
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            jobcount24h = result[0]
            #print(result)
        #print(jobcount24h)
        #SUs:
        # for SU queries, should import usage.py and use
        # those functions...
        # usage.py is meant to be end user, does not return
        # query results to be further processed.
        # job_stats table has DATE_SUBMITTED field
        #
        #    SU usage (cumulative)
        sql = "select SUM(job_stats.SU_CHARGED * resource_conversion.CONVERSION) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '2016-07-01';"
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            SUcum = result[0]
            #print(result)
        #print(SUcum)
        #    SU usage (last 24 hours)
        sql = "select SUM(job_stats.SU_CHARGED * resource_conversion.CONVERSION) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '{}';".format(yesterday.isoformat())
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            SU24h = result[0]
            #print(result)
        #print(SU24h)
        #    SU per user (last 24 hours)
        sql = "select job_stats.EMAIL, SUM(job_stats.SU_CHARGED * resource_conversion.CONVERSION) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '{}' group by job_stats.EMAIL;".format(yesterday.isoformat())
        cursor.execute(sql)
        results = cursor.fetchall()
        SUuser24h_dict = {}
        for result in results:
            SUuser24h_dict[result[0]] = result[1]
            #print(result)
        #print(SUuser24h_dict)
        #Users:
        #
        #    Number of new users (cumulative)
        sql = "select COUNT(users.EMAIL) from users where users.DATE_CREATED >= '2016-07-01';"
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            usercountcum = result[0]
            #print(result)
        #print(usercountcum)
        #    Number of new users (last 24 hours)
        sql = "select COUNT(users.EMAIL) from users where users.DATE_CREATED >= '{}';".format(yesterday.isoformat())
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            usercount24h = result[0]
            #print(result)
        #print(usercount24h)
        sql = 'select USER_ID, EMAIL, MAX_UPLOAD_SIZE_GB from users'
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            user_id = result[0]
            email = result[1]
            maxsize = result[2]
            #print(result)
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
            emailsum = cursor.fetchall()
            if len(emailsum) == 0:
                # no userdata_dir entry found for that user?
                #print('userdata_dir not found for user ({})\n'.format(user_id))
                continue
            if len(emailsum) > 1:
                print('len(emailsum) ({}) for emailsum ({}) greater than one!\n'.format(len(emailsum), emailsum))
                raise Error
            #print('emailsum ({})\n'.format(emailsum))
            datasize = emailsum[0][1]
            if datasize > maxsize * 1024 * 1024 * 1024:
               print('datasize ({}) exceeds maxsize ({})\n'.format(datasize, maxsize * 1024 * 1024 * 1024))
               warn_list.append(
                   "individual datadir max size exceeded with total (%s) bytes for (%s)\n" % (datasize, email)) 
            else:
               #print('datasize ({}) does not exceed maxsize ({})\n'.format(datasize, maxsize * 1024 * 1024 * 1024))
               pass
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

# send daily report
if time.localtime()[3] == 9:
    # 9am hour localtime, so email that datawarn ran with no warnings
    subject = 'COSMIC2 daily report'
#SUs:
#
#    SU usage (cumulative)
#    SU usage (last 24 hours)
#    SU per user (last 24 hours)
#
#Users:
#
#    Number of new users (cumulative)
#    Number of new users (last 24 hours)
#
#Disk space
#
#    Disk space usage (cumulative)
#    Disk space per user (cumultative)
#
#Jobs
#
#    Number of jobs submitted (cumulative)
#    Number of jobs submitted (last 24 hours)
    message = ''
    message = message + 'SU usage (cumulative) {}\n'.format(SUcum)
    message = message + 'SU usage (last 24 hours) {}\n'.format(SU24h)
    message = message + 'SU per user (last 24 hours)\n'
    for item in SUuser24h_dict.items():
        message = message + '    {} {}\n'.format(item[0], item[1])
    message = message + 'Number of new users (cumulative) {}\n'.format(usercountcum)
    message = message + 'Number of new users (last 24 hours) {}\n'.format(usercount24h)
    message = message + 'Disk space usage (cumulative) {} GB of globus transfer dir usage\n'.format(globususage_bytes)
    message = message + 'Disk space per user (cumulative)\n'
    for item in userusage_dict.items():
        message = message + '    {} {}\n'.format(item[0], item[1])
    message = message + 'Number of jobs submitted (cumulative) {}\n'.format(jobcountcum)
    message = message + 'Number of jobs submitted (last 24 hours) {}\n'.format(jobcount24h)
    #print(message)
    warn(message, subject, MAILLIST)
