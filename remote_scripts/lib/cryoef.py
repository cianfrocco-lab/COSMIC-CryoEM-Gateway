import os

'''
This will handle cryoef data formatting & running
'''

def star2dat(instar,outdat): 

	#Outdat file
	outfile=open(outdat,'w')

	#Determine if relion-3.1+ or before relion-3.1: if data_optics present?
	readstar=open(instar,'r')
	relion31=False
	for line in readstar:
		if 'data_optics' in line: 
			relion31=True
		if '_rlnAngleTilt' in line: 
			TiltColNum=line.split()[-1].split('#')[-1]
		if '_rlnAngleRot' in line:
                        RotColNum=line.split()[-1].split('#')[-1]
	readstar.close()

	#Parse star file
	readstar=open(instar,'r')
	for line in readstar: 
		if len(line.split())<20: 
			continue
		angletilt=line.split()[int(TiltColNum)-1]
		anglerot=line.split()[int(RotColNum)-1]
		outfile.write('%s\t%s\n' %(anglerot,angletilt))
	readstar.close()

def runcryoEF(datfile,sym): 
	'''
	/programs/x/cryoef/1.1.0/bin/cryoEF -f Refine3D/job007/run_data_1000.dat  -b 160  -a 1 -B 116 -D 200 -g C1 -r 3.2
	'''

	cmd='/home/cosmic2/software_dependencies/cryoEF/cryoEF_v1.1.0/PreCompiled/centos5.11/cryoEF -f %s -g %s' %(datfile,sym)
	

