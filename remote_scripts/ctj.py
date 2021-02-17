#!/usr/bin/env python3
# use the API instead of xdusage
# https://xsede-xdcdb-api.xsede.org/
import os
import re
import sys
import datetime
import argparse
import urllib.request
#import ast
import json
import math

CHARGE_NUMBERS = ['TG-MCB170058',]

def getdatetime(datestring):
    # API date format: 2020-03-30T09:38:47.000-07:00
    #startdt = datetime("%Y-%m-%dT%H:%M:%S.000-07:00
    #startdt = datetime.strptime("%Y-%m-%dT%H:%M:%S.000%z", job_mo.group('starttime'))
    #mydt = datetime.datetime(2020,3,17)
    #mydt = new datetime.datetime(2020,3,17)
    #print(mydt.today())
    #startdt = datetime.strptime(job_mo.group('starttime'), "%Y-%m-%dT%H:%M:%S.000%z")
    # convert CUT offset to non-:
    cutoffset_mo = None
    #sys.stderr.write('job_mo.group(\'starttime\'): ({})\n'.format(job_mo.group('starttime')))
    #sys.stderr.write('job_mo.group(\'starttime\'): ({})\n'.format(datestring))
    #cutoffset_mo = cutoffset_reo.match(job_mo.group('starttime'))
    cutoffset_mo = cutoffset_reo.match(datestring)
    if cutoffset_mo == None:
        #sys.stderr.write('failed to match cutoffset pattern for ({})\n'.format(job_mo.group('starttime')))
        #sys.stderr.write('failed to match cutoffset pattern for ({})\n'.format(datestring))
        #sys.stderr.write(cutoffset_pat + '\n')
        return(None)
    else:
        cutoffset = cutoffset_mo.group('ca') + cutoffset_mo.group('cb')
        #sys.stderr.write('cutoffset ({})\n'.format(cutoffset))
        #startdt = mydt.strptime(cutoffset_mo.group('uncut') + cutoffset, "%Y-%m-%dT%H:%M:%S%z")
        startdt = datetime.datetime.strptime(cutoffset_mo.group('uncut') + cutoffset, "%Y-%m-%dT%H:%M:%S%z")
        #sys.stderr.write('startdt ({}) for starttime ({})\n'.format(startdt, job_mo.group('starttime')))
        # check for aware datetime object
        #sys.stderr.write('startdt.tzinfo ({}) startdt.tzinfo.utcoffset(startdt) ({})\n'.format(startdt.tzinfo, startdt.tzinfo.utcoffset(startdt)))
        return(startdt)


# main()

parser = argparse.ArgumentParser()
parser.add_argument('-j', '--jobs', action='store_true')
parser.add_argument('--username')
parser.add_argument('--begindate')
parser.add_argument('--enddate')
argspace = parser.parse_args()
#if 'username' in argspace:
#    username = argspace.username
#else:
#    sys.stderr.write('username not in argspace ({})\n'.format(argspace))
# convert cipres-tgusage --begindate to xdusage -s
# cipres-tgusage format:  +%m-%d-%Y
# xdusage -s format: %Y-%m-%d
cipresdf = '%m-%d-%Y%z'
# no TZ in cipres data spec, assume UTC
if 'begindate' in argspace:
    cipresbd = argspace.begindate + '+0000'
    begindt = datetime.datetime.strptime(cipresbd, cipresdf)
    #sys.stderr.write('cipresbd: ({}) begindt: ({})\n'.format(cipresbd, begindt))
    #sys.stderr.write('begindt.tzinfo ({}) begindt.tzinfo.utcoffset(begindt) ({})\n'.format(begindt.tzinfo, begindt.tzinfo.utcoffset(begindt)))
    xdstart = begindt.strftime('%Y-%m-%d')
    #sys.stderr.write('xdstart: ({})\n'.format(xdstart))
if 'enddate' in argspace:
    cipresbd = argspace.enddate + '+0000'
    enddt = datetime.datetime.strptime(cipresbd, cipresdf)
    #sys.stderr.write('enddt.tzinfo ({}) enddt.tzinfo.utcoffset(enddt) ({})\n'.format(enddt.tzinfo, enddt.tzinfo.utcoffset(enddt)))
    xdend = enddt.strftime('%Y-%m-%d')
# no local charge from xdusage?  set to huge:
#localcharge = None
#project_pat = 'Project: (?P<account>[^/]+)/(?P<resource>\S+)\s*'
#project_reo = re.compile(project_pat)
#job_pat = r"\s*job id=(?P<jobid>\S+) jobname=(?P<jobname>\S+) resource=(?P<resource.\S+) submit=2019-07-05T11:27:53.000-07:00 start=2019-07-05T11:27:54.000-07:00 end=2019-07-05T11:31:35.000-07:00 nodecount=1 processors=24 queue=gpu charge=1\s*"
#job_pat = '\s*job id=(?P<jobid>\S+) jobname=(?P<jobname>\S+) resource=(?P<resource>\S+) submit=(?P<submittime>\S+) start=(?P<starttime>\S+) end=(?P<endtime>\S+) nodecount=(?P<nodecount>\S+) processors=(?P<processors>\S+) queue=(?P<queue>\S+) charge=(?P<charge>\S+)\s*'
#job_reo = re.compile(job_pat)
cutoffset_pat = '(?P<uncut>\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d)\.\d\d\d(?P<ca>.\d\d):(?P<cb>\d\d)'
cutoffset_reo = re.compile(cutoffset_pat)
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j')
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -s {}')
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -u {} -s {} -e {}'.format(username, xdstart, xdend))
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -a -s {} -e {}'.format(xdstart, xdend))
# get apikey, assume ASCII, rather than UTF-8 that needs binary and decode()
APIKEYFO = open('/home/cosmic2/.xsede-gateway-attributes-apikey',mode='rt')
apikeystring = APIKEYFO.read().strip()
APIKEYFO.close()
for charge_number in CHARGE_NUMBERS:
    #print(charge_number)
    url_string = 'https://xsede-xdcdb-api.xsede.org/gateway/v2/jobs/by_proj_dates/{}/{}/{}?{}'.format(charge_number, xdstart, xdend, apikeystring)
    #print(url_string)
    urlobject = urllib.request.urlopen(url_string)
    #print(urlobject.read().decode('utf-8'))
    # this eval is a little sketchy, since it does not filter
    # input and it assumes the string from the urlopen is
    # valid python, except for 'null'.
    #null = None
    #response_dict = eval(urlobject.read().decode('utf-8'))
    # ast.literal_eval allows only strings, bytes, number, tuples,
    # lists, dicts, sets, booleans, None
    #urloutputnull = urlobject.read().decode('utf-8')
    #urloutputNone = urloutputnull.replace(':null,', ':None,')
    #print(urloutputNone)
    #response_dict = ast.literal_eval(urloutputNone)
    # Camille says the response is JSON, so should use that parsing...
    response_dict = json.load(urlobject)
    #print(response_dict)
    for result_dict in response_dict['result']:
        #print(result_dict)
        startdt = getdatetime(result_dict['start_time'])
        enddt = getdatetime(result_dict['end_time'])
        submitdt = getdatetime(result_dict['submit_time'])
        #jobtd = enddt - startdt
        wallseconds = result_dict['wallduration']
        #sys.stderr.write('jobtd: ({}) total_seconds: ({}) total_seconds/3600: ({}) formatted: ({:.2f})\n'.format(jobtd, jobtd.total_seconds(), jobtd.total_seconds()/3600, jobtd.total_seconds()/3600))
        #wallhours = '{:.2f}'.format(jobtd.total_seconds()/3600)
        wallhours = '{:.2f}'.format(wallseconds/3600)
        # resource,jobid,localcharge,starttime,endtime,submittime,account,wallhours,su,nodecount,processors,queue
        # local_charge and adjusted_charge are in float, not int.  Not sure
        # if that will break something
        uline = ' {},{},{},{},{},{},{},{},{},{},{},{}'.format(
                 result_dict['resource_name'],
                 result_dict['local_jobid'],
                 int(math.ceil(float(result_dict['local_charge']))),
                 startdt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 enddt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 submitdt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 result_dict['charge_number'],
                 wallhours,
                 int(math.ceil(float(result_dict['adjusted_charge']))),
                 result_dict['nodecount'],
                 result_dict['processors'],
                 result_dict['queue']
                 )
        print(uline)

sys.exit(0)

