#!/usr/bin/env python

#Purpose: move output from isonet into user globus transfer directories, numbered correctly
import time 
import shutil 
import subprocess
import glob
import sys
import os 
import string 
import random 
import glob 

GLOBUSTRANSFERSDIR = os.environ['GLOBUSTRANSFERSDIR']
REMOTESCRIPTSDIR = os.environ['REMOTESCRIPTSDIR']
TGUSAGEDIR = os.environ['TGUSAGEDIR']
fullpath=GLOBUSTRANSFERSDIR + '/'
fullpath=sys.argv[1]
destdir=fullpath

outdir='%s/isonetOutput%i' %(destdir,time.time())
print(outdir)
os.makedirs('%s' %(outdir))

cmd='cp -r isonet_out/*h5 "%s/"' %(outdir)
print(cmd)
subprocess.Popen(cmd,shell=True).wait()

cmd='rm -rf isonet_out/data/'
print(cmd)
subprocess.Popen(cmd,shell=True).wait()

cmd='rm -rf isonet_out/training_noise/'
print(cmd)
subprocess.Popen(cmd,shell=True).wait()

cmd='cp batch_submit.sh "%s/"' %(outdir)
print(cmd)
subprocess.Popen(cmd,shell=True).wait()

