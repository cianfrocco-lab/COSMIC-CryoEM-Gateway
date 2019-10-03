#!/usr/bin/env python

#Purpose: move output relion directories into user globus transfer directories, numbered correctly
import shutil 
import subprocess
import glob
import sys
import os 
import string 
import random 
import glob 

fullpath='/projects/cosmic2/gateway/globus_transfers/'
username=sys.argv[1]
fullpath=sys.argv[2]
reliondir=sys.argv[3]
destdir=fullpath

def randomString(stringLength=40):
    """Generate a random string of fixed length """
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))

def updateStarFiles(indir,olddirname,newdirname): 
	isready=0
	while isready == 0: 
		modelstarlist=glob.glob(indir)
		if len(modelstarlist)>0: 
			isready=1
	for modelstar in modelstarlist: 
		cmd='mv %s %s_tmp.star' %(modelstar,modelstar[:-5])
		subprocess.Popen(cmd,shell=True).wait()
		fopen=open('%s_tmp.star' %(modelstar[:-5]),'r')	
		fout=open(modelstar,'w')
		for line in fopen: 
			if olddirname not in line: 
				fout.write(line)
			if olddirname in line: 
				line=line.replace(olddirname,newdirname)
				fout.write(line)
		os.remove('%s_tmp.star' %(modelstar[:-5]))
#Automatic naming:  
#crYOLO_cosmic  CtfFind_cosmic  Extract_cosmic  MotionCorr_cosmic
#currentjobnum=1+len(glob.glob("%s/crYOLO_cosmic/*" %(destdir)))+len(glob.glob("%s/CtfFind_cosmic/*" %(destdir)))+len(glob.glob("%s/Extract_cosmic/*" %(destdir)))+len(glob.glob("%s/MotionCorr_cosmic/*" %(destdir)))+len(glob.glob('%s/Class2D_cosmic/*' %(destdir)))+len(glob.glob('%s/Class3D_cosmic/*' %(destdir)))+len(glob.glob('%s/Refine3D_cosmic/*' %(destdir)))
currentjobnum=1+len(subprocess.Popen('ls -d "%s"/*/job???' %(destdir),shell=True, stdout=subprocess.PIPE).stdout.read().strip().split())

jobnum='job%03i' %(currentjobnum)

reliontype=reliondir.split('/')[0]

if not os.path.exists('%s/%s' %(destdir,reliontype)): 
	os.makedirs('%s/%s' %(destdir,reliontype))

if os.path.exists('%s/%s/%s' %(destdir,reliontype,jobnum)): 
	isdone=0
	tmpcounter=1
	while isdone==0: 
		jobnum='job%03i_%i' %(currentjobnum,tmpcounter)
		if os.path.exists('%s/%s/%s' %(destdir,reliontype,jobnum)): 
			counter=counter+1
		if not os.path.exists('%s/%s/%s' %(destdir,reliontype,jobnum)):
			isdone=1

os.makedirs('%s/%s/%s' %(destdir,reliontype,jobnum))

if 'star' in sys.argv[7]:

	readydir=0
	while readydir == 0:
        	dirname='/projects/cosmic2/meta-data/'+randomString(40)
	        if not os.path.exists(dirname):
        	        os.makedirs(dirname)
                	os.makedirs('%s/data/' %(dirname))
			readydir=1

	cmd='cp %s/* %s/' %(reliondir,dirname)
	subprocess.Popen(cmd,shell=True).wait()

	datatransfer=sys.argv[7].split('/')
	del datatransfer[-1]
	datatransfer='/'.join(datatransfer)
	cmd='cp -r %s/* %s/data/' %(datatransfer,dirname)
	subprocess.Popen(cmd,shell=True).wait()

	#Update all model.star files
	updateStarFiles('%s/*model.star' %(reliondir),reliondir,'%s/%s/' %(reliontype,jobnum))

if reliontype == 'Extract_cosmic': 
	updateStarFiles('%s/particles.star' %(reliondir),reliondir,'%s/%s/' %(reliontype,jobnum))
if reliontype == 'MotionCorr_cosmic':
	updateStarFiles('%s/corrected_micrographs.star' %(reliondir),reliondir,'%s/%s/' %(reliontype,jobnum))
if reliontype == 'CtfFind_cosmic':
	updateStarFiles('%s/micrographs_ctf.star' %(reliondir),reliondir,'%s/%s/' %(reliontype,jobnum))
	updateStarFiles('%s/micrographs_ctf_sel.star' %(reliondir),reliondir,'%s/%s/' %(reliontype,jobnum))

cmd='mv %s/* "%s"/%s/%s/' %(reliondir,destdir,reliontype,jobnum)
subprocess.Popen(cmd,shell=True).wait()

cmd='rm -rf %s/' %(reliondir)
subprocess.Popen(cmd,shell=True).wait()

shutil.copyfile(sys.argv[4],'%s/%s/%s/run.out' %(destdir,reliontype,jobnum))
shutil.copyfile(sys.argv[5],'%s/%s/%s/run.err' %(destdir,reliontype,jobnum))

#Write submit script: 
o1=open('%s/%s/%s/run_submit.script' %(destdir,reliontype,jobnum),'w')
o1.write('Command run on COSMIC2 server:\n\n%s\n' %(sys.argv[6]))
o1.close()

o1=open('%s/cosmic2_job_info.txt' %(destdir),'a')
o1.write('%s/%s/%s/run_submit.script\n' %(destdir,reliontype,jobnum))
o1.close() 
