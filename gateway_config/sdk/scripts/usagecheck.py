#!/projects/cosmic2/kennethtest/dev/python3/install/bin/python3

# https://python-future.org/quickstart.html#if-you-are-writing-code-from-scratch
#from __future__ import (absolute_import, division,
#                        print_function, unicode_literals)
#from builtins import *
#from future_builtins import *

import sys
import subprocess
import re
import os
# https://www.a2hosting.com/kb/developer-corner/mysql/connecting-to-mysql-using-python
import pymysql
import os.path

# Maven subs:
xsede_us_su_limit = "${xsede_us_su_limit}"
#print('xsede_us_su_limit ({})'.format([xsede_us_su_limit,]))
#xsede_us_su_limit = 50
portal = "${build.portal.staticUrl}"
email = "${email.serviceAddr}"
cra = "${portal.name}"
# end Maven subs:

CPURESOURCE = 'COMET'
GPURESOURCE = 'comet-gpu'
CPULIMIT = 10000
#CPULIMIT = 1
GPULIMIT = 1000
#GPULIMIT = 1
lowsa_dict = {'CPULOWSA' : 400000, 'GPULOWSA' : 40000}
#lowsa_dict = {'CPULOWSA' : 500000, 'GPULOWSA' : 50000}
#./usage.py -t
#Su per user total query:
#
#        SELECT
#            users.username,
#            users.institution,
#            job_stats.user_id,
#            job_stats.email,
#            SUM(tgusage.su * resource_conversion.conversion)
#        FROM job_stats
#        INNER JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID
#        INNER JOIN tgusage ON job_stats.REMOTE_JOB_ID = tgusage.JOBID AND tgusage.RESOURCE = resource_conversion.RESOURCE
#        INNER JOIN users ON job_stats.USER_ID = users.USER_ID
#        WHERE
#            ABS(DATEDIFF(job_stats.date_submitted, tgusage.submit_time)) < 2
#     GROUP BY users.USERNAME, job_stats.EMAIL
#username        institution     user_id email   SUM(tgusage.su * resource_conversion.conversion)
#046f34e64f8b7dc1015072dc7aa30a1a@ucsd.edu       SDSC    3       kyoshimoto@ucsd.edu     84
ut_pat = r'^\s*(?P<user>\S+)\s+(?P<institution>\S+)\s+(?P<userid>\d+)\s+(?P<email>\S+)\s+(?P<totalsu>\d+)\s*$'
ut_reo = re.compile(ut_pat, flags=re.MULTILINE)
try:
    cp = subprocess.run(["usage.py", "-t"], capture_output=True, text=True, timeout=30)
except subprocess.TimeoutExpired:
    print('TimeoutExpired for usage.py -t')
    sys.exit(1)
utotal_list = ut_reo.findall(cp.stdout)
limit_exceeded = False
for t_tuple in utotal_list:
    if int(t_tuple[4]) > int(xsede_us_su_limit):
        limit_exceeded = True
        print(f'user {t_tuple[0]} has exceeded xsede_us_su_limit {xsede_us_su_limit} with total usage of {t_tuple[4]}!\n')

# check the databse for cpu and gpu usage.  Do not use the 14 conversion
# for gpu, since we are looking at resource-specific SUs.  These have
# already been converted into appropriate values for the resource,
# so we should compare show_accounts --gpu values and charged SU values
# before 14 conversion factor?
# from usage.py
passwordFile = os.path.expandvars("${SDK_VERSIONS}/db_password.txt")
#print('passwordFile ({})'.format([passwordFile,]))
# Get the database name and password
properties = {}
pf = open(passwordFile, "r");
for line in iter(pf):
    s = line.split('=')
    properties[s[0].strip()] = s[1].strip()

conn = pymysql.connect(host=properties["host"], port=int(properties["port"]), user=properties["username"],
    passwd=properties["password"], db=properties["db"])

query = """\
SELECT EMAIL, SUM(COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0)))) ) FROM job_stats LEFT JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID  WHERE job_stats.RESOURCE = \"{}\" and job_stats.TG_CHARGE_NUMBER = \"TG-MCB170058\" GROUP BY job_stats.USER_ID
""".format(CPURESOURCE)
#print(query)
cur = conn.cursor()
numrows = cur.execute(query)
#print('numrows ({})'.format(numrows))
for row in cur.fetchall():
    line = " ".join([str(field) for field in row])
    #print('line ({})'.format(line))
    for fieldindex in range(len(row)):
        #print('field ({})'.format(row[fieldindex]))
        if fieldindex == 1  and int(row[fieldindex]) > CPULIMIT:
            print('email ({}) has exceeded cpu SU limit ({})'.format(row[0], row[1]))
cur.close()

query = """\
SELECT EMAIL, SUM(COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0)))) ) FROM job_stats LEFT JOIN resource_conversion ON job_stats.RESRC_CONVRTN_ID = resource_conversion.ID  WHERE job_stats.RESOURCE = \"{}\" and job_stats.TG_CHARGE_NUMBER = \"TG-MCB170058\" GROUP BY job_stats.USER_ID
""".format(GPURESOURCE)
#print(query)
cur = conn.cursor()
numrows = cur.execute(query)
#print('numrows ({})'.format(numrows))
for row in cur.fetchall():
    #line = " ".join([str(field) for field in row])
    #print('line ({})'.format(line))
    for fieldindex in range(len(row)):
        #print('field ({})'.format(row[fieldindex]))
        if fieldindex == 1  and int(row[fieldindex]) > GPULIMIT:
            print('email ({}) has exceeded gpu SU limit ({})'.format(row[0], row[1]))
cur.close()

conn.close()

warnsa = False
for saname in lowsa_dict.keys():
    if saname == 'CPULOWSA':
        salimit = lowsa_dict[saname]
        saoption = ''
    elif saname == 'GPULOWSA':
        salimit = lowsa_dict[saname]
        saoption = '--gpu'
    else:
        print('failed to find expected saname in ({})'.format(saname))
        sys.exit(1)
    sa_pat = r'^cosmic2\s+(?P<project>\S+)\s+(?P<used>\d+)\s+(?P<available>\d+)\s+(?P<usedbyproj>\d+)\s*$'
    sa_reo = re.compile(sa_pat, flags=re.MULTILINE)
    try:
        cp = subprocess.run(["ssh", "-l", "cosmic2", "comet-ln2.sdsc.edu", "show_accounts", saoption], capture_output=True, text=True, timeout=30)
    except subprocess.TimeoutExpired:
        print('TimeoutExpired for ssh show_accounts')
        sys.exit(1)
    sa_list = sa_reo.findall(cp.stdout)
    #print(sa_list)
    if len(sa_list) > 0:
        sa_out = cp.stdout
        for t_tuple in sa_list:
            #print('t_tuple[2] ({}) salimit ({})'.format(t_tuple[2], salimit))
            if int(t_tuple[2]) <= int(salimit):
                warnsa = True
                print('Low allocation available detected!')
                print(sa_out)
    else:
        print('ssh show_accounts failed!')
        sys.exit(1)

if limit_exceeded == True or warnsa == True:
    try:
        cp = subprocess.run(["usage.py", "-u"], capture_output=True, text=True, timeout=30)
    except subprocess.TimeoutExpired:
        print('TimeoutExpired for usage.py -u')
        sys.exit(1)
    
    print(cp.stdout)
    #print(sa_out)
    # could check cp.returncode here
    
    #YEAR(tgusage.END_TIME)  MONTHNAME(tgusage.END_TIME)     USER_ID USERNAME       EMAIL    SUM(tgusage.SU * resource_conversion.CONVERSION)
    #2019    July    3       046f34e64f8b7dc1015072dc7aa30a1a@ucsd.edu       kyoshimoto@ucsd.edu     70
    #uu_pat = r'^\s*(?P<year>\d\d\d\d)\s+(?P<month>\S+)\s+\d+\s+(?P<user>\S+)\s+(?P<email>\S+)\s+(?P<monthsu>\d+)\s*$'
    #uu_reo = re.compile(uu_pat, flags=re.DEBUG|re.MULTILINE)
    #uu_reo = re.compile(uu_pat, flags=re.MULTILINE)
    #umonth_list = uu_reo.findall(cp.stdout)
    #print(umonth_list)



