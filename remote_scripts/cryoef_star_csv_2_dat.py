#!/usr/bin/env python
import sys
import os 

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

infile=sys.argv[1]

if '.star' in infile: 
	star2dat(infile,'%s.dat' %(infile.split('.star')[0]))
