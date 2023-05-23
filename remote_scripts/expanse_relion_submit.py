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
import stat

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
        elements = inputline.split('"')
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
            print("Error, could not parse inputZipFile in ({})\n".format(inputline))
            log(statusfile, "can't get inputZipFile, submit_job is returning 1\n")
            return 1

        tmplog.write("inputZipFile (%s)\n" % inputZipFile)
        #outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]

        #Get current working directory on comet
        pwd=os.getcwd()
        usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().decode('utf8').split('=')[-1].strip()
        ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read().decode('utf8')

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
                print("Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting")
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
        tmplog.write('\nstarfiledirname='+starfiledirname+'\n')
        tmplog.write('\nnewstarname='+newstarname+'\n')

        workingStarDirName=starfilename #Extract/job023/particles.star
        fullStarDir=starfilename.split('/')
        del fullStarDir[-1]
        fullStarDir='/'.join(fullStarDir) #Extract/job023/
        counter=1
        tmplog.write('\nfullStarDir='+fullStarDir+'\n')
        '''while counter<=len(starfiledirname.split('/')):
                checkDir=starfiledirname.split('/')[-counter]
                if fullStarDir.split('/')[-1] == checkDir:
                        fullStarDir=fullStarDir.split('/')
                        del fullStarDir[-1]
                        fullStarDir='/'.join(fullStarDir)
                counter=counter+1
        '''

        #Symlink directory: 
        DirToSymLink=fullStarDir
        tmplog.write('\n'+'symlink'+DirToSymLink)
        cmd="ln -s '%s/' ." %(DirToSymLink)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()
        if newstarname[0] == '/':
                newstarname=newstarname[1:] 

        shutil.copyfile(starfilename,newstarname)

        return newstarname,threshold,DirToSymLink.split('/')[-1]

def preparePreprocessingRun(inputline,jobdir):

	#Get information from commandline:
	# pipeline --cs 2.7 --apix .66 --kev 300 --i "testing pipeline/micrograph_uploading/micrographs.star"

	#DEBUGGING
        o1=open('_preprocess.txt','w')
	#Input files
	#input_starfile=inputline.split()[returnEntryNumber(inputline,'--i')]
        #elements = string.split(inputline, '"')
        elements = inputline.split('"')
        testargs = []
        o1.write(inputline)
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
        extract = None
        extractScaled = None
        ctfscore = None 
        ctfreslim = None 
        motioninput= ''
        motionflag=0
        #standard_pipeline --cs 2.7 --apix 0.66 --extract 512 --ctfscore 0 --ctfreslim 4 --kev 300 -t 0.1 --extractScale 128 
        for eindex in range(len(testargs)):
            if testargs[eindex] == '-i':
                input_starfile = testargs[eindex + 1]
            if testargs[eindex] == '--apix':
                apix=testargs[eindex + 1]
            if testargs[eindex] == '--cs':
                cs=testargs[eindex + 1]
            if testargs[eindex] == '--kev':
                kev=testargs[eindex + 1]
            if testargs[eindex] == '--extract':
                extract=testargs[eindex + 1]
            if testargs[eindex] == '--extractScale':
                extractScaled=testargs[eindex + 1]
            if testargs[eindex] == '--ctfscore':
                ctfscore=testargs[eindex + 1]
            if testargs[eindex] == '--ctfreslim':
                ctfreslim=testargs[eindex + 1]
            if testargs[eindex] == '--motion':
                motioninput=motioninput+' '+testargs[eindex]
            if testargs[eindex] == '--moviebin':
                motioninput=motioninput+' '+testargs[eindex]+'='+testargs[eindex + 1]
            if testargs[eindex] == '--moviebfactor':
                motioninput=motioninput+' '+testargs[eindex]+'='+testargs[eindex + 1]
            if testargs[eindex] == '--moviepatchx':
                motioninput=motioninput+' '+testargs[eindex]+'='+testargs[eindex + 1]+' '
            if testargs[eindex] == '--moviepatchy':
                motioninput=motioninput+' '+testargs[eindex]+'='+testargs[eindex + 1]
        if input_starfile == None:
            print("Error, could not parse input starfile in (%s)" % inputline)
            log(statusfile, "can't get input starfile, submit_job is returning 1\n")
            return 1
        if motionflag == 0:
            motioninput=''
	
	#Get current working directory on comet
        pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().decode('utf8').strip()
        o1.write('pwd=%s\n' %(pwd))
        usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().decode('utf8').split('=')[-1].strip()
        o1.write('usernamedir=%s\n' %(usernamedir))
        #usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
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
                o1.write('%i\n' %(len(motioninput)))
                if len(motioninput) == 0: 
                        if '_rlnMicrographName' in rln_line:
                                if len(rln_line.split()) == 2:
                                    colnum=int(rln_line.split()[-1].split('#')[-1])-1
                                if len(rln_line.split()) == 1:
                                    colnum=0
                if len(motioninput) > 0: 
                        if '_rlnMicrographMovieName' in rln_line:
                                if len(rln_line.split()) == 2:
                                    colnum=int(rln_line.split()[-1].split('#')[-1])-1
                                if len(rln_line.split()) == 1:
                                    colnum=0
        rlno1.close()
        o1.write('reading rln col=%i' %(colnum))

        if colnum<0:
                print("Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting")
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
        o1.write('\n1222newstarname='+newstarname+'\n')

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
        outdest=fullStarDir
        DirToSymLink=fullStarDir+'/'+starfiledirname
        o1.write('\n'+'symlink'+DirToSymLink)
        cmd="ln -s '%s' ." %(DirToSymLink)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()

	#Copy starfile to current directory
        #shutil.copyfile(starfilename,newstarname.split('/')[-1])

	#Get email
        jobproperties_dict = getProperties('_JOBINFO.TXT')
        mailuser = jobproperties_dict['email']

        return DirToSymLink,DirToSymLink.split('/')[-1],apix,cs,kev,extract,extractScaled,ctfscore,ctfreslim,outdest,motioninput

def prepareRelionRun(args):
	#Start temp log file
        relion_command=''
        outdir=''
        numiters=''
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
        elements = inputline.split('"') #string.split(inputline, '"')
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
            print("Error, could not parse inputZipFile in ({})".format(inputline))
            log(statusfile, "can't get inputZipFile, submit_job is returning 1\n")
            return 1
            
        tmplog.write("inputZipFile (%s)\n" % inputZipFile)
        #outdir=inputline.split()[returnEntryNumber(inputline,'--o')].split('/')[0]
        
        #Get current working directory on comet
        pwd=os.getcwd() #subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()

        #Get username
        tmplog.write("%s" %(pwd))
        tmplog.write("cat %s/_JOBINFO.TXT | grep Name="%(pwd))
        for jobline in open('%s/_JOBINFO.TXT' %(pwd),'r'): 
            if "Name" in jobline: 
                usernamedir=jobline.split('=')[-1].strip()
                tmplog.write("          %s     " %(usernamedir))
                #subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()
        #ls=subprocess.Popen("ls", shell=True, stdout=subprocess.PIPE).stdout.read()

        #Get userdirectory data and write to log file
        #tmplog.write(pwd+'\n'+ls+'\n'+usernamedir)
        userdir=GLOBUSTRANSFERSDIR + '/'+usernamedir
        tmplog.write('\n'+userdir)

        #Get full path to starfile on cosmic
        starfilename=userdir+'/'+'%s' %(inputZipFile)
        tmplog.write('\n'+'starfilename:   '+starfilename)

        newstarname=inputZipFile.split('/')
        del newstarname[0]
        newstarname='/'.join(newstarname)
        tmplog.write('\n'+'newstarname:   '+newstarname)
        '''#Get relative path from starfile 
        #Read star file header to get particle name
        rlno1=open(starfilename,'r')
        colnum=-1
        for rln_line in rlno1:
                if '_rlnImageName' in rln_line:
                        colnum=int(rln_line.split()[-1].split('#')[-1])-1
        rlno1.close()

        if colnum<0:
                print("Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting")
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
        tmplog.write('\nstarfiledirname='+starfiledirname+'\n')
        workingStarDirName=starfilename
        fullStarDir=starfilename.split('/')
        del fullStarDir[-1]
        fullStarDir='/'.join(fullStarDir)
        counter=1
        tmplog.write('\nfullStarDir='+fullStarDir+'\n')
        while counter<=len(starfiledirname.split('/')):
                checkDir=starfiledirname.split('/')[-counter]
                tmplog.write('\n'+checkDir+'\n')
                tmplog.write('\n'+fullStarDir.split('/')[-1]+'\n')
                if fullStarDir.split('/')[-1] == checkDir:
                        fullStarDir=fullStarDir.split('/')
                        del fullStarDir[-1]
                        fullStarDir='/'.join(fullStarDir)
                counter=counter+1
        #Symlink directory: 
        DirToSymLink=userdir+'/'+inputZipFile.split('/')[0]   #fullStarDir
        tmplog.write('\n'+'symlink'+DirToSymLink)
        cmd="ln -s '%s/'* ." %(DirToSymLink)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()
       
        '''

        #3/29/21
        #Get relative path from starfile 
        #Read star file header to get particle name
        rlno1=open(starfilename,'r')
        colnum=-1
        for rln_line in rlno1:
                if '_rlnImageName' in rln_line:
                        colnum=int(rln_line.split()[-1].split('#')[-1])-1
        rlno1.close()

        if colnum<0:
                print("Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting")
                sys.exit()
        rlno1=open(starfilename,'r')
        checker=0
        while checker==0:
                for rln_line in rlno1:
                        if '@' in rln_line:
                                particlename=rln_line.split()[colnum].split('@')[-1]
                                checker=1
        rlno1.close()
        tmplog.write('\n'+'particlename:   '+particlename)
        particlenameDir=particlename.split('/')[0]
        tmplog.write('\n'+'particlenameDir:   '+particlenameDir)
        starfilenameDir=''
        checker=0
        for path in starfilename.split('/'):
                tmplog.write('\n'+'path:   '+path)
                if path == particlenameDir:
                        checker=1
                if checker==0:
                        starfilenameDir=starfilenameDir+'/'+path
        tmplog.write('\n'+'starfilenameDir SEL'+starfilenameDir)
        tmplog.write('\n'+'starfilenameDir SEL'+'/'+path)
        if starfilenameDir == '/'+starfilename: 
                #if starfilenameDir is empty; this means user provided select file separate from particle stack
                starfilenameDir=starfilename.replace(newstarname,'')
                tmplog.write('\n'+'starfilenameDir SEL'+starfilenameDir)
        newstarnameold=newstarname
        newstarname=''
        checker=0
        for path in starfilename.split('/'):
                if path == particlenameDir:
                        checker=1
                if checker==1:
                        if len(newstarname)>0:
                                newstarname=newstarname+'/'+path

                        if len(newstarname)==0: 
                                newstarname=path

        if len(newstarname) ==0:
            newstarname=newstarnameold
        tmplog.write('\n'+'newstarname:'+newstarname)
        pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
        o1=open('_tmp2.txt','w')
        o1.write('%s\n'%(pwd))
        o1.close()

        DirToSymLink=starfilenameDir   #fullStarDir
        tmplog.write('\n'+'symlink'+DirToSymLink)
        cmd="ln -s '%s/'* ." %(DirToSymLink)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()

        if not os.path.exists('%s' %(newstarname)):
        #Print error since could not find input star file
                pwd= subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read()
                o1=open('_tmp1.txt','w')
                o1.write('%s\n'%(pwd))
                o1.write('could not find %s' %(newstarname))
                o1.close()        
                print('Could not find input star file' )
                sys.exit()
        if 'isac' in inputline: 
                relion_command=newstarname

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
                                if 'relion_refine_mpi' in inputline: 
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
                print('Error=1')
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
                        if 'relion_refine_mpi' in inputline:
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
                                if not os.path.exists(newoutdir.split('/')[counter]): 
                                    os.makedirs(newoutdir.split('/')[counter])
                                runningdir=newoutdir.split('/')[counter]
                        if counter>0: 
                                if not os.path.exists('%s/%s' %(runningdir,newoutdir.split('/')[counter])):
                                    os.makedirs('%s/%s' %(runningdir,newoutdir.split('/')[counter]))
                                runningdir='%s/%s' %(runningdir,newoutdir.split('/')[counter])
                        counter=counter+1

                #Copy files into running dir        
                newoutdir=newoutdir.split('/')
                del newoutdir[-1]
                newoutdir='/'.join(newoutdir)
                tmplog.write('\n'+datastar.split('/')[-1])
                tmplog.write('\n%s/%s' %(newoutdir,datastar.split('/')[-1][:-5])) 
                if not os.path.exists('%s/%s.star' %(newoutdir,datastar.split('/')[-1][:-5])):
                        shutil.copyfile(datastar.split('/')[-1],'%s/%s.star' %(newoutdir,datastar.split('/')[-1][:-5]))
                if not os.path.exists('%s/%s.star' %(newoutdir,samplingstar.split('/')[-1][:-5])):
                        shutil.copyfile(samplingstar.split('/')[-1],'%s/%s.star' %(newoutdir,samplingstar.split('/')[-1][:-5]))
                if not os.path.exists('%s/%s.star' %(newoutdir,model1star.split('/')[-1][:-5])):
                        shutil.copyfile(model1star.split('/')[-1],'%s/%s.star' %(newoutdir,model1star.split('/')[-1][:-5]))
                if not os.path.exists('%s/%s.star' %(newoutdir,model2star.split('/')[-1][:-5])):
                        shutil.copyfile(model2star.split('/')[-1],'%s/%s.star' %(newoutdir,model2star.split('/')[-1][:-5]))
                if not os.path.exists('%s/%s' %(newoutdir,model2mrc)):
                        shutil.copyfile(model2mrc,'%s/%s' %(newoutdir,model2mrc))
                if not os.path.exists('%s/%s' %(newoutdir,model1mrc)):
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
    propFile= open( filename, "r" )
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
        #os.chmod(lib.epilogue, 0744);
        os.chmod(lib.epilogue, stat.IRUSR | sts.IWUSR | stat.IXUSR | stat.IRGRP | stat.IROTH);

# Function to run the gateway_submit_attribute script
def runGSA ( gateway_user, jobid, resource ):
        #cmd = "/opt/ctss/gateway_submit_attributes/gateway_submit_attributes -resource %s.sdsc.xsede -gateway_user %s -submit_time \"`date '+%%F %%T %%:z'`\" -jobid %s" % (resource, "%s@cosmic2.sdsc.edu" % gateway_user, jobid)
        timestring = time.strftime('%Y-%m-%d %H:%M %Z', time.localtime())
#<<<<<<< Updated upstream
#        cmd = "{}/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir={}/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://xsede-xdcdb-api.xsede.org/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede.org' --jobid='{}' --submittime='{}'".format(REMOTESCRIPTSDIR, REMOTESCRIPTSDIR, '{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)
#=======
#        #cmd = "{}/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir={}/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://xsede-xdcdb-api.xsede.org/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede.org' --jobid='{}' --submittime='{}'".format(REMOTESCRIPTSDIR, REMOTESCRIPTSDIR, '{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)
#>>>>>>> Stashed changes
        cmd = "{}/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir={}/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://allocations-api.access-ci.org/acdb/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede.org' --jobid='{}' --submittime='{}'".format(REMOTESCRIPTSDIR, REMOTESCRIPTSDIR, '{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)

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
    print("submit command ({})".format((cmd,)))
    p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
    print(cmd)
    #sys.exit()
    output =  p.communicate()[0]
    retval = p.returncode
    if retval != 0:
        # read whatever qsub wrote to the statusfile and print it to stdout
        print("Error submitting job:\n")
        f = open(statusfile, "r"); print(f.read() + "\n\n"); f.close()
        print(output)
        # When we return 2 it means too many jobs are queued.  qstat returns -226 on abe
        # in this situation ... not sure if that's true here, on trestles as well.
        #if retval == -226:
        #if retval == 1:
        #    retval = 2
        if re.search('Too many simultaneous jobs in queue',output.decode()) != None:
            retval = 2
        f = open(statusfile, "r")
        f_string = f.read()
        f.close()
        if re.search('sbatch: error: MaxSubmitJobsPerAccount', f_string) != None:
            retval = 2
        

        log(statusfile, "submit_job is returning %d\n" %  retval)

        return retval
    log(statusfile, "sbatch output is: " + output.decode() + "\n" +
        "======================================================================" +  "\n")

    # output from qsub should on trestles is just the full job id, <id>.trestles-fe1.sdsc.edu:
    # output from stampede is a bunch of stuff.  job id line is:
    #Submitted batch job 4822650
    #p = re.compile(r"^(\d+).trestles.\S+", re.M)
    p = re.compile(r"^Submitted batch job (?P<jobid>\d+)\s*$", re.M)
    m = p.search(output.decode())
    if m != None:
        jobid = m.group('jobid')
        short_jobid = m.group('jobid')
        print("jobid={}".format(int(short_jobid)))
        log(statusfile, "JOBID is %s\n" % jobid)
        log("./_JOBINFO.TXT", "\nJOBID=%s\n" % jobid)
        log("./_JOBINFO.TXT", "\njob_properties (%s)\n" % (job_properties,))
        #gatewayuser = string.split(job_properties['User\\'],'=')[1]
        gatewayuser = job_properties['User\\'].split('=')[1]
        log("./_JOBINFO.TXT", "\ngatewayuser (%s)\n" % (gatewayuser,))
        log("./_JOBINFO.TXT","\nChargeFactor=1.0\nncores=%s" %(job_properties['cores']))
        log("./_JOBINFO.TXT","\npartition=%s" %(partition,))
        gpupartition_pat = r"^(gpu|gpu-shared|gpu-debug|gpu-preempt)$"
        cpupartition_pat = r"^(compute|shared|large-shared|debug|preempt)$"
        gpupartition_reo = re.compile(gpupartition_pat)
        cpupartition_reo = re.compile(cpupartition_pat)
                #   expanse-gpu.sdsc.xsede.org
                        #  expanse-ps.sdsc.xsede.org
                                #    expanse.sdsc.xsede.org
        resource = None
        if gpupartition_reo.match(partition):
            resource = 'expanse-gpu'
        elif cpupartition_reo.match(partition):
            resource = 'expanse'
        else:
            log("./_JOBINFO.TXT","\nFailed to find resource for partition=%s" %(partition,))
            print("\nFailed to find resource for partition=%s" %(partition,))
            return 1
        runGSA ( gatewayuser, jobid, resource )
        return 0
    else:
        print("Error, sbatch says: %s" % output)
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
#args['account'] = 'csd547'
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
if 'standard_pipeline' in args['commandline']:
        jobtype='standard_pipeline'
if 'cryolo' in args['commandline']:
        jobtype='cryolo'
if 'relion_refine_mpi' in args['commandline']: 
        jobtype='relion'
if 'relion_postprocess' in args['commandline']:
        jobtype='postprocess'
if 'cryoEF' in args['commandline']:
        jobtype='cryoef'
if 'csparc2star.py' in args['commandline']:
        jobtype='csparc2star'
if 'micassess' in args['commandline']:
        jobtype='micassess'
if 'deepemhancer' in args['commandline']:
        jobtype='deepemhancer'
if 'isonet' in args['commandline']:
        jobtype='isonet'
if 'omegafold' in args['commandline']:
        jobtype='omegafold'
if 'cryodrgn' in args['commandline']:
        jobtype='cryodrgn'
if 'smappoi.py' in args['commandline']:
        jobtype='hrtm'
if 'localbfactor' in args['commandline']:
        jobtype='local_bfactor_estimation'
if 'locspiral' in args['commandline']:
        jobtype='locspiral'
if 'local_bfactor_sharpen' in args['commandline']:
        jobtype='local_bfactor_sharpen'
if 'loc_occupancy' in args['commandline']:
        jobtype='loc_occupancy'
if 'isac' in args['commandline']:
        jobtype='isac'
if '3dfscrun' in args['commandline']:
        jobtype='3dfsc'
if 'alphafold2' in args['commandline']:
        jobtype='alphafold2'
if 'colabfold' in args['commandline']:
        jobtype='colabfold'
if 'esmfold' in args['commandline']:
        jobtype='esmfold'
if 'igfold' in args['commandline']:
        jobtype='igfold'
if 'align_avgs' in args['commandline']:
        jobtype='alignproj'
if 'angelo' in args['commandline']:
        jobtype='modelangelo'
if 'hhblits' in args['commandline']:
        jobtype='hhblits'
if re.search('sleep_time.txt', args['commandline']):
        jobtype='sleep'

print('commandline ({})\n'.format(args['commandline']))

if jobtype == 'hhblits':
        cmd=args['commandline']
        runhours=1
        runminutes=60
        cmdline=cmd.split()
        totEntries=len(cmdline)
        counter=0
        while counter < totEntries:
                entry=cmdline[counter]
                if '-i' == entry:
                        inputfile=cmdline[counter+1]
                if '-d' == entry:
                        database=cmdline[counter+1]
                counter=counter+1
        if database == 'UniClust30':
            memory=8
            databasefile= '/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2/download_dir/uniclust30/uniclust30_2018_08/uniclust30_2018_08'
        if database == 'Pdb70':
            memory=8
            databasefile='/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2/download_dir/pdb70/pdb70'
        if database =='BFD':
            memory=16
            databasefile='/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2/download_dir/bfd/bfd_metaclust_clu_complete_id30_c90_final_seq.sorted_opt' 
        cmd='/expanse/projects/cosmic2/expanse/software_dependencies/hhsuite/bin/hhblits -i %s -d %s -oa3m %s.a3m -M first' %(inputfile,databasefile,inputfile.strip('"'))

        counter=0
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --mem=%iG
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s >> stdout.txt 2>>stderr.txt
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,1,memory,jobdir,cmd)
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

if jobtype == 'modelangelo':
        cmd=args['commandline']
        runhours=2
        runminutes=00
        cmdline=cmd.split()
        totEntries=len(cmdline)
        counter=0
        mode='build'
        mask=' '
        while counter < totEntries:
                entry=cmdline[counter]
                if 'build_no_seq' == entry:
                        mode='build_no_seq'
                if '-v' == entry:
                        volume=cmdline[counter+1]
                if '-f' == entry:
                        fasta=cmdline[counter+1]
                if '-m' == entry:
                        mask=' -m %s' %(cmdline[counter+1])
                counter=counter+1

        #Assemble command
        cmd='model_angelo %s -v %s %s' %(mode,volume,mask)
        if mode == 'build':
            cmd=cmd+' -f %s' %(fasta)
            fopen=open(fasta,'r')
            data=fopen.read()
            numAAs=len(data)
            if numAAs>800:
                runhours=6
        if mode == 'build_no_seq':
            runhours=2        

        partition='gpu-shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (runhours, runminutes)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --gpus=1
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
module purge
module load gpu anaconda3
source /cm/shared/apps/spack/gpu/opt/spack/linux-centos8-skylake_avx512/gcc-8.3.1/anaconda3-2020.11-bsn4npoxyw7jzz7fajncek3bvdoaa5wv/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse//software_dependencies/conda_model_angelo/
%s >> stdout.txt 2>stderr.txt
zip -r output.zip output/ 
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,10,jobdir,cmd)
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

if jobtype == 'omegafold':
        command=args['commandline']
        cmdline=command.split() #--subbatch_size=-1 --num_cycle=10 --fasta_paths="gfp.fasta"
        totEntries=len(cmdline)
        counter=0
        while counter < totEntries:
                entry=cmdline[counter]
                if 'subbatch' in entry: 
                        subbatch=entry.split('=')[-1]
                if 'num_cycle' in entry:
                        numcycle=entry.split('=')[-1]
                if 'fasta_paths' in entry:
                        fasta=entry.split('=')[-1]
                counter=counter+1

        tmplog=open('_tmplog','w')
        tmplog.write(cmdline[0])
        tmplog.write(command)
        pwd=os.getcwd() 

        #Assemble command
        cmd='omegafold %s output_dir/' %(fasta)
        if float(subbatch)>0:
                cmd=cmd+' --subbatch_size=%s' %(subbatch)
        cmd=cmd+ ' --num_cycle=%s' %(numcycle)
        cmd=cmd+ ' --weights_file=/expanse/projects/cosmic2/expanse/software_dependencies/conda-omegafold/model.pt'

        runhours=0
        runminutes=40
        partition='gpu-shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (runhours, runminutes)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --gpus=1
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
module purge
module load gpu anaconda3 
source /cm/shared/apps/spack/gpu/opt/spack/linux-centos8-skylake_avx512/gcc-8.3.1/anaconda3-2020.11-bsn4npoxyw7jzz7fajncek3bvdoaa5wv/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/software_dependencies/conda-omegafold/
%s >> stdout.txt 2>stderr.txt
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,10,jobdir,cmd)
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

if jobtype == 'isonet':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '-p':
                        approach=cmdline[counter+1]
                counter=counter+1

        tmplog=open('_tmplog','w')
        tmplog.write(cmdline[0])
        tmplog.write(command)
        pwd=os.getcwd() #subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()

        #Get username
        tmplog.write("%s" %(pwd))
        tmplog.write("cat %s/_JOBINFO.TXT | grep Name="%(pwd))
        for jobline in open('%s/_JOBINFO.TXT' %(pwd),'r'):
            if "Name" in jobline:
                usernamedir=jobline.split('=')[-1].strip()
                tmplog.write("          %s     " %(usernamedir))

        #Get userdirectory data and write to log file
        userdir=GLOBUSTRANSFERSDIR + '/'+usernamedir
        tmplog.write('\n'+userdir)

        #Get input directory name 
        inputZipFile=command.split('"')[-2]
        isonetInput=inputZipFile.split('/')
        del isonetInput[0]
        isonetInput='/'.join(isonetInput)

        #Get starfilename
        inputZipFile=command.split('"')[-2]
        juststar=inputZipFile.split('/')[-1]
        justdir=inputZipFile.split('/')[-2]
        isonetstarinput='%s/%s' %(justdir,juststar)        

        #Get full path to starfile on cosmic
        starfilename=userdir+'/'+'%s' %(inputZipFile)
        tmplog.write('\n'+'starfilename:   '+starfilename)

        newstarname=starfilename.split('/')
        del newstarname[-1]
        newstarname='/'.join(newstarname)

        cmd='ln -s "/%s" .' %(newstarname)
        tmplog.write('ln -s "/%s" .' %(newstarname))
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read() 

        globusJobDir=newstarname.split('/')
        del globusJobDir[-1]
        globusJobDir='/'.join(globusJobDir)

        cmd=command
        #Assemble command
        cmd='isonet.py refine "%s"  --remove_intermediate ' %(isonetstarinput)
        if approach == 'Exhaustive':
            cmd=cmd+' --iterations 40 --noise_start_iter 11,16,21,26,31,36 --noise_level 0.05,0.1,0.15,0.2,0.25,0.3'
        if approach == 'Disabled':
            cmd=cmd+' --iterations 30 --noise_start_iter 0 --noise_level 0'
        if approach == 'Debug':
            cmd=cmd+' --iterations 1 --noise_start_iter 0 --noise_level 0'
        cmd=cmd+' --gpuID 0,1,2,3' 
        cmd=cmd+' --result_dir isonet_out'

        runhours=24
        runminutes=00
        partition='gpu'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (runhours, runminutes)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --gpus=4
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
module purge
module load gpu anaconda3 
source /cm/shared/apps/spack/gpu/opt/spack/linux-centos8-skylake_avx512/gcc-8.3.1/anaconda3-2020.11-bsn4npoxyw7jzz7fajncek3bvdoaa5wv/etc/profile.d/conda.sh
export PATH=/expanse/projects/cosmic2/expanse/software_dependencies/IsoNet/bin:$PATH
export PYTHONPATH=/expanse/projects/cosmic2/expanse/software_dependencies/:$PYTHONPATH
conda activate /expanse/projects/cosmic2/expanse/software_dependencies/conda-isonet3
%s >> stdout.txt 2>stderr.txt
%s/transfer_output_isonet.py '%s'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,40,jobdir,cmd,REMOTESCRIPTSDIR,globusJobDir)
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
if jobtype == 'alignproj':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        tmplog=open('_tmplog','w')
        tmplog.write(cmdline[0])
        tmplog.write(command)
        cmd=command
        runhours=00
        runminutes = 10
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
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
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/software_dependencies/EMAN2
module load cpu gcc openmpi relion/3.1.1
export PATH='/expanse/projects/cosmic2/expanse/software_dependencies/spider/24.01/spider/bin/':$PATH
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,1,jobdir,cmd)
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

if jobtype == 'sleep': 
        #'echo WORKING DIR `pwd`; echo ENV `env` ; date > sleep_time.txt ; sleep 10 ; date >> sleep_time.txt '
        #relion_command,outdir,out_destination,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use,newstarname=prepareRelionRun(args)
        partition = 'gpu-shared'
        runhours = 0.1
        runminutes = math.ceil(60 * runhours)
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes = 1
        #ntaskspernode = int(properties_dict['ntasks-per-node'])
        ntaskspernode = 1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
        #ntaskspernode = int(properties_dict['ntasks-per-node'])
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
##SBATCH --gres=gpu:4
#SBATCH --gpus=1
#SBATCH --mem=90
#SBATCH --licenses=cosmic:1
#module load cuda/9.2
#module load intelmpi/2018.1.163
#source /home/cosmic2/software_dependencies/relion/relion-3.1-gpu.sh
date 
export OMP_NUM_THREADS=5
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
sleep 120 >>stdout.txt 2>>stderr.txt
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], nodes,4,jobdir)
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

if jobtype == '3dfsc':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--i': 
                        half1map=cmdline[counter+1].strip('"')
                if entry == '--i2':
                        half2map=cmdline[counter+1]
                counter=counter+1

        #../ThreeDFSC/ThreeDFSC_Start.py --halfmap1=T40_map1_Masked_144.mrc --halfmap2=T40_map2_Masked_144.mrc --fullmap=130K-T40.mrc --apix=1.31 --ThreeDFSC=T40-3DFSC
        #source /expanse/projects/cosmic2/expanse/software_dependencies/EMAN-1.9/eman.bashrc
            
        
        cmdold='''source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate 3DFSC_2
source /expanse/projects/cosmic2/expanse/software_dependencies/phenix-installer-1.19.2-4158-intel-linux-2.6-x86_64-centos6/phenix/phenix-1.19.2-4158/phenix_env.sh
/expanse/projects/cosmic2/expanse/software_dependencies/phenix-installer-1.19.2-4158-intel-linux-2.6-x86_64-centos6/phenix/phenix-1.19.2-4158/build/bin/phenix.fmodel %s scattering_table=electron high_resolution=%f generate_fake_p1_symmetry=True >> stdout.txt 2>> stderr.txt
/expanse/projects/cosmic2/expanse/software_dependencies/phenix-installer-1.19.2-4158-intel-linux-2.6-x86_64-centos6/phenix/phenix-1.19.2-4158/build/bin/phenix.mtz2map %s.mtz include_fmodel=True >> stdout.txt 2>> stderr.txt
/expanse/projects/cosmic2/expanse/software_dependencies/scripts/resize_map_according_to_other_mapDims_RELION.py %s_fmodel.ccp4 %s %s_newbox.mrc %s >> stdout.txt 2>> stderr.txt
/expanse/projects/cosmic2/expanse/software_dependencies/Anisotropy/ThreeDFSC/ThreeDFSC_Start.py --halfmap1=%s --halfmap2=%s_newbox.mrc --fullmap=%s --apix=%s --ThreeDFSC=3DFSC-output >> stdout.txt 2>> stderr.txt
zip -r 3DFSC-output.zip Results_3DFSC-output/
''' %(half2map,float(angpix)*2,half2map,half2map,half1map,half2map[:-4],angpix,half1map,half2map[:-4],half1map,angpix)
        cmd='''source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate 3DFSC_2
source /expanse/projects/cosmic2/expanse/software_dependencies/EMAN-1.9/eman.bashrc
/expanse/projects/cosmic2/expanse/software_dependencies/scripts/resize_map_according_to_other_mapDims_EMAN.py %s %s %s_newbox.mrc %s >> stdout.txt 2>> stderr.txt
/expanse/projects/cosmic2/expanse/software_dependencies/Anisotropy/ThreeDFSC/ThreeDFSC_Start.py --halfmap1=%s --halfmap2=%s_newbox.mrc --fullmap=%s --apix=%s --ThreeDFSC=3DFSC-output >> stdout.txt 2>> stderr.txt
zip -r 3DFSC-output.zip Results_3DFSC-output/
''' %(half1map,half2map,half2map[:-4],angpix,half1map,half2map[:-4],half1map,angpix)
        runhours=1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --no-requeue
#SBATCH --mem=24G
#SBATCH --licenses=cosmic:1
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date
module load cpu gcc openmpi
module load relion/3.1.1
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,1,jobdir,cmd)
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
		

if jobtype == 'loc_occupancy': 
        command=args['commandline']
        #localbfactor --angpix .82 --bfactor_max 2.96 --bw 4.8 --i2 emd_10418_half_map_2.mrc --noise 0.95 --bfactor_min 15 --numpoints 10 --thresh 0.0153 --i "emd_10418_half_map_1.mrc"
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        mem='24G'
        ntasks=24
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--highmem':
                        mem='240G'
                        ntasks=5
                if entry == '--bfactor_max':
                        maxres=cmdline[counter+1]
                if entry == '--bfactor_min':
                        minres=cmdline[counter+1]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--bw':
                        bandwidth=cmdline[counter+1]
                if entry == '--i': 
                        half1map=cmdline[counter+1].strip('"')
                if entry == '--i2':
                        half2map=cmdline[counter+1]
                if entry == '--thresh':
                        thresh=cmdline[counter+1]
                if entry == '--threshcompare': 
                        noisethresh=cmdline[counter+1]
                counter=counter+1
        #get inputs
         
        runscript='runLocOccupancy.m' 
        o1=open(runscript,'w')
        o1.write('''clear all
close all

addpath('/expanse/projects/cosmic2/expanse/software_dependencies/LocSpiral-LocBSharpen-LocBFactor-LocOccupancy/Code')
myCluster = parcluster('local')
myCluster.NumWorkers = %i
saveProfile(myCluster);
parpool('local',%i)

vol1 = ReadMRC('%s');
vol2 = ReadMRC('%s');
vol = 0.5*(vol1+vol2);
clear vol1 vol2;
WriteMRC(vol,%s,'%s_combined.mrc');

mask = vol > %s; 
mask = bwareaopen(mask,25,6);
WriteMRC(mask,%s,'mask.mrc');
[map] = locOccupancy(vol,mask,%s,%s,%s,%s,%s);
WriteMRC(map,%s,'%s_locOccupancy.mrc');''' %(ntasks,ntasks,half1map,half2map,angpix,half1map[:-4],thresh,angpix,angpix,minres,maxres,noisethresh,bandwidth,angpix,half1map[:-4]))
        o1.close()
        cmd='''module load matlab/2022b
matlab -nodisplay -nosplash -nodesktop -r "run('%s');exit" > stdout.txt 2> stderr.txt''' %(runscript)
        runhours=1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
module load cpu 
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,24,1,jobdir,cmd)
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

if jobtype == 'local_bfactor_sharpen': 
        command=args['commandline']
	#localbfactor --angpix .82 --bfactor_max 2.96 --bw 4.8 --i2 emd_10418_half_map_2.mrc --noise 0.95 --bfactor_min 15 --numpoints 10 --thresh 0.0153 --i "emd_10418_half_map_1.mrc"
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        mem='24G'
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--highmem':
                        mem='240G'
                entry=cmdline[counter]
                if entry == '--bfactor_max':
                        maxres=cmdline[counter+1]
                if entry == '--bfactor_min':
                        minres=cmdline[counter+1]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--bw':
                        bandwidth=cmdline[counter+1]
                if entry == '--i': 
                        half1map=cmdline[counter+1].strip('"')
                if entry == '--i2':
                        half2map=cmdline[counter+1]
                if entry == '--thresh':
                        thresh=cmdline[counter+1]
                if entry == '--noise': 
                        noisethresh=cmdline[counter+1]
                counter=counter+1
        #get inputs
         
        runscript='runLocBfactor.m' 
        o1=open(runscript,'w')
        o1.write('''clear all
close all

addpath('/expanse/projects/cosmic2/expanse/software_dependencies/LocSpiral-LocBSharpen-LocBFactor-LocOccupancy/Code')
myCluster = parcluster('local')
myCluster.NumWorkers = 24
saveProfile(myCluster);
parpool('local',24)

vol1 = ReadMRC('%s');
vol2 = ReadMRC('%s');
vol = 0.5*(vol1+vol2);
clear vol1 vol2;
WriteMRC(vol,%s,'%s_combined.mrc');

mask = vol > %s; 
mask = bwareaopen(mask,25,6);
WriteMRC(mask,%s,'mask.mrc');
[map W] = locBSharpen(vol,mask,%s,%s,%s,%s,%s);
WriteMRC(map,%s,'%s_locBfactor.mrc');''' %(half1map,half2map,angpix,half1map[:-4],thresh,angpix,angpix,minres,maxres,noisethresh,bandwidth,angpix,half1map[:-4]))
        o1.close()
        cmd='''module load matlab/2022b
matlab -nodisplay -nosplash -nodesktop -r "run('%s');exit" > stdout.txt 2> stderr.txt''' %(runscript)
        runhours=1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --mem=%s
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
module load cpu 
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,24,1,mem,jobdir,cmd)
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

if jobtype == 'locspiral': 
        command=args['commandline']
	#localbfactor --angpix .82 --bfactor_max 2.96 --bw 4.8 --i2 emd_10418_half_map_2.mrc --noise 0.95 --bfactor_min 15 --numpoints 10 --thresh 0.0153 --i "emd_10418_half_map_1.mrc"
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        mem='24G'
        ntasks=24
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--highmem':
                        mem='240G'
                        ntasks=5
                if entry == '--bfactor_max':
                        maxres=cmdline[counter+1]
                if entry == '--bfactor_min':
                        minres=cmdline[counter+1]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--bw':
                        bandwidth=cmdline[counter+1]
                if entry == '--i': 
                        half1map=cmdline[counter+1].strip('"')
                if entry == '--i2':
                        half2map=cmdline[counter+1]
                if entry == '--thresh':
                        thresh=cmdline[counter+1]
                if entry == '--noise': 
                        noisethresh=cmdline[counter+1]
                counter=counter+1
        #get inputs
         
        runscript='runLocSpiral.m' 
        o1=open(runscript,'w')
        o1.write('''clear all
close all

addpath('/expanse/projects/cosmic2/expanse/software_dependencies/LocSpiral-LocBSharpen-LocBFactor-LocOccupancy/Code')
myCluster = parcluster('local')
myCluster.NumWorkers = %s
saveProfile(myCluster);
parpool('local',%s)

vol1 = ReadMRC('%s');
vol2 = ReadMRC('%s');
vol = 0.5*(vol1+vol2);
clear vol1 vol2;
WriteMRC(vol,%s,'%s_combined.mrc');

mask = vol > %s; 
mask = bwareaopen(mask,25,6);
WriteMRC(mask,%s,'mask.mrc');
[map W] = locSpiral(vol,mask,%s,%s,%s,%s,%s);
WriteMRC(map,%s,'%s_locSpiralMap.mrc');''' %(ntasks,ntasks,half1map,half2map,angpix,half1map[:-4],thresh,angpix,angpix,minres,maxres,noisethresh,bandwidth,angpix,half1map[:-4]))
        o1.close()
        cmd='''module load matlab/2022b
matlab -nodisplay -nosplash -nodesktop -r "run('%s');exit" > stdout.txt 2> stderr.txt''' %(runscript)
        runhours=1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --mem=%s
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
module load cpu 
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,ntasks,1,mem,jobdir,cmd)
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

if jobtype == 'local_bfactor_estimation': 
        command=args['commandline']
	#localbfactor --angpix .82 --bfactor_max 2.96 --bw 4.8 --i2 emd_10418_half_map_2.mrc --noise 0.95 --bfactor_min 15 --numpoints 10 --thresh 0.0153 --i "emd_10418_half_map_1.mrc"
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        mem='24G'
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--highmem':
                        mem='240G'
                if entry == '--bfactor_max':
                        maxres=cmdline[counter+1]
                if entry == '--bfactor_min':
                        minres=cmdline[counter+1]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--bw':
                        bandwidth=cmdline[counter+1]
                if entry == '--i': 
                        half1map=cmdline[counter+1].strip('"')
                if entry == '--i2':
                        half2map=cmdline[counter+1]
                if entry == '--noise':
                        noisethresh=cmdline[counter+1]
                if entry == '--numpoints':
                        numpoints=cmdline[counter+1]
                if entry == '--thresh':
                        thresh=cmdline[counter+1]
                counter=counter+1
        #get inputs
         
        runscript='localBfactor_estimation.m' 
        o1=open(runscript,'w')
        o1.write('''clear all
close all

addpath('/expanse/projects/cosmic2/expanse/software_dependencies/LocSpiral-LocBSharpen-LocBFactor-LocOccupancy/Code')
myCluster = parcluster('local')
myCluster.NumWorkers = 24
saveProfile(myCluster);
parpool('local',24)

vol1 = ReadMRC('%s');
vol2 = ReadMRC('%s');
vol = 0.5*(vol1+vol2);
clear vol1 vol2;
WriteMRC(vol,%s,'%s_combined.mrc');

mask = vol > %s; 
mask = bwareaopen(mask,25,6);

[AMap BMap noise Mod resSquare] = locBFactor(vol,mask,%s,%s,%s,%s,%s,%s);
WriteMRC(BMap*4,%s,'%s_local_bfactorMap.mrc');	
WriteMRC(AMap,%s,'%s_local_bfactorAMap.mrc');''' %(half1map,half2map,angpix,half1map[:-4],thresh,angpix,minres,maxres,numpoints,noisethresh,bandwidth,angpix,half1map[:-4],angpix,half1map[:-4]))
        o1.close()
        cmd='''module load matlab/2022b
matlab -nodisplay -nosplash -nodesktop -r "run('%s');exit" > stdout.txt 2> stderr.txt''' %(runscript)
        runhours=1
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --mem=%s 
#SBATCH --licenses=cosmic:1
module load cpu
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,24,1,mem,jobdir,cmd)
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


if jobtype == 'hrtm': 

        command=args['commandline']
        cmdline=command.split()

        totEntries=len(cmdline)
        counter=0
	#smappoi.py --cc 2.7 --angpix 1 --cif 6ek0_LSU.cif --w 0.07 --a_i 0.000050 --angle_inc 1.875 --t_sample 100 --deltaE 0.7 --df_inc 50 --cs 2.7 --b_factor 0 --kev 300 --
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--cs':
                        cs=cmdline[counter+1]
                if entry == '--cc':
                        cc=cmdline[counter+1]
                if entry == '--angpix':
                        angpix=cmdline[counter+1]
                if entry == '--cif':
                        inputcif=cmdline[counter+1]
                if entry == '--w':
                        ampcontrast=cmdline[counter+1]
                if entry == '--a_i':
                        a_i=cmdline[counter+1]
                if entry == '--angle_inc':
                        angle_inc=cmdline[counter+1]
                if entry == '--t_sample':
                        t_sample=cmdline[counter+1]
                if entry == '--deltaE':
                        deltaE=cmdline[counter+1]
                if entry == '--df_inc':
                        df_inc=cmdline[counter+1]
                if entry == '--b_factor':
                        b_factor=cmdline[counter+1]
                if entry == '--kev':
                        kev=cmdline[counter+1]
                if entry == '--i':
                        mrcfile=cmdline[counter+1].strip('"')
                if entry == '--aPerPix_search':
                        aperpix_search=cmdline[counter+1]
                counter=counter+1

        outdirname='hrtm-output'

        aperpix_search_add=''
        if float(aperpix_search)>0:
                aperpix_search_add='aPerPix_search %f\n' %(float(aperpix_search))

        #Write parameter file
        parfile='hrtm_params.txt'
        p=open(parfile,'w')
        p.write('''# the fxn to run:
function search_global\n''')
        p.write('''# The image:
imageFile %s
aPerPix %s
defocus ddff1 ddff2 aaaang
T_sample %s\n''' %(mrcfile,angpix,t_sample))
        p.write('''# The reference structure:
structureFile %s\n''' %(inputcif))
        p.write('''# Microscope specs:
V_acc %i
Cs %f
Cc %f
deltaE %s
a_i %s\n''' %(int(kev)*1000,float(cs)/1000,float(cc)/1000,deltaE,a_i))
        p.write('''# search specs:
nCores 4
outputDir %s
df_inc %s
angle_inc %s
%s''' %(outdirname,df_inc,angle_inc,aperpix_search_add))
        p.close()

        runhours=8
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
cp /home/cosmic2/software_dependencies/hrtm/compute.cfg .
python /home/cosmic2/software_dependencies/hrtm/run_ctffind4.py hrtm_params.txt >>stdout.txt 2>stderr.txt
/home/cosmic2/software_dependencies/hrtm/smappoi_run.sh hrtm_params_ctf.txt >>stdout.txt 2>stderr.txt
zip -r hrtm-output.zip hrtm-output
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'],1,4,6,jobdir)
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
if jobtype == 'cryolo':
        formatted_starname,threshold,indirname=prepareMicassess(args['commandline'],jobdir)
        runhours=2
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --gpus=1
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
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
module load gpu/0.15.4 
module load anaconda3/2020.11
source /expanse/projects/cosmic2/expanse/software_dependencies/pipeline/init.sh
/expanse/projects/cosmic2/expanse/software_dependencies/pipeline/cryolo.py --indir=%s --outdir=cryolo/ -t %s
zip -r cryolo-picks.zip cryolo/
zip -r /expanse/projects/cosmic2/meta-data/%s-micrographs.zip %s/
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'],jobdir,indirname,threshold,randomString(40),indirname)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
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
        uninvert=False
        relion31=False
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        checkpoint=''
        indexfile=''
        nowindow=False
        while counter < totEntries: 
                entry=cmdline[counter]
                if entry == '--w':
                        ampcontrast=cmdline[counter+1]
                if entry == '--cs':
                        cs=cmdline[counter+1]
                if entry == '-b': 
                        batch=cmdline[counter+1]
                if entry == '--relion31':
                        relion31=True
                if entry == '--load':
                        checkpoint=cmdline[counter+1]
                if entry == '--no-window':
                        nowindow=True
                if entry == '--ind':
                        indexfile=cmdline[counter+1]
                if entry == '--angpix': 
                        angpix=cmdline[counter+1]
                if entry == '--enc-dim':
                        qdim=cmdline[counter+1]
                if entry == '--D':
                        newboxsize=cmdline[counter+1]
                if entry == '--uninvert-data':
                        uninvert=True
                if entry == '--dec-layers': 
                        players=cmdline[counter+1]
                if entry == '--zdim': 
                        zdim=cmdline[counter+1]
                if entry == '--consensus':
                        pose_ctf=cmdline[counter+1]
                if entry == '--enc-layers': 
                        qlayers=cmdline[counter+1]
                if entry == '--dec-dim':
                        pdim=cmdline[counter+1]
                if entry == '-n':
                        epochs=cmdline[counter+1]
                if entry == '--origbox':
                        orig_boxsize=cmdline[counter+1]
                if entry == '--i': 
                        starfile=cmdline[counter+1]
                if entry == '--kev':
                        kev=cmdline[counter+1]
                counter=counter+1        

        relion31cmd=''
        if relion31 is True:
                relion31cmd='--relion31'

        #Get datapath data
        pwd=os.getcwd()
        #pwd=subprocess.Popen("pwd", shell=True, stdout=subprocess.PIPE).stdout.read().strip()
        for jobline in open('%s/_JOBINFO.TXT' %(pwd),'r'):
            if "Name" in jobline:
                usernamedir=jobline.split('=')[-1].strip()
                #usernamedir=subprocess.Popen("cat %s/_JOBINFO.TXT | grep Name="%(pwd), shell=True, stdout=subprocess.PIPE).stdout.read().split('=')[-1].strip()

        #Get userdirectory data and write to log file
        userdir=GLOBUSTRANSFERSDIR + '/'+usernamedir
        
        starfilename=starfile.split('/')
        cosmic2foldername=starfilename[0].strip('"')
        del starfilename[0]
        starfilename='/'.join(starfilename).strip('"')

        cmd="ln -s '%s/%s/'* ." %(userdir,cosmic2foldername)
        subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE).stdout.read()        
        
        cmd='''module load gpu 
module load cuda10.2/toolkit
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/conda/cryodrgn/\n'''

        #Downsample
        cmd=cmd+'''cryodrgn downsample %s -D %i -o particles.%i.mrcs --chunk 40000 %s >> stdout.txt 2>>stderr.txt\n''' %(starfilename,int(newboxsize),int(newboxsize),relion31cmd)

        #Pose reconfig
        pose31=''
        if relion31 is True: 
                pose31=' --Apix %s' %(angpix)
        cmd=cmd+'''cryodrgn parse_pose_star %s -o pose.pkl -D %s %s %s >> stdout.txt 2>>stderr.txt\n''' %(pose_ctf,orig_boxsize,relion31cmd,pose31)

        #CTF info
        cmd=cmd+'''cryodrgn parse_ctf_star %s -D %s --Apix %s --cs %s -w %s --kv %s -o ctf.pkl %s  >> stdout.txt 2>>stderr.txt\n''' %(pose_ctf,orig_boxsize,angpix,cs,ampcontrast,kev,relion31cmd)

        #VAE 
        invertcmd=''
        if uninvert is True:
                invertcmd='--uninvert-data'
        indexcmd=''
        checkpointcmd=''
        nowindowcmd=''
        if nowindow is True:
                nowindowcmd='--no-window'
        if len(checkpoint)>0:
                checkpointcmd='--load %s' %(checkpoint)
        if len(indexfile)>0:
                indexcmd='--ind %s' %(indexfile)
        cmd=cmd+'cryodrgn train_vae particles.%i.txt --poses pose.pkl --ctf ctf.pkl --zdim %s -n %s --enc-dim %s %s --enc-layers %s --dec-dim %s --dec-layers %s -b %i -o cryodrgn_vae_zdim%i_encDim%i_encLayers%i_decDim%i_decLayers%i_epochs%i %s %s %s %s >> stdout.txt 2>>stderr.txt\n' %(int(newboxsize),zdim,epochs,qdim,invertcmd,qlayers,pdim,players,int(batch),int(zdim),int(qdim),int(qlayers),int(pdim),int(players),int(epochs),indexcmd,checkpointcmd,nowindowcmd,relion31cmd)

        #Analysis
        cmd=cmd+'cryodrgn analyze cryodrgn_vae_zdim%i_encDim%i_encLayers%i_decDim%i_decLayers%i_epochs%i %i --Apix %s >> stdout.txt 2>>stderr.txt\n' %(int(zdim),int(qdim),int(qlayers),int(pdim),int(players),int(epochs),int(epochs)-1,angpix)
        cmd=cmd+'python /home/cosmic2/software_dependencies/cryodrgn_scripts/parse_cryodrgn_output.py cryodrgn_vae_zdim%i_encDim%i_encLayers%i_decDim%i_decLayers%i_epochs%i/\n' %(int(zdim),int(qdim),int(qlayers),int(pdim),int(players),int(epochs))
        cmd=cmd+'zip -r cosmic2-cryodrgn.zip cryodrgn_vae_zdim%i_encDim%i_encLayers%i_decDim%i_decLayers%i_epochs%i\n' %(int(zdim),int(qdim),int(qlayers),int(pdim),int(players),int(epochs))

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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --gpus=1
#SBATCH --licenses=cosmic:1
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

if jobtype == 'colabfold':
        COLABFOLDECHO = '/expanse/projects/cosmic2/expanse/software_dependencies/ColabFold/echoit.csh'
        CLUSTERSCRIPT = '/expanse/projects/cosmic2/expanse/software_dependencies/ColabFold/Colabfold/Slurm_setup/wrappers/utils/submit_colabfold_cluster.csh'
        command=args['commandline']
        cmdline=command.split()
        cmd=''
        totEntries=len(cmdline)
        fasta_paths = []
        predpermodel = 1
        use_amber = 'False'
        use_templates = 'False'
        for option in cmdline:
            name = None
            value = None
            optionparts = option.split('=')
            if len(optionparts) == 2:
                name = optionparts[0]
                value = optionparts[1]
            #if name == '--num_models':
            #    num_models = value
            if name == '--num_models':
                num_models = value
                cmd=cmd+' --num-models %i' %(int(num_models))
            if name == '--num_recycles':
                num_recycles = value
                cmd=cmd+' --num-recycle %i' %(int(num_recycles))
            if name == '--stop_at_score':
                stopscore = value
                if int(stopscore)>0:
                    cmd=cmd+ ' --stop-at-score %i' %(int(stopscore))
            if name == '--use_amber':
                use_amber = value
                if int(use_amber) == 0:
                    use_amber = 'False'
                else:
                    use_amber = 'True'
                    cmd=cmd+' --amber --use-gpu-relax'
            if name == '--use_templates':
                use_templates = value
                if int(use_templates) == 0:
                    use_templates = 'False'
                else:
                    use_templates = 'True'
                    cmd=cmd+' --templates'
            if name == '--max_msa':
                maxmsa=value
                if maxmsa != 'auto':
                    cmd=cmd+ '--max-msa %s' %(value)
            if name == '--fasta_path':
                fasta_path = value.strip('"')

        # Open the input file for reading
        counter=1
        readfasta=open('%s/%s' %(os.getcwd(),fasta_path.strip('"')), 'r')
        # Initialize an empty string to store the joined lines
        joined_lines= ''
        # Loop through each line of the file
        for line in readfasta:
        # Strip any leading or trailing whitespace characters
            line = line.strip()
            # If the line starts with '>', replace the '>' character with a semicolon
            if line.startswith('>'):
                if counter>1:
                    line = ':'
                if counter ==1:
                    line='' 
                # Append the line to the joined lines string
            joined_lines += line
            counter=counter+1
        # Print the joined lines string without any carriage returns
        outstring=joined_lines.replace('\n', '')
        outfasta=open('%s_stitched.fasta' %(fasta_path.split('.')[0]),'w')
        outstring='>%s\n' %(fasta_path.split('.')[0])+outstring
        outfasta.write(outstring)
        outfasta.close()
        fasta_path='%s_stitched.fasta' %(fasta_path.split('.')[0])
        readfasta.close()
        #Check fasta file: for capital letters; only two lines (right?) if htere is a semi colon. otherwise we need to create a single line for multiple chains
            #if name == '--fasta_paths':
            #    fasta_paths_list = value.split(',')
            #    for fasta_path in fasta_paths_list:
            #        # strip out the double-quotes that pisexml put in
            #        fasta_paths.append(re.sub(r'"',r'',fasta_path.strip()))
        #fasta_paths_string = ''
        DEBUGFO=open('_submitdebug.txt','w')
        #for fasta_path in fasta_paths:
        #for findex, fasta_path in enumerate(fasta_paths):
        #    FO.write('fasta_path: (%s)\n'%(fasta_path))
        #    fasta_paths_string = fasta_paths_string + '{}/{}'.format(jobdir,fasta_path)
        #    if len(fasta_paths) > 1 and findex < len(fasta_paths) - 1:
        #        fasta_paths_string = fasta_paths_string + ','
        DEBUGFO.write('fasta_path: (%s)\n'%(fasta_path))
        #cmd = "/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2.multimer.v2.2.0/runit.afold.bash.multi --num_multimer_predictions_per_model=%s --db_preset='%s' --model_preset='%s' --fasta_paths='%s'" % (predpermodel, db_preset, model_preset, fasta_paths_string)
        #runhours=12
        runhours=48
        runminutes = math.ceil(60 * runhours)
        #partition='gpu'
        partition='gpu-shared'
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        nodes=1
        ntaskspernode = int(properties_dict['ntasks-per-node'])
        cpus = int(properties_dict['cpus'])
        gpus = int(properties_dict['gpus'])
        memory = properties_dict['memory']
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
        ntaskspernode = int(properties_dict['ntasks-per-node'])
        jobdir = os.getcwd()
        OUTPUT = jobdir + '/output_dir'
        CPUS = '10'
        runfile='run.sh'
        openrunfile=open(runfile,'w')
        openrunfile.write('''module purge 
module load cpu/0.17.3b
module load gpu/0.17.3b  nvhpc/21.9/4xco23d
module load intel/19.1.3.304/vecir2b
module load cuda/11.2.2
module load anaconda3/2021.05
source /cm/shared/apps/spack/0.17.3/gpu/b/opt/spack/linux-rocky8-skylake_avx512/gcc-8.5.0/anaconda3-2021.05-kfluefzsateihlamuk2qihp56exwe7si/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/software_dependencies/localcolabfold-newmay2023/localcolabfold/colabfold-conda/
colabfold_batch %s output/ %s''' %(fasta_path,cmd))
        openrunfile.close()
        text = """#!/bin/sh
#SBATCH -o scheduler_stdout.txt    # Name of stdout output file(%%j expands to jobId)
#SBATCH -e scheduler_stderr.txt    # Name of stderr output file(%%j expands to jobId)
#SBATCH --partition=%s           # submit to the 'large' queue for jobs > 256 nodes
#SBATCH -J %s        # Job name
#SBATCH -t %s         # Run time (hh:mm:ss) - 1.5 hours
#SBATCH --mail-user=%s
#SBATCH --mail-type=begin
#SBATCH --mail-type=end
#The next line is required if the user has more than one project
# #SBATCH -A A-yourproject  # Allocation name to charge job against
#SBATCH -A %s  # Allocation name to charge job against
#SBATCH --nodes=%i  # Total number of nodes requested (16 cores/node)
#SBATCH --cpus-per-task=10
#SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
#SBATCH --mem=90G
#SBATCH --no-requeue
#SBATCH --gpus=1
#SBATCH --licenses=cosmic:1
date
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
bash run.sh >>stdout.txt 2>>stderr.txt
/bin/tar -czf output.tar.gz output
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,jobdir)
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
        #STITCHEDFILE = f'{OUTPUT}/stitchedsequence.tmp'
        #properties_dict = getProperties(f'{OUTPUT}/scheduler.conf')
        #JOBNAME = properties_dict['JobHandle']
        #ARGS = f' --jobname={jobname} --num_models={num_models} --cpus={cpus} --gpus={gpus} --memory={memory} --output={OUTPUT} --num_recycles={num_recycles} --use_amber={use_amber} --use_templates={use_templates}'
        #cmd = f'{COLABFOLDECHO} {fasta_path} {ARGS}'
        #cmdoutput = os.popen(cmd).read()
        #DEBUGFO.write('\n'+ cmdoutput + '\n')
        #sys.stderr.write('\n'+ cmdoutput + '\n')
        #sbatch_pat = r'^sbatch --gpus=(?P<gpus>\d+) --cpus-per-task=(?P<cpus_per_task>\d+) --mem=(?P<mem>\d+G) --error=(?P<error>\S+) --output=(?P<output>\S+) /expanse/projects/cosmic2/expanse/software_dependencies/ColabFold/Colabfold/Slurm_setup/wrappers/utils/submit_colabfold_cluster.csh (?P<arg1>.*/stitchedsequence.tmp) (?P<arg2>\S+) (?P<arg3>\S+) (?P<arg4>\S+) (?P<arg5>\S+) (?P<arg6>\S+) (?P<arg7>\S+) (?P<arg8>\S+)\s*$'
        #sbatch_reo = re.compile(sbatch_pat, re.MULTILINE)
        #sbatch_mo = sbatch_reo.search(cmdoutput)
        #jobscript = open(CLUSTERSCRIPT).read()
        #jobscript = re.sub(r'\$1', sbatch_mo.group('arg1'), jobscript)
        #jobscript = re.sub(r'\$2', sbatch_mo.group('arg2'), jobscript)
        #jobscript = re.sub(r'\$3', sbatch_mo.group('arg3'), jobscript)
        #jobscript = re.sub(r'\$3', sbatch_mo.group('arg3'), jobscript)
        #jobscript = re.sub(r'\$4', sbatch_mo.group('arg4'), jobscript)
        #jobscript = re.sub(r'\$5', sbatch_mo.group('arg5'), jobscript)
        #jobscript = re.sub(r'\$6', sbatch_mo.group('arg6'), jobscript)
        #jobscript = re.sub(r'\$7', sbatch_mo.group('arg7'), jobscript)
        #jobscript = re.sub(r'\$8', sbatch_mo.group('arg8'), jobscript)
        #jobscript = re.sub(r'#SBATCH --gpus=\d+', '#SBATCH --gpus={}'.format(sbatch_mo.group('gpus')), jobscript)
        #jobscript = re.sub(r'#SBATCH --job-name=Colabfold', '#SBATCH --job-name={}'.format(jobname), jobscript)
        #jobscript = re.sub(r'###COSMIC2_SBATCH_DIRECTIVES###', '#SBATCH --licenses=cosmic:1', jobscript)
        #cosmic2precode = """
#date
#cd '%s/output_dir/'
#date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
#echo 'Job is now running' >> job_status.txt
#module load singularitypro
#(pwd > stdout.txt) > & stderr.txt
#""" % (jobdir#,)
#        cosmic2postcode = """
#cd '%s/'
#/bin/tar -czf output_dir.tar.gz output_dir
#date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
#""" % (jobdir,)
#        jobscript = re.sub(r'#___COSMIC2_PRECODE___', cosmic2precode, jobscript)
#        jobscript = re.sub(r'#___COSMIC2_POSTCODE___', cosmic2postcode, jobscript)
#        jobscript = re.sub(r'___COSMIC2_MAILUSER___', mailuser, jobscript)

#        FO = open('batch_command.run', 'w')
#        FO.write(jobscript)
#        FO.close()
#

#        text = """#!/bin/sh
##SBATCH -o scheduler_stdout.txt    # Name of stdout output file(%%j expands to jobId)
##SBATCH -e scheduler_stderr.txt    # Name of stderr output file(%%j expands to jobId)
##SBATCH --partition=%s           # submit to the 'large' queue for jobs > 256 nodes
##SBATCH -J %s        # Job name
##SBATCH -t %s         # Run time (hh:mm:ss) - 1.5 hours
##SBATCH --mail-user=%s
##SBATCH --mail-type=begin
##SBATCH --mail-type=end
###SBATCH --qos=nsg
##The next line is required if the user has more than one project
## #SBATCH -A A-yourproject  # Allocation name to charge job against
##SBATCH -A %s  # Allocation name to charge job against
##SBATCH --nodes=%i  # Total number of nodes requested (16 cores/node)
###SBATCH --ntasks-per-node=%i             # Total number of mpi tasks requested
###SBATCH --cpus-per-task=%i
##SBATCH --cpus-per-task=10
##SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
###SBATCH --mem=374G
###SBATCH --mem=279G
##SBATCH --mem=90G
##SBATCH --no-requeue
##SBATCH --gpus=1
#date
#cd '%s/'
#mkdir output_dir
#date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
#echo 'Job is now running' >> job_status.txt
#module load singularitypro
#pwd > stdout.txt 2>stderr.txt
##%s
#singularity exec --nv --bind /expanse/projects/cosmic2/expanse/software_dependencies/alphafold2.multimer.v2.2.0,/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2.multimer.download_dir.v2.2.0 -H %s /cm/shared/apps/containers/singularity/tensorflow/tensorflow-2.5.0-ubuntu-18.04-cuda-11.2-openmpi-4.0.5-20210707.sif %s --output_dir=%s/output_dir --stdouterr_path=%s/output_dir/outerr.txt --slurm_job_id=${SLURM_JOB_ID}
#python %s/plotting_afold.py %s/output_dir/
#/bin/tar -czf output_dir.tar.gz output_dir
#date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
#""" \
#        %(partition,jobname, runtime, mailuser, args['account'], 1,4,6,jobdir,cmd,jobdir,cmd,jobdir,jobdir,REMOTESCRIPTSDIR,jobdir)
        
#        runfile = "./batch_command.run"
#        statusfile = "./batch_command.status"
#        cmdfile = "./batch_command.cmdline"
#        debugfile = "./nsgdebug"
#        FO = open(runfile, mode='w')
#        FO.write(text)
#        FO.flush()
#        os.fsync(FO.fileno())
#        FO.close()
#        rc = submitJob(job_properties=jobproperties_dict, runfile='batch_command.run', statusfile='batch_command.status', cmdfile='batch_command.cmdline')
#        DEBUGFO.close()

if jobtype == 'esmfold':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        chunk=-1
        #fasta_paths = []
        #predpermodel = 1
        for option in cmdline:
            name = None
            value = None
            optionparts = option.split('=')
            if len(optionparts) == 2:
                name = optionparts[0]
                value = optionparts[1]
            if name == '--fasta_path':
                fasta_path = re.sub(r'"',r'',value.strip())
            if name == '--chunk':
                chunk=value
            if name == '--num_recycles':
                numrecycles=value
        # Open the input file for reading
        counter=1
        readfasta=open('%s/%s' %(os.getcwd(),fasta_path.strip('"')), 'r')
        # Initialize an empty string to store the joined lines
        joined_lines= ''
        # Loop through each line of the file
        for line in readfasta:
        # Strip any leading or trailing whitespace characters
            line = line.strip()
            # If the line starts with '>', replace the '>' character with a semicolon
            if line.startswith('>'):
                if counter>1:
                    line = ':'
                if counter ==1:
                    line=''
                # Append the line to the joined lines string
            joined_lines += line
            counter=counter+1
        # Print the joined lines string without any carriage returns
        seq=joined_lines.replace('\n', '')
        readfasta.close()

        FO=open('_submitdebug.txt','w')

        cmd = "python3 %s/run_esmfold_cmdline.py %s %s %s %s" %(REMOTESCRIPTSDIR,seq,fasta_path[:-6],chunk,numrecycles)
        runhours=3
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#The next line is required if the user has more than one project
# #SBATCH -A A-yourproject  # Allocation name to charge job against
#SBATCH -A %s  # Allocation name to charge job against
#SBATCH --nodes=%i  # Total number of nodes requested (16 cores/node)
#SBATCH --cpus-per-task=1
#SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
#SBATCH --mem=90G
#SBATCH --gpus=1
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
. /expanse/projects/cosmic2/expanse/software_dependencies/esmfold.kenneth/env.cuda
export HOME=/expanse/projects/cosmic2/expanse/software_dependencies/esmfold.kenneth/HOME.cuda
. /expanse/projects/cosmic2/expanse/software_dependencies/esmfold.kenneth/HOME.cuda/.bashrc
conda activate esmfold
cd '%s/'
%s >>stdout.txt 2>>stderr.txt
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,jobdir,cmd)
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

if jobtype == 'igfold':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        #fasta_paths = []
        #predpermodel = 1
        for option in cmdline:
            name = None
            value = None
            optionparts = option.split('=')
            if len(optionparts) == 2:
                name = optionparts[0]
                value = optionparts[1]
            #if name == '----num_multimer_predictions_per_model':
            #    predpermodel = value
            #if name == '--db_preset':
            #    db_preset = value
            #if name == '--model_preset':
            #    model_preset = value
            #if name == '--fasta_paths':
            #    fasta_paths_list = value.split(',')
            #    for fasta_path in fasta_paths_list:
            #        # strip out the double-quotes that pisexml put in
            #        fasta_paths.append(re.sub(r'"',r'',fasta_path.strip()))
            if name == '--fasta_path':
                fasta_path = re.sub(r'"',r'',value.strip())
        #fasta_paths_string = ''
        FO=open('_submitdebug.txt','w')
        #for fasta_path in fasta_paths:
        #for findex, fasta_path in enumerate(fasta_paths):
        #    FO.write('fasta_path: (%s)\n'%(fasta_path))
        #    fasta_paths_string = fasta_paths_string + '{}/{}'.format(jobdir,fasta_path)
        #    if len(fasta_paths) > 1 and findex < len(fasta_paths) - 1:
        #        fasta_paths_string = fasta_paths_string + ','
        #FO.close()
        #cmd = "/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2.multimer.v2.2.0/runit.afold.bash.multi --num_multimer_predictions_per_model=%s --db_preset='%s' --model_preset='%s' --fasta_paths='%s'" % (predpermodel, db_preset, model_preset, fasta_paths_string)
        cmd = "/expanse/projects/cosmic2/expanse/software_dependencies/igfold/install/venv/bin/python3 /expanse/projects/cosmic2/expanse/software_dependencies/igfold/test2.py --fasta_path='%s'" % (fasta_path,)
        #runhours=12
        runhours=48
        runminutes = math.ceil(60 * runhours)
        #partition='gpu-shared'
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#The next line is required if the user has more than one project
# #SBATCH -A A-yourproject  # Allocation name to charge job against
#SBATCH -A %s  # Allocation name to charge job against
#SBATCH --nodes=%i  # Total number of nodes requested (16 cores/node)
#SBATCH --cpus-per-task=1
#SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
#SBATCH --mem=8G
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
mkdir output_dir
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
. /expanse/projects/cosmic2/expanse/software_dependencies/igfold/env.cpu
%s >>stdout.txt 2>>stderr.txt
python %s/plotting_afold.py %s/output_dir/
/bin/tar -czf output_dir.tar.gz output_dir
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], 1,jobdir,cmd,REMOTESCRIPTSDIR,jobdir)
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

if jobtype == 'alphafold2':
        command=args['commandline']
        cmdline=command.split()
        totEntries=len(cmdline)
        fasta_paths = []
        predpermodel = 1
        for option in cmdline:
            name = None
            value = None
            optionparts = option.split('=')
            if len(optionparts) == 2:
                name = optionparts[0]
                value = optionparts[1]
            if name == '--skip_run_relax':
                if value == 0:
                    run_relax = True
                else:
                    run_relax = False
            if name == '--max_template_date':
                max_template_date = value
            if name == '--num_multimer_predictions_per_model':
                predpermodel = value
            if name == '--db_preset':
                db_preset = value
            if name == '--model_preset':
                model_preset = value
            if name == '--fasta_paths':
                fasta_paths_list = value.split(',')
                for fasta_path in fasta_paths_list:
                    # strip out the double-quotes that pisexml put in
                    fasta_paths.append(re.sub(r'"',r'',fasta_path.strip()))
        fasta_paths_string = ''
        FO=open('_submitdebug.txt','w')
        #for fasta_path in fasta_paths:
        for findex, fasta_path in enumerate(fasta_paths):
            FO.write('fasta_path: (%s)\n'%(fasta_path))
            fasta_paths_string = fasta_paths_string + '{}/{}'.format(jobdir,fasta_path)
            if len(fasta_paths) > 1 and findex < len(fasta_paths) - 1:
                fasta_paths_string = fasta_paths_string + ','
        FO.close()
        #cmd = "/expanse/projects/cosmic2/expanse/software_dependencies/alphafold2.multimer.v2.2.0/runit.afold.bash.multi --num_multimer_predictions_per_model=%s --db_preset='%s' --model_preset='%s' --fasta_paths='%s'" % (predpermodel, db_preset, model_preset, fasta_paths_string)
        #runhours=12
        runhours=48
        runminutes = math.ceil(60 * runhours)
        #partition='gpu'
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --ntasks-per-node=1             # Total number of mpi tasks requested
#SBATCH --cpus-per-task=10
#SBATCH --mem=94348M
#SBATCH --no-requeue
#SBATCH --gpus=1
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
date
cd '%s/'
mkdir output_dir
export TMPDIR=`pwd`/output_dir
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
module load singularitypro
module load gpu/0.15.4
module load python/3.8.5
. /expanse/projects/cosmic2/expanse/software_dependencies/afold.2.3.1.kenneth/venv/bin/activate

export ALPHAFOLD_DIR=/expanse/projects/cosmic2/expanse/software_dependencies/afold.2.3.1.kenneth/alphafold_singularity-2.3.1
export ALPHAFOLD_DATADIR=/expanse/projects/cosmic2/expanse/software_dependencies/afold.2.3.1.kenneth/download_dir

echo ALPHAFOLD_DIR=$ALPHAFOLD_DIR
echo ALPHAFOLD_DATADIR=$ALPHAFOLD_DATADIR


pwd > stdout.txt 2>stderr.txt
# Run AlphaFold; default is to use GPUs
python3 ${ALPHAFOLD_DIR}/run_singularity.py \
    --data_dir=${ALPHAFOLD_DATADIR} \
    --fasta_paths=%s \
    --max_template_date=%s \
    --db_preset=%s \
    --model_preset=%s \
    --num_multimer_predictions_per_model=%s \
    --run_relax=%s >output_dir/outerr.txt 2>&1

deactivate
module purge
python %s/plotting_afold.py %s/output_dir/
/bin/tar -czf output_dir.tar.gz output_dir
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'],jobdir,fasta_paths_string,max_template_date,db_preset,model_preset,predpermodel,run_relax,REMOTESCRIPTSDIR,jobdir)
        #%(partition,jobname, runtime, mailuser, args['account'], 1,4,6,jobdir,cmd,jobdir,cmd,jobdir,jobdir,REMOTESCRIPTSDIR,jobdir)
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
        cmdline=command.split()
        totEntries=len(cmdline)
        counter=0
        maskflag=0
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '-m':
                        maskflag=1
                counter=counter+1

        if maskflag == 1: 
                command=''
                counter=0
                while counter < totEntries:
                        entry=cmdline[counter]
                        if entry == '-p':
                                counter=counter+2
                        if entry != '-p':
                                command=command+' %s '%(cmdline[counter])
                                counter=counter+1

        cmd='''module load gpu
module load cuda10.2/toolkit
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/conda-expanse/deepEMhancer_env
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --gpus=4
#SBATCH --licenses=cosmic:1
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

if jobtype == 'postprocess':
        command=args['commandline']
        cmdline=command.split()
        #if adhoc_bfac then remove autob_lowres. If NOT adhoc_bfac then add --auto_bfac and keep autob_lowres
        totEntries=len(cmdline)
        counter=0
        adhocBflag=0
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--adhoc_bfac':
                        adhocBflag=1
                counter=counter+1

        if adhocBflag == 1:
                command=''
                counter=0
                while counter < totEntries:
                        entry=cmdline[counter]
                        if entry == '--autob_lowres':
                                counter=counter+2
                        if entry != '--autob_lowres':
                                command=command+' %s '%(cmdline[counter])
                                counter=counter+1
        if adhocBflag == 0:
                command=command+' --auto_bfac'

        cmd='''module load cpu gcc openmpi
module load relion/3.1.1
mkdir postprocess 
%s >>stdout.txt 2>>stderr.txt
''' %(command)
        runhours=1
        runminutes = math.ceil(60 * runhours)
        partition='compute'
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
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
        cmd='''module load anaconda3/2020.11
module load cpu
module load DefaultModules
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/conda/pyem
%s %s >> stdout 2>>stderr.txt''' %(command,outfile)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
module load slurm 
module load cpu 
module load singularitypro
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
singularity exec /expanse/projects/cosmic2/singularity/ubuntu-pyem-v6.simg /opt/miniconda2/bin/python /cosmic2-software/pyem/csparc2star.py %s %s''' %(originfile,outfile)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
module load cpu 
module load slurm 
module load shared
module load DefaultModules
module load gcc
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --cpus-per-task=8
#SBATCH --gpus=4
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
module purge
module load slurm
module load gpu/0.15.4
module load openmpi
module load relion
export OMP_NUM_THREADS=8
export OMPI_MCA_btl_openib_allow_ib=1
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
#/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/monitor_relion_job.py %s %s $SLURM_JOBID %s & 
pwd > stdout.txt 2>stderr.txt
mpirun -np %i %s --j 8 %s --scratch_dir /scratch/$USER/job_$SLURM_JOB_ID >>stdout.txt 2>>stderr.txt
%s/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' %s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], nodes,5,jobdir,outdir.split('_cosmic')[0],outdir,numiters,mpi_to_use,relion_command,gpuextra2,REMOTESCRIPTSDIR,username,out_destination,outdir,relion_command,newstarname)
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

if jobtype == 'standard_pipeline':
        DirToSymLink,indir,apix,cs,kev,extract,extractScaled,ctfscore,ctfreslim,userglobusdir,motioninput=preparePreprocessingRun(args['commandline'],jobdir) 
        out_destination=DirToSymLink
        outputdir='Preprocess_cosmic2_%i' %(int(time.time()))
        #outputdir,kev,apix,cs,extract,extractScaled,ctfscore,ctfreslim,indir
        runminutes = math.ceil(60 * 3)
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        ntaskspernode = int(properties_dict['ntasks-per-node'])
        nodes=1
        partition='gpu-shared'
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
        jobstatus.write('Job currently in queue\n\n')
        jobstatus.close()
        ntaskspernode = 1 #int(properties_dict['ntasks-per-node'])
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
#SBATCH --cpus-per-task=8
#SBATCH --gpus=1
#SBATCH --mem=50G
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
module purge
module load slurm
module load gpu
module load openmpi
module load relion
module load gcc/7.2.0
export OMP_NUM_THREADS=8
export OMPI_MCA_btl_openib_allow_ib=1
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh
conda activate /expanse/projects/cosmic2/expanse/conda/cryolo/
export OMP_NUM_THREADS=8
export OMPI_MCA_btl_openib_allow_ib=1
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
/expanse/projects/cosmic2/expanse/software_dependencies/pipeline/pipeline-mike-standard-pipe-v1.py --outdir='%s' -o -d --kev=%s --apix=%s --cs=%s --extract=%s --extractScaled=%s --ctfscorelowerlim=%s --ctfreslim=%s --indir='%s' --Gctfpath='/expanse/projects/cosmic2/expanse/software_dependencies/Gctf/Gctf_v1.18_b2_sm60_cu9.1' --relionpath='/cm/shared/apps/spack/gpu/opt/spack/linux-centos8-skylake_avx512/gcc-8.3.1/relion-3.1.1-axhl7egohcygsvf3kbblfh7n5rdb3o7c/bin/' --cryolo_gmodel='/expanse/projects/cosmic2/expanse/software_dependencies/cryolo_gmodel_files/gmodel_phosnet_202005_N63_c17.h5' --cryolo_env='/expanse/projects/cosmic2/expanse/conda/cryolo/' %s>>stdout.txt 2>>stderr.txt
mv %s %s/
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], nodes,1,jobdir,outputdir,kev,apix,cs,extract,extractScaled,ctfscore,ctfreslim,DirToSymLink,motioninput,outputdir,userglobusdir)
        runfile = "./batch_command.run"
        statusfile = "./batch_command.status"
        cmdfile = "./batch_command.cmdline"
        debugfile = "./nsgdebug"
        FO = open(runfile, mode='w')
        FO.write(text)
        FO.flush()
        os.fsync(FO.fileno())
        FO.close()
        sys.exit()
        rc = submitJob(job_properties=jobproperties_dict, runfile='batch_command.run', statusfile='batch_command.status', cmdfile='batch_command.cmdline')

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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --licenses=cosmic:1
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

if jobtype == 'isac':
        relion_command,outdir,out_destination,runhours,nodes,numiters,worksubdir,partition,gpuextra1,gpuextra2,gpuextra3,mpi_to_use,newstarname=prepareRelionRun(args)
        runminutes = math.ceil(60 * runhours)
        instar=relion_command 
        cmdline=args['commandline'].split()
        totEntries=len(cmdline)
        counter=0
        options=''
        while counter < totEntries:
                entry=cmdline[counter]
                if entry == '--CTF':
                    options=options+' --CTF'
                if 'maxit' in entry: 
                    options=options+' ' + entry
                if 'minimum' in entry: 
                    options=options+' ' + entry
                if 'img_per' in entry: 
                    options=options+' ' + entry
                if 'radius=' in entry: 
                    options=options+' ' + entry
                counter=counter+1
        sphiredir='sphire-%s' %(''.join(random.choices(string.ascii_uppercase + string.digits, k=8)))
        starconvert='isac-converted-%s.star' %(''.join(random.choices(string.ascii_uppercase + string.digits, k=8)))
        outdir='output-%s' %(sphiredir)
        for line in open(instar,'r'):
            if 'mrc' in line:
                for entry in line.split():
                    if 'mrc' in entry:
                        f=entry
        f=f.split('@')[-1]
        f=f.split('/')
        del f[-1]
        f='/'.join(f)
        star=instar.split("/")
        del star[-1]
        star='/'.join(star)
        additionalpath=f.replace(star,'')

        isac_cmd='''conda activate /expanse/projects/cosmic2/expanse/conda/pyem/
/expanse/projects/cosmic2/expanse/software_dependencies/pyem/star.py %s %s --relion2 
conda deactivate 
#conda activate /expanse/projects/cosmic2/expanse/software_dependencies/EMAN2
source /expanse/projects/cosmic2/expanse/software_dependencies/sphire-9-22-2/eman2bash.sh
module load gcc/7.2.0
sxrelion2sphire.py %s %s >> stdout.txt 2>> stderr.txt 
e2bdb.py  %s/%s/Particles  --makevstack=bdb:%s/%s/Particles#sphire_stack >> stdout.txt 2>> stderr.txt
mpirun sp_isac2_gpu.py 'bdb:%s/%s/Particles/#sphire_stack' %s %s --gpu_devices=0,1,2,3  >> stdout.txt 2>> stderr.txt
e2proc2d.py %s/ordered_class_averages.hdf %s-ISAC_output_ordered_class_averages.mrcs >> stdout.txt 2>> stderr.txt''' %(instar,starconvert,starconvert,sphiredir,sphiredir,additionalpath,sphiredir,additionalpath,sphiredir,additionalpath,outdir,options,outdir,outdir)
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
        jobstatus.write('COSMIC2 job staged and submitted to Expanse Supercomputer at SDSC.\n\n')
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
#SBATCH --ntasks-per-node=4             # Total number of mpi tasks requested
#SBATCH --cpus-per-task=6
#SBATCH --gpus=4
#SBATCH --no-requeue
#SBATCH --licenses=cosmic:1
module load gpu
module load cuda/9.2.88
source /cm/shared/apps/spack/cpu/opt/spack/linux-centos8-zen2/gcc-10.2.0/anaconda3-2020.11-weucuj4yrdybcuqro5v3mvuq3po7rhjt/etc/profile.d/conda.sh 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'], jobdir,isac_cmd)
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

