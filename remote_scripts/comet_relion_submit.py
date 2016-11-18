#!/usr/bin/env python
#argv (['/projects/ps-nsg/home/nsguser/ngbwtest/contrib/scripts/submit.py', '--url', '-k http://nsgdev.sdsc.edu:8085/portal2/taskupdate.action?taskId=12\\&jh=NGBW-JOB-SOMEXSEDETOOL-F5A02A605B0A447AB811B21C65CB2AA8', '--', 'echo WORKING DIR `pwd`; echo ENV `env` ; date > sleep_time.txt ; sleep 10 ; date >> sleep_time.txt '])
import os
import sys
import re
import argparse
import subprocess
import string
import shutil
import math

def log(filename, message):
    f = open(filename, "a")
    f.write(message)
    f.close()

def getProperties(filename):
    propFile= file( filename, "rU" )
    propDict= dict()
    for propLine in propFile:
        propDef= propLine.strip()
        if len(propDef) == 0:
            continue
        if propDef[0] in ( '!', '#' ):
            continue
        punctuation= [ propDef.find(c) for c in ':= ' ] + [ len(propDef) ]
        found= min( [ pos for pos in punctuation if pos != -1 ] )
        name= propDef[:found].rstrip()
        value= propDef[found:].lstrip(":= ").rstrip()
        propDict[name]= value
    propFile.close()
    return propDict

def submitJob(job_properties={}, runfile='batch_command.run', statusfile='batch_comand.status',cmdfile='batch_command.cmdline'):
    log(statusfile, "submit_job start\n")
    #cmd = "qsub %s 2>> %s" % (runfile, statusfile)
    cmd = "sbatch %s 2>> %s" % (runfile, statusfile)
    #cmd = "echo 0000001 2>> %s" % (statusfile,)
    print "submit command (%s)" % (cmd,)
    p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
    print cmd
    #sys.exit()
    output =  p.communicate()[0]
    retval = p.returncode
    if retval != 0:
        # read whatever qsub wrote to the statusfile and print it to stdout
        print "Error submitting job:\n"
        f = open(statusfile, "r"); print f.read(), "\n\n"; f.close()
        print output
        # When we return 2 it means too many jobs are queued.  qstat returns -226 on abe
        # in this situation ... not sure if that's true here, on trestles as well.
        #if retval == -226:
        #if retval == 1:
        #    retval = 2
        if re.search('Too many simultaneous jobs in queue',output) != None:
            retval = 2

        log(statusfile, "submit_job is returning %d\n" %  retval)

        return retval
    log(statusfile, "sbatch output is: " + output + "\n" +
        "======================================================================" +  "\n")

    # output from qsub should on trestles is just the full job id, <id>.trestles-fe1.sdsc.edu:
    # output from stampede is a bunch of stuff.  job id line is:
    #Submitted batch job 4822650
    #p = re.compile(r"^(\d+).trestles.\S+", re.M)
    p = re.compile(r"^Submitted batch job (?P<jobid>\d+)\s*$", re.M)
    m = p.search(output)
    if m != None:
        jobid = m.group('jobid')
        short_jobid = m.group('jobid')
        print "jobid=%d" % int(short_jobid)
        log(statusfile, "JOBID is %s\n" % jobid)
        log("./_JOBINFO.TXT", "\nJOBID=%s\n" % jobid)
        log("./_JOBINFO.TXT", "\njob_properties (%s)\n" % (job_properties,))
        gatewayuser = string.split(job_properties['User\\'],'=')[1]
        log("./_JOBINFO.TXT", "\ngatewayuser (%s)\n" % (gatewayuser,))
        log("./_JOBINFO.TXT","\nChargeFactor=1.0\n")
        # gateway_submit_attributes is a post-submit script to record
        # which gateway user submitted this jobid
        cmd = "/opt/ctss/gateway_submit_attributes/gateway_submit_attributes -gateway_user %s -submit_time \"`date '+%%F %%T %%:z'`\" -jobid %s" % ("%s@nsgportal.org" % gatewayuser, jobid)
        log("./_JOBINFO.TXT", "\ngateway_submit_attributes=%s\n" % cmd)
        p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
        output =  p.communicate()[0]
        retval = p.returncode
        log("./_JOBINFO.TXT", "\ngateway_submit_attributes retval (%s) output (%s)\n" % (retval,output))
        return 0
    else:
        print "Error, sbatch says: %s" % output
        log(statusfile, "can't get jobid, submit_job is returning 1\n")
        return 1


parser = argparse.ArgumentParser()
parser.add_argument('--account')
parser.add_argument('--url')
parser.add_argument('--', dest='doubledash')
parser.add_argument('commandline')
args = vars(parser.parse_args())
args['account'] = 'hvd125'

os.system('pwd')
properties_dict = getProperties('./scheduler.conf')
runhours = float(properties_dict['runhours'])
nodes = int(properties_dict['nodes'])
ntaskspernode = int(properties_dict['cores_per_node'])
#fname = properties_dict['fname']
runminutes = math.ceil(60 * runhours)
hours, minutes = divmod(runminutes, 60)
runtime = "%02d:%02d:00" % (hours, minutes)
jobdir = os.getcwd()
shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
print jobproperties_dict
print "jobfinfo JobHandle (%s)" % jobproperties_dict['JobHandle']
mailuser = jobproperties_dict['email']
jobname = jobproperties_dict['JobHandle']

text = """#!/bin/sh
#SBATCH -o scheduler_stdout.txt    # Name of stdout output file(%%j expands to jobId)
#SBATCH -e scheduler_stderr.txt    # Name of stderr output file(%%j expands to jobId)
#SBATCH --partition=compute           # submit to the 'large' queue for jobs > 256 nodes
#SBATCH -J %s        # Job name
#SBATCH -t %s         # Run time (hh:mm:ss) - 1.5 hours
#SBATCH --mail-user=%s
#SBATCH --mail-type=begin
#SBATCH --mail-type=end
##SBATCH --qos=nsg
#The next line is required if the user has more than one project
# #SBATCH -A A-yourproject  # Allocation name to charge job against
#SBATCH -A %s  # Allocation name to charge job against
#SBATCH --nodes=%s  # Total number of nodes requested (16 cores/node)
#SBATCH --ntasks-per-node=%s             # Total number of mpi tasks requested
module load python
module load gsl
source $HOME/.bashrc

cd '%s'
ibrun -v %s 1>>stdout.txt 2>>stderr.txt
/bin/tar -cvzf output.tar.gz ./*
""" \
% \
(jobname, runtime, mailuser, args['account'], nodes, ntaskspernode, jobdir, args['commandline'])

runfile = "./batch_command.run"
statusfile = "./batch_command.status"
cmdfile = "./batch_command.cmdline"
debugfile = "./nsgdebug"

FO = open(runfile, mode='w')
FO.write(text)
FO.flush()
os.fsync(FO.fileno())
FO.close()

rc = submitJob(job_properties=jobproperties_dict, runfile='batch_command.run', statusfile='batch_command.status', cmdfile='batch_command.cmdline')

