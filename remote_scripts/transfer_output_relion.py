#!/usr/bin/env python

#Purpose: move output relion directories into user globus transfer directories, numbered correctly
import shutil 
import subprocess
import glob
import sys
import os 

fullpath='/projects/cosmic2/gateway/globus_transfers/'
username=sys.argv[1]
fullpath=sys.argv[2]
reliondir=sys.argv[3]
destdir=fullpath

#Automatic naming:  
#crYOLO_cosmic  CtfFind_cosmic  Extract_cosmic  MotionCorr_cosmic
#currentjobnum=1+len(glob.glob("%s/crYOLO_cosmic/*" %(destdir)))+len(glob.glob("%s/CtfFind_cosmic/*" %(destdir)))+len(glob.glob("%s/Extract_cosmic/*" %(destdir)))+len(glob.glob("%s/MotionCorr_cosmic/*" %(destdir)))+len(glob.glob('%s/Class2D_cosmic/*' %(destdir)))+len(glob.glob('%s/Class3D_cosmic/*' %(destdir)))+len(glob.glob('%s/Refine3D_cosmic/*' %(destdir)))
currentjobnum=1+len(subprocess.Popen('ls -d %s/*/job???' %(destdir),shell=True, stdout=subprocess.PIPE).stdout.read().strip().split())

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

cmd='mv %s/* %s/%s/%s/' %(reliondir,destdir,reliontype,jobnum)
subprocess.Popen(cmd,shell=True).wait()

cmd='rm -rf %s/' %(reliondir)
subprocess.Popen(cmd,shell=True).wait()

shutil.copyfile(sys.argv[4],'%s/%s/%s/run.out' %(destdir,reliontype,jobnum))
shutil.copyfile(sys.argv[5],'%s/%s/%s/run.err' %(destdir,reliontype,jobnum))
