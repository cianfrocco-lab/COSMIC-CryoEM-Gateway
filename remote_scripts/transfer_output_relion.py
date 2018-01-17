#!/usr/bin/env python

#Purpose: move output relion directories into user globus transfer directories, numbered correctly
import subprocess
import glob
import sys
import os 

fullpath='/projects/cosmic2/gateway/globus_transfers/'
username=sys.argv[1]
reliondir=sys.argv[2]
destdir='%s/%s' %(fullpath,username)

#Automatic naming:  
currentjobnum=1+len(glob.glob('%s/Class2D_cosmic/*' %(destdir)))+len(glob.glob('%s/Class3D_cosmic/*' %(destdir)))+len(glob.glob('%s/Refine3D_cosmic/*' %(destdir)))
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


