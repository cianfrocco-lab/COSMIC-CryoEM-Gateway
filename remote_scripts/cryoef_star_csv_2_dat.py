#!/usr/bin/env python
import sys
import os 

def star2dat(instar,outdat): 

    #Determine if relion-3.1+ or before relion-3.1: if data_optics present?
    readstar=open(instar,'r')
    relion31=False
    TiltColNum=-1
    for line in readstar:
        if 'data_optics' in line: 
            relion31=True
        if '_rlnAngleTilt' in line: 
            TiltColNum=line.split()[-1].split('#')[-1]
        if '_rlnAngleRot' in line:
            RotColNum=line.split()[-1].split('#')[-1]
    readstar.close()

    if float(TiltColNum)<0:
        print('Error: _rlnAngleTilt not found')
        sys.exit()
	
    outfile=open(outdat,'w')
    #Parse star file
    readstar=open(instar,'r')
    for line in readstar: 
        if relion31 is True:
            if len(line.split())<20: 
                continue
            angletilt=line.split()[int(TiltColNum)-1]
            anglerot=line.split()[int(RotColNum)-1]
            outfile.write('%s\t%s\n' %(anglerot,angletilt))
        if relion31 is False:
            if len(line)<30:
                continue
            angletilt=line.split()[int(TiltColNum)-1]
            anglerot=line.split()[int(RotColNum)-1]
            outfile.write('%s\t%s\n' %(anglerot,angletilt))
    readstar.close()

infile=sys.argv[1]

if '.star' in infile: 
    star2dat(infile,'%s.dat' %(infile.split('.star')[0]))
