#!/usr/bin/env python

#Purpose: Monitor RELION job on Comet
#-> To run: ./monitor_relion_job.py Class2D /path/to/outputs [jobID] [# iters] 

import subprocess
import os 
import sys
import glob 

jobtype=sys.argv[1]
datapath=sys.argv[2]
jobid=sys.argv[3]
numiters=sys.argv[4]

#Hard-coded parameters
numberStableResEstimatesBeforeTerm=2
running=True
termflag=0
while running is True: 

	if jobtype == 'Class2D' or jobtype == 'Class3D': 
	
		#Check resolution stability
		optimiserlist=sorted(glob.glob('%s/*optimiser.star' %(datapath)))

		if len(optimiserlist) > 0:
			for optfile in optimiserlist: 
				check=subprocess.Popen('cat %s | grep _rlnNumberOfIterWithoutResolutionGain' %(optfile),shell=True, stdout=subprocess.PIPE).stdout.read().strip().split()[-1]
				if float(check) > numberStableResEstimatesBeforeTerm: 
					termflag=1

	if os.path.exists('%s/run_it%03i_data.star' %(datapath,int(numiters))): 
		running=False
		termflag=0 

	if termflag == 1: 
		cmd='scancel %s' %(jobid)
		subprocess.Popen(cmd,shell=True).wait()
		running=False

