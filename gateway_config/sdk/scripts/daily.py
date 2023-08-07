#!/usr/bin/env python
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
import sys
import tempfile
import subprocess
import shlex
import re

GTD = "${database.globusRoot}"
RCFILE = "${expanse.rc}"
WARNBYTES = ${user.data.size.warn}
MAILLIST = "${email.adminAddr}"
MAILX = os.popen('which sendmail').read().strip()
ECHO = os.popen('which echo').read().strip()
DU = os.popen('which du').read().strip()
CAT = os.popen('which cat').read().strip()
SSH = os.popen('which ssh').read().strip()
# check to see if MAILX, ECHO and DU got populated.
if MAILX == '' or ECHO == '' or DU == '' or CAT == '':
    print('couldn\'t find echo, du, cat or mailx')
    sys.exit(1)
# would like to check to see if they are valid commands.
# how to deal with shell aliases? aliases seem to not get
# picked up by which...
# in warn(), there will probably be stderr output that
# would get emailed by cron...

def runit(command):
    args = shlex.split(command)
    child_obj = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    #print(f'waiting for {command} to complete...\n')
    rc = child_obj.wait()
    (stdout, stderr) = child_obj.communicate()
    #print(f'{command} completed\n')
    #print(f' rc: {rc}\n')
    #print(f' stdout: {stdout}\n')
    return(stdout, stderr, rc)

def warn(message, subject, recipient) :
    FO = tempfile.NamedTemporaryFile(mode='w', delete=True)
    FO.write(f'Subject: {subject}\n')
    FO.write(message)
    FO.write('\n.\n')
    FO.flush()
    cmd = CAT + ' ' + "'%s'" % FO.name + ' | ' + MAILX + '  '+ " %s" % recipient
    os.system(cmd)
    FO.close()

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

command = f'{DU} --block-size=1 -s {GTD}'
return_tuple = runit(command)
globususage_raw = return_tuple[0]
rc = return_tuple[2]
perm_pat = f'/usr/bin/du: cannot read directory \'(?P<path>[^\']*)\': Permission denied'
perm_reo = re.compile(perm_pat)
#print(f'perm_pat: ({perm_pat})\n')
if not rc == 0:
    print(f'du rc: {rc}\n')
    print(f'stderr: {return_tuple[1]}\n')
    for line in return_tuple[1].decode().split('\n'):
        print(f'line: {line}\n')
        perm_mo = perm_reo.match(line)
        if perm_mo == None:
            print(f'not matched\n')
        else:
            print(f"matched with path: {perm_mo.group('path')}\n")
            # try to ssh chmod.sh the dir
            command = f'{SSH} -l cosmic2 login.expanse.sdsc.edu ". {RCFILE}; chmod.sh \'/expanse{perm_mo.group("path")}\'"'
            return_tuple = runit(command)
            print(f'return_tuple for {command}: {return_tuple}\n')
command = f'{DU} --block-size=1 -s {GTD}'
return_tuple = runit(command)
globususage_raw = return_tuple[0]

globususage_bytes = int(globususage_raw.split()[0])
globususage_gb = globususage_bytes // (1024 * 1024 *1024)
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
        usage_raw = os.popen("du --block-size=1 -s %s" % fullpath).read()
        #usage_bytes = int(string.split(usage_raw)[0])
        usage_bytes = int(usage_raw.split()[0])
        usage_gb = usage_bytes // (1024 * 1024 *1024)
        userusage_dict[fullpath] = usage_gb
        #print "usage_raw (%s) usage_bytes (%s)" % (usage_raw, usage_bytes)
        if usage_bytes > WARNBYTES:
            warn_list.append(
                "high disk usage (%sGB) for (%s)\n" %
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
        #    jobcount per user (last 24 hours)
        sql = "select job_stats.EMAIL, count(job_stats.jobhandle) from job_stats where job_stats.DATE_SUBMITTED >= '{}' group by job_stats.EMAIL;".format(yesterday.isoformat())
        #print('sql: ({})'.format(sql))
        cursor.execute(sql)
        results = cursor.fetchall()
        jobcountuser24h_dict = {}
        for result in results:
            jobcountuser24h_dict[result[0]] = result[1]
            #print('result ({})'.format(result))
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
        sql = "select SUM(COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0))))  * COALESCE(resource_conversion.CONVERSION, 1)) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '2016-07-01';"
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            SUcum = result[0]
            #print(result)
        #print(SUcum)
        #    SU usage (last 24 hours)
        sql = "select SUM(COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0)))) * COALESCE(resource_conversion.CONVERSION, 1)) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '{}';".format(yesterday.isoformat())
        #print('sql: ({})'.format(sql))
        cursor.execute(sql)
        results = cursor.fetchall()
        for result in results:
            SU24h = result[0]
            #print('result ({})'.format(result))
            #print(result)
        #print(SU24h)
        #    SU per user (last 24 hours)
        sql = "select job_stats.EMAIL, SUM(COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0)))) * COALESCE(resource_conversion.CONVERSION, 1)) from job_stats INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID where job_stats.DATE_SUBMITTED >= '{}' group by job_stats.EMAIL;".format(yesterday.isoformat())
        #print('sql: ({})'.format(sql))
        cursor.execute(sql)
        results = cursor.fetchall()
        SUuser24h_dict = {}
        for result in results:
            SUuser24h_dict[result[0]] = result[1]
            #print('result ({})'.format(result))
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

expanseallocation = runit('expanseallocation.sh')

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
    message = message + expanseallocation[0].decode() + '\n' + expanseallocation[1].decode() + '\n'
    message = message + 'SU usage (cumulative) {}\n'.format(SUcum)
    message = message + 'SU usage (last 24 hours) {}\n'.format(SU24h)
    message = message + 'SU per user (last 24 hours)\n'
    for item in SUuser24h_dict.items():
        message = message + '    {} {}\n'.format(item[0], item[1])
    message = message + 'Number of jobs submitted (cumulative) {}\n'.format(jobcountcum)
    message = message + 'Number of jobs submitted (last 24 hours) {}\n'.format(jobcount24h)
    message = message + 'Number of jobs per user (last 24 hours)\n'
    for item in jobcountuser24h_dict.items():
        message = message + '    {} {}\n'.format(item[0], item[1])
    message = message + 'Number of users (cumulative) {}\n'.format(usercountcum)
    message = message + 'Number of new users (last 24 hours) {}\n'.format(usercount24h)
    message = message + 'Disk space usage (cumulative) {} GB of globus transfer dir usage\n'.format(globususage_gb)
    message = message + 'Disk space per user (cumulative) GB\n'
    for item in userusage_dict.items():
        message = message + '    {} {}\n'.format(item[0], item[1])
    #print(message)
    warn(message, subject, MAILLIST)
