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
import zipfile 

def prepareRelionRun(inputline):
	tmplog=open('_tmplog','w')
	tmplog.write(inputline)
	
	inputZipFile=inputline.split()[returnEntryNumber(inputline,'--i')]
	#outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]
	
	pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
	usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
	ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

	tmplog.write(pwd+'\n'+ls+'\n'+usernamedir)
	userdir='/projects/cosmic2/gateway/globus_transfers/'+usernamedir
	tmplog.write('\n'+userdir)

	cmd='ln -s %s/%s %s/' %(userdir,inputZipFile.split('/')[0],pwd)
	tmplog.write('\n'+cmd)
	subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).wait()

	pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
        o1=open('_tmp2.txt','w')
        o1.write('%s\n'%(pwd))
        o1.close()

	tmplog.write(inputZipFile)

	if not os.path.exists('%s' %(inputZipFile)):
	#Print error since could not find input star file
		pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
		o1=open('_tmp1.txt','w')
		o1.write('%s\n'%(pwd))
		o1.close()	
		print 'Error=4' 
		sys.exit()
	
	#Get starfile name
	starfilename='%s' %(inputZipFile)

	#Create output dirs
	if '--ref' in inputline:
                if '--auto_refine' not in inputline:
                        os.makedirs('Class3D_cosmic')
                        os.makedirs('Class3D_cosmic/job001/')
                        outdir='Class3D_cosmic/job001'
			#Get num iters: 
			varcounter=0
			for variable in inputline.split():
        			if variable == '--iter':
                			iter_position=varcounter
        			varcounter=varcounter+1
			numiters=inputline.split()[iter_position+1]

	if '--ref' not in inputline:
                os.makedirs('Class2D_cosmic')
                os.makedirs('Class2D_cosmic/job001')
                outdir='Class2D_cosmic/job001'
		#Get num iters: 
                varcounter=0
                for variable in inputline.split():
	                if variable == '--iter':
         	               iter_position=varcounter
                        varcounter=varcounter+1
                numiters=inputline.split()[iter_position+1]

        if '--auto_refine' in inputline:
                os.makedirs('Refine3D_cosmic')
                os.makedirs('Refine3D_cosmic/job001')
                outdir='Refine3D_cosmic/job001'
		numiters=-1
	#Calculate number of nodes given number of lines in starfile
	numLines=len(open(starfilename,'r').readlines())
	nodes=1
	runtime=8
	if numLines > 10000:
		nodes=2		
		runtime=12
	if numLines > 20000:
                nodes=3
		runtime=12
	if numLines > 30000:
        	nodes=4   
		runtime=12    
	if numLines > 40000:
                nodes=5	
		runtime=18
	if numLines > 50000:
                nodes=6
		runtime=18
	if numLines > 60000:
                nodes=7
		runtime=24
	if numLines > 80000:
                nodes=8
		runtime=24
	if numLines > 100000:
                nodes=10
		runtime=24
	#>> Write number to scheduler.conf
	if not os.path.exists('scheduler.conf'):
		print 'Error=1'
		sys.exit()
	nodes=1
	outwrite=open('scheduler.conf','a')
	outwrite.write('nodes=%i\n' %(nodes))
	outwrite.close()

	#Replace zipfile name in relion command 
	inputline_list=inputline.split()
	inputline_list[returnEntryNumber(inputline,'--i')]=starfilename
	inputline_list[returnEntryNumber(inputline,'--o')]='%s/run' %(outdir)
	if inputline_list[returnEntryNumber(inputline,'--ref')].split('.')[-1] == 'map': 
		inputline_list[returnEntryNumber(inputline,'--ref')]=inputline_list[returnEntryNumber(inputline,'--ref')]+':mrc'

	#Join list into single string
        relion_command=' '.join(inputline_list)	
	return relion_command,outdir,runtime,nodes,numiters #print 'cmd="%s"' %(relion_command)

def returnEntryNumber(inputlist,queryString):
	'''Returns entry number in list for a given string in a list'''
	counter=1
	output=0
	for entry in inputlist.split(): 
		if entry == queryString:
			output=counter
		counter=counter+1
	return output

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

def createEpilog(self):
        rfile = open(lib.epilogue, "w") 
        text = """#!/bin/bash
        date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > %s/term.txt
        squeue -j $SLURM_JOB_ID -l >> %s/term.txt
        echo "This file created by srun of: $*" >> %s/term.txt
        """ % (lib.jobdir, lib.jobdir, lib.jobdir) 
        rfile.write(textwrap.dedent(text));
        rfile.close();
        os.chmod(lib.epilogue, 0744);

# Function to run the gateway_submit_attribute script
def runGSA ( gateway_user, jobid ):
        cmd = "/opt/ctss/gateway_submit_attributes/gateway_submit_attributes -gateway_user %s -submit_time \"`date '+%%F %%T %%:z'`\" -jobid %s" % ("%s@cosmic2.sdsc.edu" % gateway_user, jobid)
        log("./_JOBINFO.TXT", "\ngateway_submit_attributes=%s\n" % cmd)
        p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
        output =  p.communicate()[0]
        retval = p.returncode
        log("./_JOBINFO.TXT", "\ngateway_submit_attributes retval (%s) output (%s)\n" % (retval,output))
	return retval 


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
	log("./_JOBINFO.TXT","\nChargeFactor=1.0\nncores=%s" %(job_properties['cores']))
	runGSA ( gatewayuser, jobid )
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
args['account'] = 'csd547'

pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
o1=open('_tmp2.txt','w')
o1.write('%s\n'%(pwd))
o1.close()
os.system('pwd')
properties_dict = getProperties('./scheduler.conf')
#runhours = float(properties_dict['runhours'])
#nodes = int(properties_dict['nodes']) ###>> Nodes are calculated in this script  - see prepareRelionRun
#ntaskspernode = int(properties_dict['cores_per_node'])
ntaskspernode = int(properties_dict['ntasks-per-node'])
#fname = properties_dict['fname']
jobdir = os.getcwd()
relion_command,outdir,runhours,nodes,numiters=prepareRelionRun(args['commandline'])
runminutes = math.ceil(60 * runhours)
hours, minutes = divmod(runminutes, 60)
runtime = "%02d:%02d:00" % (hours, minutes)
#nodes = int(properties_dict['nodes'])
ntaskspernode = int(properties_dict['ntasks-per-node'])
o1=open('_JOBINFO.TXT','a')
o1.write('\ncores=%i\n' %(nodes*ntaskspernode))
o1.close()
shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
print jobproperties_dict
print "jobfinfo JobHandle (%s)" % jobproperties_dict['JobHandle']
mailuser = jobproperties_dict['email']
jobname = jobproperties_dict['JobHandle']
for line in open('_JOBINFO.TXT','r'):
	if 'User\ Name=' in line: 
		username=line.split('=')[-1].strip()

#Prepare relion command, unzip file, calculate number of nodes
#relion_command=prepareRelionRun(args['commandline'])
#nodes = int(properties_dict['nodes'])
ntaskspernode = int(properties_dict['ntasks-per-node'])
text = """#!/bin/sh
#SBATCH -o scheduler_stdout.txt    # Name of stdout output file(%%j expands to jobId)
#SBATCH -e scheduler_stderr.txt    # Name of stderr output file(%%j expands to jobId)
#SBATCH --partition=%s           # submit to the 'large' queue for jobs > 256 nodes
#SBATCH -J %s        # Job name
#SBATCH -t %s         # Run time (hh:mm:ss) - 1.5 hours
#SBATCH --mail-user=%s
#SBATCH --mail-type=begin
#SBATCH --mail-type=end
##SBATCH --qos=nsg
#The next line is required if the user has more than one project
# #SBATCH -A A-yourproject  # Allocation name to charge job against
#SBATCH -A %s  # Allocation name to charge job against
#SBATCH --nodes=%i  # Total number of nodes requested (16 cores/node)
#SBATCH --ntasks-per-node=%i             # Total number of mpi tasks requested
##SBATCH --gres=gpu:p100:4    #only p100 nodes

export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
module load python
module load gsl
module load %s
source $HOME/.bashrc
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/monitor_relion_job.py %s %s $SLURM_JOBID %s
ibrun -np 5 --tpr 4 %s --j 4 1>>stdout.txt 2>>stderr.txt
/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s %s
""" \
% \
('compute',jobname, runtime, mailuser, args['account'], 1,24,'relion/2.0.3',jobdir,outdir.split('_cosmic')[0],outdir,numiters,relion_command,username,outdir)

#P100: relion/2.1.b1_p100
#K80: relion/2.1.b1
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

# Following output to done.txt is needed by the gateway framework
#`date +'%s %a %b %e %R:%S %Z %Y' > done.txt`
#echo "retval=$rc">> done.txt
donefile = "done.txt"
#d = subprocess.Popen("date +'%s %a %b %e %R:%S %Z %Y", shell=True, stdout=subprocess.PIPE)
d=subprocess.Popen("date +'%s %a %b %e %R:%S %Z %Y'", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
FO = open(donefile, mode='a')
FO.write(d+"\n")
FO.write("retval=" + str(rc) + "\n")
FO.flush()
os.fsync(FO.fileno())
FO.close()

