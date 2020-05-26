#!/opt/python/bin/python3
import os
import re
import sys
import datetime
import argparse

def getdatetime(datestring):
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
        sys.stderr.write('failed to match cutoffset pattern for ({})\n'.format(datestring))
        sys.stderr.write(cutoffset_pat + '\n')
        return(None)
    else:
        cutoffset = cutoffset_mo.group('ca') + cutoffset_mo.group('cb')
        sys.stderr.write('cutoffset ({})\n'.format(cutoffset))
        #startdt = mydt.strptime(cutoffset_mo.group('uncut') + cutoffset, "%Y-%m-%dT%H:%M:%S%z")
        startdt = datetime.datetime.strptime(cutoffset_mo.group('uncut') + cutoffset, "%Y-%m-%dT%H:%M:%S%z")
        sys.stderr.write('startdt ({}) for starttime ({})\n'.format(startdt, job_mo.group('starttime')))
        # check for aware datetime object
        sys.stderr.write('startdt.tzinfo ({}) startdt.tzinfo.utcoffset(startdt) ({})\n'.format(startdt.tzinfo, startdt.tzinfo.utcoffset(startdt)))
        return(startdt)


# main()

parser = argparse.ArgumentParser()
parser.add_argument('-j', '--jobs', action='store_true')
parser.add_argument('--username')
parser.add_argument('--begindate')
parser.add_argument('--enddate')
argspace = parser.parse_args()
if 'username' in argspace:
    username = argspace.username
else:
    sys.stderr.write('username not in argspace ({})\n'.format(argspace))
# convert cipres-tgusage --begindate to xdusage -s
# cipres-tgusage format:  +%m-%d-%Y
# xdusage -s format: %Y-%m-%d
cipresdf = '%m-%d-%Y%z'
# no TZ in cipres data spec, assume UTC
if 'begindate' in argspace:
    cipresbd = argspace.begindate + '+0000'
    begindt = datetime.datetime.strptime(cipresbd, cipresdf)
    sys.stderr.write('cipresbd: ({}) begindt: ({})\n'.format(cipresbd, begindt))
    sys.stderr.write('begindt.tzinfo ({}) begindt.tzinfo.utcoffset(begindt) ({})\n'.format(begindt.tzinfo, begindt.tzinfo.utcoffset(begindt)))
    xdstart = begindt.strftime('%Y-%m-%d')
    sys.stderr.write('xdstart: ({})\n'.format(xdstart))
if 'enddate' in argspace:
    cipresbd = argspace.enddate + '+0000'
    enddt = datetime.datetime.strptime(cipresbd, cipresdf)
    sys.stderr.write('enddt.tzinfo ({}) enddt.tzinfo.utcoffset(enddt) ({})\n'.format(enddt.tzinfo, enddt.tzinfo.utcoffset(enddt)))
    xdend = enddt.strftime('%Y-%m-%d')
# no local charge from xdusage?  set to huge:
localcharge = 1000000000000
project_pat = 'Project: (?P<account>[^/]+)/(?P<resource>\S+)\s*'
project_reo = re.compile(project_pat)
#job_pat = r"\s*job id=(?P<jobid>\S+) jobname=(?P<jobname>\S+) resource=(?P<resource.\S+) submit=2019-07-05T11:27:53.000-07:00 start=2019-07-05T11:27:54.000-07:00 end=2019-07-05T11:31:35.000-07:00 nodecount=1 processors=24 queue=gpu charge=1\s*"
job_pat = '\s*job id=(?P<jobid>\S+) jobname=(?P<jobname>\S+) resource=(?P<resource>\S+) submit=(?P<submittime>\S+) start=(?P<starttime>\S+) end=(?P<endtime>\S+) nodecount=(?P<nodecount>\S+) processors=(?P<processors>\S+) queue=(?P<queue>\S+) charge=(?P<charge>\S+)\s*'
job_reo = re.compile(job_pat)
cutoffset_pat = '(?P<uncut>\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d)\.\d\d\d(?P<ca>.\d\d):(?P<cb>\d\d)'
cutoffset_reo = re.compile(cutoffset_pat)
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j')
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -s {}')
#FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -u {} -s {} -e {}'.format(username, xdstart, xdend))
FO = os.popen('/usr/local/xdusage-2.1-1/bin/xdusage -j -a -s {} -e {}'.format(xdstart, xdend))
myline = FO.readline()
while myline != '':
    # check for Project line
    project_mo = None
    project_mo = project_reo.match(myline)
    if project_mo == None:
        sys.stderr.write('project_mo == None for ({})\n'.format(myline))
    else:
        sys.stderr.write('project_mo != None for ({})\n'.format(myline))
        account = project_mo.group('account')
        sys.stderr.write('account set to ({})\n'.format(account))
    job_mo = None
    job_mo = job_reo.match(myline)
    if job_mo == None:
        sys.stderr.write('job_mo == None for ({})\n'.format(myline))
    else:
        sys.stderr.write('job_mo != None for ({})\n'.format(myline))
        # output cipres-tgusage line:
        # comet-gpu.sdsc.xsede,15239438,1,03/29/2018 10:10:18 PDT,03/29/2018 10:10:36 PDT,03/29/2018 08:11:15 PDT,TG-MCB170058,0.01,1,1,24,gpu
        # resource,jobid,localcharge,starttime,endtime,submittime,account,wallhours,su,nodecount,processors,queue
        # calculate wallhours
        startdt = getdatetime(job_mo.group('starttime'))
        enddt = getdatetime(job_mo.group('endtime'))
        submitdt = getdatetime(job_mo.group('submittime'))
        jobtd = enddt - startdt
        sys.stderr.write('jobtd: ({}) total_seconds: ({}) total_seconds/3600: ({}) formatted: ({:.2f})\n'.format(jobtd, jobtd.total_seconds(), jobtd.total_seconds()/3600, jobtd.total_seconds()/3600))
        wallhours = '{:.2f}'.format(jobtd.total_seconds()/3600)
        # resource,jobid,localcharge,starttime,endtime,submittime,account,wallhours,su,nodecount,processors,queue
        uline = ' {},{},{},{},{},{},{},{},{},{},{},{}'.format(
                 job_mo.group('resource'),
                 job_mo.group('jobid'),
                 localcharge,
                 startdt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 enddt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 submitdt.strftime("%m/%d/%Y %H:%M:%S %Z"),
                 account,
                 wallhours,
                 job_mo.group('charge'),
                 job_mo.group('nodecount'),
                 job_mo.group('processors'),
                 job_mo.group('queue')
                 )
        print(uline)
        
    myline = FO.readline()
        
FO.close()
