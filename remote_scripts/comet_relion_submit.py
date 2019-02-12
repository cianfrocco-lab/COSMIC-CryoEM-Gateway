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
import linecache 

def preparePreprocessingRun(inputline):

	#Get information from commandline:
	#Example: --bfactor 100 --ctf_res_lim 6  --cs 2.7 --do_motion --out_box 0 --tile_frames 5 --apix 1 --diameter 200 --movie_binning 1  --out_apix 0 --kev 300 --i mic_upload_test/micrographs.star 

	#Input files
	input_starfile=inputline.split()[returnEntryNumber(inputline,'--i')]

	#Align movies
	movie_align=False
	if '--do_motion' in inputline: 
		movie_align=True
	if movie_align is True:
		movie_dose_weighting=False 
		if '--dose' in inputline: 
			movie_dose_weighting=True
		if movie_dose_weighting is True:
			movie_dose_per_frame=float(inputline.split()[returnEntryNumber(inputline,'--dose')])
		if movie_dose_weighting is False:
			movie_dose_per_frames=1
		movie_binning=int(inputline.split()[returnEntryNumber(inputline,'--movie_binning')])
		movie_bfactor=int(inputline.split()[returnEntryNumber(inputline,'--bfactor')])
		movie_tiles=int(inputline.split()[returnEntryNumber(inputline,'--tile_frames')])
		if '--gain_ref' in inputline: 
			movie_gain_reference=inputline.split()[returnEntryNumber(inputline,'--gain_ref')]

	#Get CTF info: 
	ctf_kev=int(inputline.split()[returnEntryNumber(inputline,'--kev')])
	ctf_cs=float(inputline.split()[returnEntryNumber(inputline,'--cs')])
	ctf_reslim=int(inputline.split()[returnEntryNumber(inputline,'--ctf_res_lim')])
	angpix=float(inputline.split()[returnEntryNumber(inputline,'--apix')])

	#Particle stack values	
	particle_diameter_angstroms=int(inputline.split()[returnEntryNumber(inputline,'--diameter')]) #Angstroms
	particle_output_angpix=float(inputline.split()[returnEntryNumber(inputline,'--out_apix')]) #angpix
	if particle_output_angpix == 0: 
		particle_output_angpix=angpix
	particle_output_boxsize=int(inputline.split()[returnEntryNumber(inputline,'--out_box')]) #pixels, unbinned
	if particle_output_boxsize==0: 
		particle_output_boxsize=round(2*particle_diameter_angstroms/angpix)
		#Check if even dimensions
		if particle_output_boxsize%2==1: 
			particle_output_boxsize=particle_output_boxsize+1
	
	#DEBUGGING parameter parsing
	o1=open('_preprocess.txt','w')
	o1.write('preprocessing info\n')
	o1.write('movie align=%s\n' %(movie_align))
	if movie_align is True:
		if movie_dose_weighting is True: 
			o1.write('movie_dose_per_frame=%f\n' %(movie_dose_per_frame))
		o1.write('movie_binning=%i\n' %(movie_binning))
		o1.write('movie_bfactor=%i\n' %(movie_bfactor))
		o1.write('movie_tiles=%i\n' %(movie_tiles))
		if '--gain_ref' in inputline:
			o1.write('gain_ref=%s\n' %(movie_gain_reference))
	o1.write('ctf_kev=%i\n' %(ctf_kev))
	o1.write('ctf_cs=%f\n' %(ctf_cs))
	o1.write('ctf_reslim=%i\n' %(ctf_reslim))
	o1.write('angpix=%f\n' %(angpix))
	o1.write('particle_diameter_angstroms=%i\n' %(particle_diameter_angstroms))
	o1.write('particle_output_angpix=%f\n' %(particle_output_angpix))
	o1.write('particle_output_boxsize=%i\n' %(particle_output_boxsize))
	o1.write('\n\nFinished\n')

	#Movie align command generation
	movie_align_cmd=''
	if movie_align is True: 
		#Define output directory
		if not os.path.exists('MotionCorr_cosmic2'): 
			os.makedirs('MotionCorr_cosmic2')
		counter=1
		while counter<1000:
			if not os.path.exists('MotionCorr_cosmic2/job%03i' %(counter)): 
				movie_outdir='MotionCorr_cosmic2/job%03i' %(counter)
				os.makedirs('MotionCorr_cosmic2/job%03i' %(counter))
				counter=10001
			counter=counter+1
		movie_align_cmd='relion_run_motioncorr_mpi --i %s --o %s --first_frame_sum 1 --last_frame_sum -1 --use_own  --j 1 --bin_factor %i --bfactor %i --angpix %i --voltage %i --dose_per_frame %i --preexposure 0 --patch_x %i --patch_y %i --gain_rot 0 --gain_flip 0' %(input_starfile,movie_outdir,movie_binning,movie_bfactor,angpix,ctf_kev,movie_dose_per_frame,movie_tiles,movie_tiles)

		#Add other options here: gain reference, dose weighting
		if '--gain_ref' in inputline:
			movie_align_cmd=movie_align_cmd+' --gainref %s' %(movie_gain_reference)
		if movie_dose_weighting is True: 
			movie_align_cmd=movie_align_cmd+' --dose_weighting  --save_noDW'
		
		o1.write('movie_align_cmd:\n')
		o1.write('%s\n' %(movie_align_cmd))

	#Generate CTF command
	if movie_align is True:
		input_mic_file='%s/corrected_micrographs.star' %(movie_outdir)
	if movie_align is False: 
		input_mic_file=input_starfile
	if not os.path.exists('CtfFind_cosmic2'):
	        os.makedirs('CtfFind_cosmic2')
        counter=1
        while counter<1000:
        	if not os.path.exists('CtfFind_cosmic2/job%03i' %(counter)):
                	ctf_outdir='CtfFind_cosmic2/job%03i' %(counter)
                        os.makedirs('CtfFind_cosmic2/job%03i' %(counter))
                        counter=10001
                counter=counter+1
	ctf_cmd='relion_run_ctffind_mpi --i %s --o %s --CS %f --HT %i --AmpCnst 0.1 --XMAG 10000 --DStep %f --Box 512 --Resmin 30 --ResMax 5 --dFMin 5000 --dFMax 50000 --FStep 500 --dAst 100 --ctffind_exe /home/cosmic2/software_dependencies/ctffind-4.1.13/ctffind --ctfWin -1 --is_ctffind4 --fast_search' %(input_mic_file,ctf_outdir,ctf_cs,ctf_kev,angpix)

	if movie_dose_weighting is True: 	
		ctf_cmd=ctf_cmd+'   --use_noDW '

	o1.write('\n\nctf_cmd:\n')
	o1.write('%s\n' %(ctf_cmd))

	#Generate crYOLO picking command

	picking_cmd=''
	extraction_cmd=''

	o1.close()

	return movie_align_cmd,ctf_cmd,picking_cmd,extraction_cmd


def prepareRelionRun(inputline):
	#Start temp log file
	tmplog=open('_tmplog','w')
	tmplog.write(inputline)
	partition='gpu'
	nodes=1
	gpuextra1='#SBATCH --gres=gpu:k80:4\n'
	gpuextra2='--gpu 0,1,2,3'
	gpuextra3='relion/3.0_beta_gpu'
	#Get input file
	inputZipFile=inputline.split()[returnEntryNumber(inputline,'--i')]
	#outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]
	
	#Get current working directory on comet
	pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()

	#Get username
	usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
	ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

	#Get userdirectory data and write to log file
	tmplog.write(pwd+'\n'+ls+'\n'+usernamedir)
	userdir='/projects/cosmic2/gateway/globus_transfers/'+usernamedir
	tmplog.write('\n'+userdir)

	#Get full path to starfile on cosmic
        starfilename=userdir+'/'+'%s' %(inputZipFile)
	tmplog.write('\n'+'starfilename:   '+starfilename)

	#Get relative path from starfile 
	##Read star file header to get particle name
        rlno1=open(starfilename,'r')
	colnum=-1
        for rln_line in rlno1:
                if '_rlnImageName' in rln_line:
                        colnum=int(rln_line.split()[-1].split('#')[-1])-1
        rlno1.close()

	if colnum<0:
		print "Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting"
		sys.exit()

	rlno1=open(starfilename,'r')
        for rln_line in rlno1:
                if len(rln_line) >= 40:
                        starfiledirname=rln_line.split()[colnum].split('@')[-1].split('/')
                        del starfiledirname[-1]
                        del starfiledirname[-1]
			starfiledirname='/'.join(starfiledirname)
        rlno1.close()

	newstarname='%s'%(starfiledirname)+'/'+'%s'%(inputZipFile.split('/')[-1])
	tmplog.write('\nnewstarname='+newstarname+'\n')

	workingStarDirName=starfilename
	fullStarDir=starfilename.split('/')
	del fullStarDir[-1]
	fullStarDir='/'.join(fullStarDir)
	counter=1
	while counter<=len(starfiledirname.split('/')):
        	checkDir=starfiledirname.split('/')[-counter]
        	if fullStarDir.split('/')[-1] == checkDir:
                	fullStarDir=fullStarDir.split('/')
                	del fullStarDir[-1]
                	fullStarDir='/'.join(fullStarDir)
        	counter=counter+1

	#Symlink directory: 
	DirToSymLink=fullStarDir
	tmplog.write('\n'+'symlink'+DirToSymLink)
	cmd='ln -s %s/* .' %(DirToSymLink)
	subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()
	
	pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
        o1=open('_tmp2.txt','w')
        o1.write('%s\n'%(pwd))
        o1.close()

	if not os.path.exists('%s' %(newstarname)):
	#Print error since could not find input star file
		pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
		o1=open('_tmp1.txt','w')
		o1.write('%s\n'%(pwd))
		o1.write('could not find %s' %(newstarname))
		o1.close()	
		print 'Could not find input star file' 
		sys.exit()
	
	#Create output dirs
	if '--ref' in inputline:
                partition='compute'
		gpuextra1=''
		gpuextra2=''
		gpuextra3='relion/3.0_beta_cpu'
		if '--auto_refine' not in inputline:
                	partition='gpu'
			gpuextra1='#SBATCH --gres=gpu:k80:4\n'
        		gpuextra2='--gpu 0,1,2,3'
        		gpuextra3='relion/3.0_beta_gpu'        
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
		if '--denovo_3dref' not in inputline: 
			if not os.path.exists('Class2D_cosmic'):
	                	os.makedirs('Class2D_cosmic')
        		counter=1
			while counter<=500: 
				if not os.path.exists('Class2D_cosmic/job%03i' %(counter)): 
			        	os.makedirs('Class2D_cosmic/job%03i' %(counter))
		                	outdir='Class2D_cosmic/job%03i' %(counter)
					counter=10000
				counter=counter+1
			#Get num iters: 
                	varcounter=0
                	for variable in inputline.split():
	                	if variable == '--iter':
         	               		iter_position=varcounter
                        	varcounter=varcounter+1
                	numiters=inputline.split()[iter_position+1]
		if '--denovo_3dref' in inputline: 
			if not os.path.exists('InitialModel_cosmic'):
                                os.makedirs('InitialModel_cosmic')
                        counter=1
                        while counter<=500:
                                if not os.path.exists('InitialModel_cosmic/job%03i' %(counter)):
                                        os.makedirs('InitialModel_cosmic/job%03i' %(counter))
                                        outdir='InitialModel_cosmic/job%03i' %(counter)
                                        counter=10000
                                counter=counter+1
			numiters=-1
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
		runtime=12
	if numLines > 20000:
		runtime=12
	if numLines > 30000:
		runtime=12    
	if numLines > 40000:
		runtime=18
	if numLines > 50000:
		runtime=18
	if numLines > 60000:
		runtime=24
	if numLines > 80000:
		runtime=24
	if numLines > 100000:
		runtime=24
	mpi_to_use=4 
	if '--auto_refine' in inputline:
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
		mpi_to_use=nodes*4
	#>> Write number to scheduler.conf
	if not os.path.exists('scheduler.conf'):
		print 'Error=1'
		sys.exit()
	outwrite=open('scheduler.conf','a')
	outwrite.write('nodes=%i\n' %(nodes))
	outwrite.close()

	#Replace zipfile name in relion command 
	inputline_list=inputline.split()
	#tmplog.write('1:'+inputline_list+'\n')
	tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--i')]))
	inputline_list[returnEntryNumber(inputline,'--i')]=newstarname
	inputline_list[returnEntryNumber(inputline,'--o')]='relion_refine_mpi --o %s/run' %(outdir)
	tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--o')]))
	if inputline_list[returnEntryNumber(inputline,'--ref')].split('.')[-1] == 'map': 
		inputline_list[returnEntryNumber(inputline,'--ref')]=inputline_list[returnEntryNumber(inputline,'--ref')]+':mrc'

	#Join list into single string
        relion_command=' '.join(inputline_list)	
	tmplog.write(relion_command)	
	return relion_command,outdir,runtime,nodes,numiters,inputZipFile.split('/')[0],partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use #print 'cmd="%s"' %(relion_command)

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
        cmd = "/opt/ctss/gateway_submit_attributes/gateway_submit_attributes -resource comet-gpu.sdsc.xsede -gateway_user %s -submit_time \"`date '+%%F %%T %%:z'`\" -jobid %s" % ("%s@cosmic2.sdsc.edu" % gateway_user, jobid)
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

###################################
## Submission parser starts here###
###################################
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
os.system('pwd')
properties_dict = getProperties('./scheduler.conf')
ntaskspernode = int(properties_dict['ntasks-per-node'])
jobdir = os.getcwd()

######################################################################
######Split job into two types here: preprocessing vs RELION jobs#####
######################################################################

if 'pipeline' in args['commandline']: 
	jobtype='pipeline'

if 'relion_refine_mpi' in args['commandline']: 
	jobtype='relion'

if jobtype == 'relion': 
	relion_command,outdir,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use=prepareRelionRun(args['commandline'])
	runminutes = math.ceil(60 * runhours)
	hours, minutes = divmod(runminutes, 60)
	runtime = "%02d:%02d:00" % (hours, minutes)
	ntaskspernode = int(properties_dict['ntasks-per-node'])
	o1=open('_JOBINFO.TXT','a')
	o1.write('\ncores=%i\n' %(nodes*ntaskspernode))
	o1.close()
	shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
	jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
	mailuser = jobproperties_dict['email']
	jobname = jobproperties_dict['JobHandle']
	for line in open('_JOBINFO.TXT','r'):
		if 'User\ Name=' in line: 
			username=line.split('=')[-1].strip()
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
	#SBATCH --cpus-per-task=6
	#SBATCH --no-requeue
	%s
	export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
	export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
	module purge
	module load gnutools
	module load intel/2015.6.233
	module load intelmpi/2015.6.233
	module load %s
	date 
	export OMP_NUM_THREADS=6
	cd '%s/'
	date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
	/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/monitor_relion_job.py %s %s $SLURM_JOBID %s & 
	pwd > stdout.txt 2>stderr.txt
	mpirun -np %i %s --j 6 %s >>stdout.txt 2>>stderr.txt
	/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s %s
	/bin/tar -cvzf output.tar.gz %s/
	""" \
	%(partition,jobname, runtime, mailuser, args['account'], nodes,4,gpuextra1,gpuextra3,jobdir,outdir.split('_cosmic')[0],outdir,numiters,mpi_to_use,relion_command,gpuextra2,username,outdir,outdir)
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

if jobtype == 'pipeline':
        #relion_command,outdir,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use=preparePreprocessingRun(args['commandline'])
	movie_align_cmd,ctf_cmd,picking_cmd,extraction_cmd=preparePreprocessingRun(args['commandline'])
