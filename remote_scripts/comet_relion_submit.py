#!/usr/bin/env python
#argv (['/projects/ps-nsg/home/nsguser/ngbwtest/contrib/scripts/submit.py', '--url', '-k http://nsgdev.sdsc.edu:8085/portal2/taskupdate.action?taskId=12\\&jh=NGBW-JOB-SOMEXSEDETOOL-F5A02A605B0A447AB811B21C65CB2AA8', '--', 'echo WORKING DIR `pwd`; echo ENV `env` ; date > sleep_time.txt ; sleep 10 ; date >> sleep_time.txt '])
import os
import time 
import sys
import re
import argparse
import subprocess
import string
import shutil
import math
import zipfile 
import linecache 
import random 

GLOBUSTRANSFERSDIR = os.environ['GLOBUSTRANSFERSDIR']
REMOTESCRIPTSDIR = os.environ['REMOTESCRIPTSDIR']
TGUSAGEDIR = os.environ['TGUSAGEDIR']
def randomString(stringLength=40):
    """Generate a random string of fixed length """
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))

def prepareMicassess(inputline,jobdir):
	tmplog=open('_tmplog','w')
        tmplog.write("start of tmplog\n")
        tmplog.write(inputline + "\n")
        tmplog.write("inputline (%s)\n" % inputline)
	elements = string.split(inputline, '"')
        testargs = []
        for eindex in range(len(elements)):
            if eindex % 2 == 0:
                # eindex is even, so not double quoted, so split on whitespace
                testargs = testargs + elements[eindex].strip().split()
            else:
                # eindex is odd, so don't split on whitespace
                testargs.append(elements[eindex])
	inputZipFile = None
        for eindex in range(len(testargs)):
            if testargs[eindex] == '-i':
                inputZipFile = '%s' %(testargs[eindex + 1])
	    if testargs[eindex] == '-t':
		threshold='%s' %(testargs[eindex + 1])
        if inputZipFile == None:
            print "Error, could not parse inputZipFile in (%s)" % inputline
            log(statusfile, "can't get inputZipFile, submit_job is returning 1\n")
            return 1

        tmplog.write("inputZipFile (%s)\n" % inputZipFile)
        #outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]

        #Get current working directory on comet
        pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()

        #Get username
        usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
        ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

        #Get userdirectory data and write to log file
        tmplog.write(pwd+'\n'+ls+'\n'+usernamedir)
        userdir='%s/'%(GLOBUSTRANSFERSDIR)+usernamedir
        tmplog.write('\n'+userdir)

        #Get full path to starfile on cosmic
        starfilename=userdir+'/'+'%s' %(inputZipFile)
        tmplog.write('\n'+'starfilename:   '+starfilename)

	#starfilename=userdir+'/'+'%s' %(input_starfile)
        foldername='%s' %(inputZipFile.split('/')[0])

        tmplog.write('starfilename=%s\n' %(starfilename))
        tmplog.write('userdir=%s\n' %(userdir))

	rlno1=open(starfilename,'r')
        colnum=-1
        for rln_line in rlno1:
 	       if '_rlnMicrographName' in rln_line:
        	       if len(rln_line.split()) == 2:
                	       colnum=int(rln_line.split()[-1].split('#')[-1])-1
                       if len(rln_line.split()) == 1:
                               colnum=0
        rlno1.close()
        tmplog.write('reading rln col=%i' %(colnum))

        if colnum<0:
                print "Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting"
	        return 1
	rlno1=open(starfilename,'r')
        for rln_line in rlno1:
                if len(rln_line) >= 40:
                        starfiledirname=rln_line.split()[colnum]
                        starfiledirname=starfiledirname.split('/')
                        del starfiledirname[-1]
                        starfiledirname='/'.join(starfiledirname)
        rlno1.close()
        #newstarname='%s'%(starfiledirname)+'/'+'%s'%(inputZipFile.split('/')[-1])
        newstarname='%s'%(inputZipFile.split('/')[-1])
	tmplog.write('\nnewstarname='+newstarname+'\n')

        workingStarDirName=starfilename
        fullStarDir=starfilename.split('/')
        del fullStarDir[-1]
        fullStarDir='/'.join(fullStarDir)
        counter=1

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
        cmd="ln -s '%s/'* ." %(DirToSymLink)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()
	if newstarname[0] == '/':
		newstarname=newstarname[1:] 

	shutil.copyfile(starfilename,newstarname)

	return newstarname,threshold

def preparePreprocessingRun(inputline,jobdir):

	#Get information from commandline:
	# pipeline --cs 2.7 --apix .66 --kev 300 --i "testing pipeline/micrograph_uploading/micrographs.star"

	#DEBUGGING
	o1=open('_preprocess.txt','w')
	#Input files
	#input_starfile=inputline.split()[returnEntryNumber(inputline,'--i')]
	elements = string.split(inputline, '"')
        testargs = []
        for eindex in range(len(elements)):
            if eindex % 2 == 0:
                # eindex is even, so not double quoted, so split on whitespace
                testargs = testargs + elements[eindex].strip().split()
            else:
                # eindex is odd, so don't split on whitespace
                testargs.append(elements[eindex])
        input_starfile = None
	apix = None
	cs = None
	kev = None
        for eindex in range(len(testargs)):
            if testargs[eindex] == '--i':
                input_starfile = testargs[eindex + 1]
	    if testargs[eindex] == '--apix':
		apix=testargs[eindex + 1]
	    if testargs[eindex] == '--cs':
                cs=testargs[eindex + 1]
	    if testargs[eindex] == '--kev':
                kev=testargs[eindex + 1]
	if input_starfile == None:
            print "Error, could not parse input starfile in (%s)" % inputline
            log(statusfile, "can't get input starfile, submit_job is returning 1\n")
            return 1
	
	#Get current working directory on comet
        pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
	usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
        ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

        #Get userdirectory data and write to log file
        userdir=GLOBUSTRANSFERSDIR+'/'+usernamedir

        #Get full path to starfile on cosmic
        starfilename=userdir+'/'+'%s' %(input_starfile)
	foldername='%s' %(input_starfile.split('/')[0])

	o1.write('starfilename=%s\n' %(starfilename))
	o1.write('userdir=%s\n' %(userdir))
        
	rlno1=open(starfilename,'r')
        colnum=-1
        for rln_line in rlno1:
        	if '_rlnMicrographName' in rln_line:
                	if len(rln_line.split()) == 2:
                        	colnum=int(rln_line.split()[-1].split('#')[-1])-1
                        if len(rln_line.split()) == 1:
                                colnum=0
        rlno1.close()
        o1.write('reading rln col=%i' %(colnum))

        if colnum<0:
        	print "Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting"
                return 1

	rlno1=open(starfilename,'r')
	for rln_line in rlno1:
        	if len(rln_line) >= 40:
                	starfiledirname=rln_line.split()[colnum]
                        starfiledirname=starfiledirname.split('/')
			del starfiledirname[-1]
        	        starfiledirname='/'.join(starfiledirname)
	rlno1.close()

        newstarname='%s'%(starfiledirname)+'/'+'%s'%(input_starfile.split('/')[-1])
	o1.write('\nnewstarname='+newstarname+'\n')

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
	o1.write('\n'+'symlink'+DirToSymLink)
        cmd="ln -s '%s/'* ." %(DirToSymLink)
	subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()

	#Copy starfile to current directory
	shutil.copyfile(starfilename,newstarname.split('/')[-1])

	#Get email
	jobproperties_dict = getProperties('_JOBINFO.TXT')
        mailuser = jobproperties_dict['email']

	#Format pipeline.sh script
	with open(os.path.join('/home/cosmic2/software_dependencies/Automatic-preprocessing-COSMIC2/', 'pipeline.sh'), 'r') as f:
	        with open(os.path.join(pwd, 'pipeline.sh'), 'w') as new_f:
        	    for line in f:
                	new_line = line.replace('$$input_dir', starfiledirname)\
	                .replace('$$input_star', newstarname.split('/')[-1])\
        	        .replace('$$user_email', mailuser)\
                	.replace('$$CS', cs)\
	                .replace('$$HT', kev)\
        	        .replace('$$apix', apix)\
                	.replace('$$final_apix', apix)
	                new_f.write(new_line)
	return DirToSymLink


def prepareRelionRun(args):
	#Start temp log file
        inputline = args['commandline']
	tmplog=open('_tmplog','w')
        tmplog.write("start of tmplog\n")
	tmplog.write(inputline + "\n")
	tmplog.write("inputline (%s)\n" % inputline)
	partition='gpu'
	nodes=1
	gpuextra1='#SBATCH --gres=gpu:p100:4\n'
	gpuextra2='--gpu 0:1:2:3'
	gpuextra3='relion/3.0.5_gpu'
	#Get input file
	#inputZipFile=inputline.split()[returnEntryNumber(inputline,'--i')].strip('"')
        elements = string.split(inputline, '"')
        testargs = []
        for eindex in range(len(elements)):
            if eindex % 2 == 0:
                # eindex is even, so not double quoted, so split on whitespace
                testargs = testargs + elements[eindex].strip().split()
            else:
                # eindex is odd, so don't split on whitespace
                testargs.append(elements[eindex])

	#tmplog.write("testargs (%s)\n" % testargs)
        #ifparser = argparse.ArgumentParser()
        #tmplog.write("after ArgumentParser()\n")
        #ifparser.add_argument('--i')
        #tmplog.write("after add_argument()\n")
        ##ifargs = vars(ifparser.parse_args(testargs))
        #ifargs = ifparser.parse_args(testargs)
        #tmplog.write("after parse_args()\n")
        #tmplog.write("ifargs (%s)\n" % (ifargs,))
	#inputZipFile=ifargs['--i'].strip('"')
        inputZipFile = None
        for eindex in range(len(testargs)):
            if testargs[eindex] == '--i':
                inputZipFile = testargs[eindex + 1]
        if inputZipFile == None:
            print "Error, could not parse inputZipFile in (%s)" % inputline
            log(statusfile, "can't get inputZipFile, submit_job is returning 1\n")
            return 1
            
        tmplog.write("inputZipFile (%s)\n" % inputZipFile)
	#outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]
	
	#Get current working directory on comet
	pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()

	#Get username
	usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
	ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

	#Get userdirectory data and write to log file
	tmplog.write(pwd+'\n'+ls+'\n'+usernamedir)
	userdir=GLOBUSTRANSFERSDIR + '/'+usernamedir
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
                if len(rln_line.split()) >= 10:
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
	cmd="ln -s '%s/'* ." %(DirToSymLink)
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
                #partition='compute'
		#gpuextra1=''
		#gpuextra2=''
		#gpuextra3='relion/3.0_beta_cpu'
		if '--auto_refine' not in inputline:
                	partition='gpu'
			gpuextra1='#SBATCH --gres=gpu:p100:4\n'
        		gpuextra2='--gpu 0:1:2:3'
        		gpuextra3='relion/3.0.5_gpu'
			if not os.path.exists('Class3D_cosmic'):
				os.makedirs('Class3D_cosmic')
			counter=1
                        while counter<=500:
        	                if not os.path.exists('Class3D_cosmic/job%03i' %(counter)):
	        	                os.makedirs('Class3D_cosmic/job%03i' %(counter))
                        	        outdir='Class3D_cosmic/job%03i' %(counter)
                                	counter=10000
                                counter=counter+1
			#Get num iters: 
			varcounter=0
			for variable in inputline.split():
        			if variable == '--iter':
                			iter_position=varcounter
        			varcounter=varcounter+1
			numiters=inputline.split()[iter_position+1]

	if '--ref' not in inputline:
		if '--denovo_3dref' not in inputline: 
			if '--reconstruct_subtracted_bodies' in inputline: 
				if not os.path.exists('Multibody_cosmic'):
                                        os.makedirs('Multibody_cosmic')
                                counter=1
                                while counter<=500:
                                        if not os.path.exists('Multibody_cosmic/job%03i' %(counter)):
                                                os.makedirs('Multibody_cosmic/job%03i' %(counter))
                                                outdir='Multibody_cosmic/job%03i' %(counter)
                                                counter=10000
                                        counter=counter+1
	
			if '--reconstruct_subtracted_bodies' not in inputline:
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
                if not os.path.exists('Refine3D_cosmic'):
                	os.makedirs('Refine3D_cosmic/job001')
                counter=1
                while counter<=500:
                	if not os.path.exists('Refine3D_cosmic/job%03i' %(counter)):
                        	os.makedirs('Refine3D_cosmic/job%03i' %(counter))
                                outdir='Refine3D_cosmic/job%03i' %(counter)
                                counter=10000
                        counter=counter+1
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
	mpi_to_use=5
	''' 
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
	'''
	#>> Write number to scheduler.conf
	if not os.path.exists('scheduler.conf'):
		print 'Error=1'
		sys.exit()
	outwrite=open('scheduler.conf','a')
	outwrite.write('nodes=%i\n' %(nodes))
	outwrite.close()

	#Replace zipfile name in relion command 
	if '--reconstruct_subtracted_bodies' not in inputline:
		if '"' not in inputline:
			inputline_list=inputline.split()
			#tmplog.write('1:'+inputline_list+'\n')
        		tmplog.write("inputline_list (%s)\n" % (inputline_list,))
			tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--i')]))
			inputline_list[returnEntryNumber(inputline,'--i')]=newstarname
			inputline_list[returnEntryNumber(inputline,'--o')]='relion_refine_mpi --o %s/run' %(outdir)
			tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--o')]))
			if inputline_list[returnEntryNumber(inputline,'--ref')].split('.')[-1] == 'map': 
				inputline_list[returnEntryNumber(inputline,'--ref')]=inputline_list[returnEntryNumber(inputline,'--ref')]+':mrc'

			#Join list into single string
		        relion_command=' '.join(inputline_list)	
			tmplog.write(relion_command)	

		if '"' in inputline: 
			inputline_list=inputline.split('"')[0].split()
			tmplog.write("inputline_list (%s)\n" % (inputline_list,))
	                inputline_list[returnEntryNumber(inputline,'--o')]='relion_refine_mpi --o %s/run' %(outdir)
        	        tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--o')]))
                	if inputline_list[returnEntryNumber(inputline,'--ref')].split('.')[-1] == 'map':
                        	inputline_list[returnEntryNumber(inputline,'--ref')]=inputline_list[returnEntryNumber(inputline,'--ref')]+':mrc'	
			relion_command=' '.join(inputline_list)+'  %s' %(newstarname)
			tmplog.write(relion_command)	
	if '--reconstruct_subtracted_bodies' in inputline:
		inputline_list=inputline.split()
                tmplog.write('\n%s\n' %(inputline_list[returnEntryNumber(inputline,'--i')]))
                masksfile=inputline_list[returnEntryNumber(inputline,'--multibody_masks')]
		with zipfile.ZipFile(masksfile) as zip_ref:
			zip_ref.extractall()
		masksfile='%s/multibody-masks.star' %(masksfile.split('/')[0][:-4])
		inputline_list[returnEntryNumber(inputline,'--o')]='relion_refine_mpi --o %s/run' %(outdir)
		inputline_list[returnEntryNumber(inputline,'--i')]=''
		inputline_list[returnEntryNumber(inputline,'--multibody_masks')]=masksfile
		relion_command=' '.join(inputline_list)
                tmplog.write(relion_command)
		numiters=-1

		#Handle data.star file
		datastar=inputline_list[returnEntryNumber(inputline,'--datastar')]
		optimiser=inputline_list[returnEntryNumber(inputline,'--continue')]
		samplingstar=inputline_list[returnEntryNumber(inputline,'--samplingstar')]
		model1star=inputline_list[returnEntryNumber(inputline,'--model1star')]
		model2star=inputline_list[returnEntryNumber(inputline,'--model2star')]
		model1mrc=inputline_list[returnEntryNumber(inputline,'--model1mrc')]
		model2mrc=inputline_list[returnEntryNumber(inputline,'--model2mrc')]
		newoutdir=''
		r1=open(optimiser,'r')
		for line in r1: 
			if datastar.split('/')[-1] in line: 
				newoutdir=line.split()[-1]

		#Create output directory
		tmplog.write(newoutdir)
		numdirs=len(newoutdir.split('/'))
		numdirs=len(newoutdir.split('/'))
		counter=0
		runningdir=''
		while counter < numdirs-1: 
			if counter == 0: 
				os.makedirs(newoutdir.split('/')[counter])
		        	runningdir=newoutdir.split('/')[counter]
			if counter>0: 
				os.makedirs('%s/%s' %(runningdir,newoutdir.split('/')[counter]))
				runningdir='%s/%s' %(runningdir,newoutdir.split('/')[counter])
			counter=counter+1

		#Copy files into running dir	
		newoutdir=newoutdir.split('/')
		del newoutdir[-1]
		newoutdir='/'.join(newoutdir)
		tmplog.write('\n'+datastar.split('/')[-1])
		tmplog.write('\n%s/%s' %(newoutdir,datastar.split('/')[-1][:-5])) 
		shutil.copyfile(datastar.split('/')[-1],'%s/%s.star' %(newoutdir,datastar.split('/')[-1][:-5]))
		shutil.copyfile(samplingstar.split('/')[-1],'%s/%s.star' %(newoutdir,samplingstar.split('/')[-1][:-5]))
		shutil.copyfile(model1star.split('/')[-1],'%s/%s.star' %(newoutdir,model1star.split('/')[-1][:-5]))
		shutil.copyfile(model2star.split('/')[-1],'%s/%s.star' %(newoutdir,model2star.split('/')[-1][:-5]))
		shutil.copyfile(model2mrc,'%s/%s' %(newoutdir,model2mrc))
		shutil.copyfile(model1mrc,'%s/%s' %(newoutdir,model1mrc))
		mpi_to_use=3 
		nodes=1
		gpuextra2='--gpu 0,1:2,3'
	return relion_command,outdir,DirToSymLink,runtime,nodes,numiters,inputZipFile.split('/')[0],partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use,newstarname #print 'cmd="%s"' %(relion_command)

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
def runGSA ( gateway_user, jobid, resource ):
        #cmd = "/opt/ctss/gateway_submit_attributes/gateway_submit_attributes -resource %s.sdsc.xsede -gateway_user %s -submit_time \"`date '+%%F %%T %%:z'`\" -jobid %s" % (resource, "%s@cosmic2.sdsc.edu" % gateway_user, jobid)
        timestring = time.strftime('%Y-%m-%d %H:%M %Z', time.localtime())
	cmd = "{}/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir={}/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://xsede-xdcdb-api.xsede.org/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede' --jobid='{}' --submittime='{}'".format(REMOTESCRIPTSDIR, REMOTESCRIPTSDIR, '{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)

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
        log("./_JOBINFO.TXT","\npartition=%s" %(partition,))
        gpupartition_pat = r"^(gpu|gpu-shared)$"
        cpupartition_pat = r"^(compute|shared|large-shared|debug)$"
        gpupartition_reo = re.compile(gpupartition_pat)
        cpupartition_reo = re.compile(cpupartition_pat)
        resource = None
        if gpupartition_reo.match(partition):
            resource = 'comet-gpu'
        elif cpupartition_reo.match(partition):
            resource = 'comet'
        else:
            log("./_JOBINFO.TXT","\nFailed to find resource for partition=%s" %(partition,))
            print("\nFailed to find resource for partition=%s" %(partition,))
            return 1
        runGSA ( gatewayuser, jobid, resource )
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
parser.add_argument('--i')
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
if 'cryoEF' in args['commandline']:
        jobtype='cryoef'
if 'csparc2star.py' in args['commandline']:
	jobtype='csparc2star'
if 'micassess' in args['commandline']:
	jobtype='micassess'
if 'deepemhancer' in args['commandline']:
        jobtype='deepemhancer'
if 'cryodrgn' in args['commandline']:
	jobtype='cryodrgn'

if jobtype == 'micassess':
	formatted_starname,threshold=prepareMicassess(args['commandline'],jobdir)
	runhours=1
	runminutes = math.ceil(60 * runhours)
	partition='gpu-shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
        ntaskspernode = int(properties_dict['ntasks-per-node'])
        o1=open('_JOBINFO.TXT','a')
        o1.write('\ncores=4\n')
        o1.close()
        shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
        jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
        mailuser = jobproperties_dict['email']
        jobname = jobproperties_dict['JobHandle']
        for line in open('_JOBINFO.TXT','r'):
                if 'User\ Name=' in line:
                        username=line.split('=')[-1].strip()
        jobstatus=open('job_status.txt','w')    
        jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --nodes=1  # Total number of nodes requested (16 cores/node)
#SBATCH --ntasks-per-node=6             # Total number of mpi tasks requested
#SBATCH --gres=gpu:1
#SBATCH --no-requeue
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export OMP_NUM_THREADS=$SLURM_CPUS_PER_TASK
module purge
module load anaconda/4.7.12
__conda_setup="$('/share/apps/compute/anaconda/bin/conda' 'shell.bash' 'hook' 2> /dev/null)"
if [ $? -eq 0 ]; then
    eval "$__conda_setup"
else
    if [ -f "/share/apps/compute/anaconda/etc/profile.d/conda.sh" ]; then
        . "/share/apps/compute/anaconda/etc/profile.d/conda.sh"
    else
        export PATH="/share/apps/compute/anaconda/bin:$PATH"
    fi
fi
unset __conda_setup
conda activate /projects/cosmic2/conda/cryoassess 
python /home/cosmic2/software_dependencies/Automatic-cryoEM-preprocessing/micassess.py -i %s -t %s -m ~/software_dependencies/model_files/micassess_051419.h5 -o %s_micassess_good.star 
zip -r MicAssess.zip MicAssess/
zip -r /projects/cosmic2/meta-data/%s-micassess.zip MicAssess/
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'],jobdir,formatted_starname,threshold,formatted_starname[:-5],randomString(40))
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

if jobtype == 'cryodrgn': 
	command=args['commandline']
	#Parsing commandline: 
	#cryodrgn --angpix .91 --qdim 256 --D 128 --invert --players 3 --zdim 1 --consensus run_data_30k.star --qlayers 3 --pdim 256 -n 50  --i "cryodrgn/Extract/job042/particles_30k.star"

	cmdline=command.split()
	totEntries=len(cmdline)
	counter=0
	while counter < totEntries: 
		entry=cmdline[counter]
		if entry == '--angpix': 
			angpix=cmdline[counter+1]
		if entry == '--qdim':
			qdim=cmdline[counter+1]
		if entry == '--D':
			newboxsize=cmdline[counter+1]
		if entry == '--invert':
			invert=True
		if entry == '--players': 
			players=cmdline[counter+1]
		if entry == '--zdim': 
			zdim=cmdline[counter+1]
		if entry == '--consensus':
			pose_ctf=cmdline[counter+1]
		if entry == '--qlayers': 
			qlayers=cmdline[counter+1]
		if entry == '--pdim':
			pdim=cmdline[counter+1]
		if entry == '-n':
			epochs=cmdline[counter+1]
		if entry == '--origbox':
			orig_boxsize=cmdline[counter+1]
		if entry == '--i': 
			starfile=cmdline[counter+1]
		counter=counter+1	

	#Get datapath data
        pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
	usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()

        #Get userdirectory data and write to log file
        userdir=GLOBUSTRANSFERSDIR + '/'+usernamedir
        
	starfilename=starfile.split('/')
	cosmic2foldername=starfilename[0].strip('"')
	del starfilename[0]
	starfilename='/'.join(starfilename).strip('"')
	#/projects/cosmic2/gatewaydev/globus_transfers/mcianfro@umich.edu/"cryodrgn/Extract/job042/particles_30k.star"
	#starfilename=Extract/job042/particles_30k.star
	#userdir=/projects/cosmic2/gatewaydev/globus_transfers/mcianfro@umich.edu/
	#cosmic2foldername=cryodrgn

	cmd="ln -s '%s/%s/'* ." %(userdir,cosmic2foldername)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()	
	
	cmd='''module load cuda/9.2
source /share/apps/compute/anaconda/etc/profile.d/conda.sh
conda activate /projects/cosmic2/conda/cryodrgn\n'''

	#Downsample
	cmd=cmd+'''cryodrgn downsample %s -D %i -o particles.%i.mrcs --chunk 40000 >> stdout.txt 2>>stderr.txt\n''' %(starfilename,int(newboxsize),int(newboxsize))

	#Pose reconfig
	cmd=cmd+'''cryodrgn parse_pose_star %s -o pose.pkl -D %s  >> stdout.txt 2>>stderr.txt\n''' %(pose_ctf,orig_boxsize)

	#CTF info
	cmd=cmd+'''cryodrgn parse_ctf_star %s -D %s --Apix %s -o ctf.pkl  >> stdout.txt 2>>stderr.txt\n''' %(pose_ctf,orig_boxsize,angpix)

	#VAE 
	invertcmd=''
	if invert is True:
		invertcmd='--invert-data'
	cmd=cmd+'cryodrgn train_vae particles.%i.txt --poses pose.pkl --ctf ctf.pkl --zdim %s -n %s --qdim %s %s --qlayers %s --pdim %s --players %s -o cryodrgn  >> stdout.txt 2>>stderr.txt\n' %(int(newboxsize),zdim,epochs,qdim,invertcmd,qlayers,pdim,players)

	#Analysis
	cmd=cmd+'cryodrgn analyze cryodrgn %i --Apix %s >> stdout.txt 2>>stderr.txt\n' %(int(epochs)-1,angpix)
	cmd=cmd+'zip -r cosmic2-cryodrgn.zip cryodrgn\n'

	o1.write(cmd)

	runhours=12
        runminutes = math.ceil(60 * runhours)
        partition='gpu-shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
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
        jobstatus=open('job_status.txt','w')
        jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --cpus-per-task=%i
#SBATCH --no-requeue
#SBATCH --gres=gpu:1
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,6,jobdir,cmd)
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


if jobtype == 'deepemhancer':
        command=args['commandline']
	outfile='%s_deepEMhancer-sharpened.mrc' %(command.split()[-1].strip('"')[:-4])
        cmd='''module load cuda/9.2
source /share/apps/compute/anaconda/etc/profile.d/conda.sh
conda activate /projects/cosmic2/conda/deepEMhancer_env
%s -o %s -g 0,1,2,3 >>stdout.txt 2>>stderr.txt 
''' %(command,outfile)
        runhours=4
        runminutes = math.ceil(60 * runhours)
        partition='gpu'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
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
        jobstatus=open('job_status.txt','w')
        jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --cpus-per-task=%i
#SBATCH --no-requeue
#SBATCH --gres=gpu:4
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,4,6,jobdir,cmd)
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

if jobtype == 'csparc2star':
	command=args['commandline']
	outfile=command.split()[-1].split('.')[0]+'.star'
	cmd='''module load singularity
singularity exec /home/cosmic2/software_dependencies/pyem/ubuntu-pyem-v6.simg /opt/miniconda2/bin/python %s %s''' %(command,outfile)
	runhours=8
        runminutes = math.ceil(60 * runhours)
        partition='shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
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
        jobstatus=open('job_status.txt','w')	
	jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --cpus-per-task=1
#SBATCH --no-requeue
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], nodes,1,jobdir,cmd)
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

if jobtype == 'cryoef': 
	#relion_command,outdir,out_destination,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use,newstarname=prepareRelionRun(args)
	command=args['commandline']
	infile=command.split()[-1].split('"')[1]
	csparc2star=''
	if '.cs' in infile: 
		originfile=infile
		infile=infile.split('.')        
                del infile[-1]
                infile=''.join(infile)
                outfile='%s.star' %(infile)
		csparc2star='''module load singularity
singularity exec /home/cosmic2/software_dependencies/pyem/ubuntu-pyem-v6.simg /opt/miniconda2/bin/python /cosmic2-software/pyem/csparc2star.py %s %s''' %(originfile,outfile)
		infile=outfile

	if '.star' in command: 
		command=command.replace('.star','.dat')	
		datfile=infile.replace('.star','.dat')
	if '.cs' in command: 
		command=command.replace('.cs','.dat')
		datfile=infile.replace('.star','.dat')
	runhours=8
	runminutes = math.ceil(60 * runhours)
	partition='shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
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
        jobstatus=open('job_status.txt','w')
        jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --cpus-per-task=1
#SBATCH --no-requeue
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
%s/cryoef_star_csv_2_dat.py %s >> stdout.txt 2>>stderr.txt
if test -f %s; then
	%s >>stdout.txt 2>>stderr.txt
fi
cat %s.log >> stdout.txt
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], nodes,1,jobdir,csparc2star,REMOTESCRIPTSDIR,infile,datfile,command,infile.split('.')[0])
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

if jobtype == 'relion': 
	relion_command,outdir,out_destination,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use,newstarname=prepareRelionRun(args)
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
	jobstatus=open('job_status.txt','w')
        jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --gres=gpu:4
module load cuda/9.2
module load intelmpi/2018.1.163
source /home/cosmic2/software_dependencies/relion/relion-3.1-gpu.sh
date 
export OMP_NUM_THREADS=5
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
#/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/monitor_relion_job.py %s %s $SLURM_JOBID %s & 
pwd > stdout.txt 2>stderr.txt
mpirun -np %i %s --j 5 %s --scratch_dir /scratch/$USER/$SLURM_JOB_ID >>stdout.txt 2>>stderr.txt
%s/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' %s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
	%(partition,jobname, runtime, mailuser, args['account'], nodes,4,jobdir,outdir.split('_cosmic')[0],outdir,numiters,mpi_to_use,relion_command,gpuextra2,REMOTESCRIPTSDIR,username,out_destination,outdir,relion_command,newstarname)
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
	#donefile = "done.txt"
	##d = subprocess.Popen("date +'%s %a %b %e %R:%S %Z %Y", shell=True, stdout=subprocess.PIPE)
	#d=subprocess.Popen("date +'%s %a %b %e %R:%S %Z %Y'", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
	#FO = open(donefile, mode='a')
	#FO.write(d+"\n")
	#FO.write("retval=" + str(rc) + "\n")
	#FO.flush()
	#os.fsync(FO.fileno())
	#FO.close()

if jobtype == 'pipeline':
        #relion_command,outdir,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use=preparePreprocessingRun(args['commandline'])
	DirToSymLink=preparePreprocessingRun(args['commandline'],jobdir)
	out_destination=DirToSymLink
	outdir='AutoPipeline'
	#General parameters
        totcores=1
	runminutes = math.ceil(2880)
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        o1=open('_JOBINFO.TXT','a')
        o1.write('\ncores=%i\n' %(totcores))
        o1.close()
        shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
        jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
        mailuser = jobproperties_dict['email']
        jobname = jobproperties_dict['JobHandle']
        partition='shared'
	for line in open('_JOBINFO.TXT','r'):
       		if 'User\ Name=' in line:
			username=line.split('=')[-1].strip()

	jobstatus=open('job_status.txt','w')
	jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
	jobstatus.write('Job currently in queue\n\n')
	jobstatus.close()

	#Create command
	runcmd=''
	runcmd=runcmd+'bash pipeline.sh >> stdout.txt 2>> stderr.txt\n' 
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
#SBATCH --nodes=1  # Total number of nodes requested (16 cores/node)
#SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
#SBATCH --no-requeue
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
module purge
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
%s/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' %s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
echo 'Job finished && date' >> job_status.txt
"""%(partition,jobname, runtime, mailuser, args['account'],jobdir,runcmd,REMOTESCRIPTSDIR,username,out_destination,outdir,runcmd,'pipeline')
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

