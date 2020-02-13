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

GLOBUSTRANSFERSDIR = '/projects/cosmic2/gateway/globus_transfers'
REMOTESCRIPTSDIR = '/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts'

#==========================
def randomString(stringLength=40):
    """Generate a random string of fixed length """
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))

#==========================
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

	return newstarname

#==========================
def preparePreprocessingRun(inputline,jobdir):

	#Get information from commandline:
	#Example: --bfactor 100 --ctf_res_lim 6  --cs 2.7 --do_motion --out_box 0 --tile_frames 5 --apix 1 --diameter 200 --movie_binning 1  --out_apix 0 --kev 300 --i mic_upload_test/micrographs.star 

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
        for eindex in range(len(testargs)):
            if testargs[eindex] == '--i':
                input_starfile = testargs[eindex + 1]
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
	#Align movies
	movie_align=False
	ctf_run=False
	cryolo_run=False
	negstain=False
	if '--do_motion' in inputline: 
		movie_align=True
	if '--do_ctf' in inputline: 
		ctf_run=True
	if '--do_cryolo' in inputline: 
		cryolo_run=True
	if '--negative_stain' in inputline: 
		negstain=True
	movie_dose_weighting=False
	if movie_align is True:
		if negstain is True:
			print 'Error: Both motion correction and negative options were chosen. Please fix and resubmit.'
			sys.exit()
		if '--dose' in inputline: 
			movie_dose_weighting=True
		if movie_dose_weighting is True:
			movie_dose_per_frame=float(inputline.split()[returnEntryNumber(inputline,'--dose')])
		if movie_dose_weighting is False:
			movie_dose_per_frame=1
		movie_binning=int(inputline.split()[returnEntryNumber(inputline,'--movie_binning')])
		movie_bfactor=int(inputline.split()[returnEntryNumber(inputline,'--bfactor')])
		movie_tiles=int(inputline.split()[returnEntryNumber(inputline,'--tile_frames')])
		angpix=float(inputline.split()[returnEntryNumber(inputline,'--apix')])
		ctf_kev=int(inputline.split()[returnEntryNumber(inputline,'--kev')])
		if '--gain_ref' in inputline: 
			movie_gain_reference=inputline.split()[returnEntryNumber(inputline,'--gain_ref')]
		
		#Get relative path from starfile 
	        ##Read star file header to get particle name
        	rlno1=open(starfilename,'r')
	        colnum=-1
        	for rln_line in rlno1:
                	if '_rlnMicrographMovieName' in rln_line:
				if len(rln_line.split()) == 2:
	                        	colnum=int(rln_line.split()[-1].split('#')[-1])-1
				if len(rln_line.split()) == 1: 
					colnum=0
	        rlno1.close()
		o1.write('reading rln col=%i' %(colnum))

        	if colnum<0:
			print "Incorrect format of star file. Could not find _rlnImageName in star file header information. Exiting"
		        return 1
        
	if movie_align is False:
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

	if ctf_run is True:
		#Get CTF info: 
		ctf_kev=int(inputline.split()[returnEntryNumber(inputline,'--kev')])
		ctf_cs=float(inputline.split()[returnEntryNumber(inputline,'--cs')])
		ctf_reslim=int(inputline.split()[returnEntryNumber(inputline,'--ctf_res_lim')])
		angpix=float(inputline.split()[returnEntryNumber(inputline,'--apix')])
		ampcontrast=0.1
		if negstain is True:
			ampcontrast=0.25	
	o1.write('here2\n')
	if cryolo_run is True: 
		#Particle stack values	
		particle_diameter_angstroms=int(inputline.split()[returnEntryNumber(inputline,'--diameter')]) #Angstroms
		particle_output_angpix=float(inputline.split()[returnEntryNumber(inputline,'--out_apix')]) #angpix
		if particle_output_angpix == 0: 
			particle_output_angpix=angpix
		particle_output_boxsize_unbinned=int(inputline.split()[returnEntryNumber(inputline,'--out_box')]) #pixels, unbinned
		if particle_output_boxsize_unbinned==0: 
			particle_output_boxsize_unbinned=round(2*particle_diameter_angstroms/angpix)
			#Check if even dimensions
			if particle_output_boxsize_unbinned%2==1: 
				particle_output_boxsize_unbinned=particle_output_boxsize_unbinned+1
		particle_output_diam=round(particle_diameter_angstroms/angpix)
		bg_radius=round(particle_output_diam/2)
		if particle_output_angpix != 0: 
			particle_output_boxsize_binned=round(particle_output_boxsize_unbinned/(particle_output_angpix/angpix))
			if particle_output_boxsize_binned%2==1:
				particle_output_boxsize_binned=particle_output_boxsize_binned+1
			bg_radius=round((particle_output_diam/(particle_output_angpix/angpix))/2)

	#DEBUGGING parameter parsing
	o1.write('preprocessing info\n')
	o1.write('movie align=%s\n' %(movie_align))
	if movie_align is True:
		if movie_dose_weighting is True: 
			o1.write('movie_dose_per_frame=%f\n' %(movie_dose_per_frame))
		o1.write('movie_binning=%i\n' %(movie_binning))
		o1.write('movie_bfactor=%i\n' %(movie_bfactor))
		o1.write('movie_tiles=%i\n' %(movie_tiles))
		if '--gain_ref' in inputline:
			o1.write('gain_ref=%s\n' %(movie_gain_reference))
	if ctf_run is True:
		o1.write('ctf_kev=%i\n' %(ctf_kev))
		o1.write('ctf_cs=%f\n' %(ctf_cs))
		o1.write('ctf_reslim=%i\n' %(ctf_reslim))
		o1.write('angpix=%f\n' %(angpix))
	if cryolo_run is True:
		o1.write('particle_diameter_angstroms=%i\n' %(particle_diameter_angstroms))
		o1.write('particle_output_angpix=%f\n' %(particle_output_angpix))
		o1.write('particle_output_boxsize=%i\n' %(particle_output_boxsize_unbinned))
		o1.write('\n\nFinished\n')

	#Movie align command generation
	movie_align_cmd=''
	if movie_align is True: 
		#Define output directory
		if not os.path.exists('MotionCorr_cosmic'): 
			os.makedirs('MotionCorr_cosmic')
		counter=1
		while counter<1000:
			if not os.path.exists('MotionCorr_cosmic/job%03i' %(counter)): 
				movie_outdir='MotionCorr_cosmic/job%03i' %(counter)
				os.makedirs('MotionCorr_cosmic/job%03i' %(counter))
				counter=10001
			counter=counter+1
		movie_align_cmd='relion_run_motioncorr_mpi --i %s --o %s --first_frame_sum 1 --last_frame_sum -1 --use_own  --j 1 --bin_factor %i --bfactor %i --angpix %f --voltage %i --dose_per_frame %i --preexposure 0 --patch_x %i --patch_y %i --gain_rot 0 --gain_flip 0' %(newstarname,movie_outdir,movie_binning,movie_bfactor,angpix,ctf_kev,movie_dose_per_frame,movie_tiles,movie_tiles)

		#Add other options here: gain reference, dose weighting
		if '--gain_ref' in inputline:
			movie_align_cmd=movie_align_cmd+' --gainref %s' %(movie_gain_reference)
		if movie_dose_weighting is True: 
			movie_align_cmd=movie_align_cmd+' --dose_weighting  --save_noDW'
		
		o1.write('movie_align_cmd:\n')
		o1.write('%s\n' %(movie_align_cmd))

	#Generate CTF command
	if movie_align is True:
		input_mic_file='%s/corrected_micrographs.star' %(movie_outdir)
	if movie_align is False: 
		input_mic_file=input_starfile.split('/')
		del input_mic_file[0]
		input_mic_file='/'.join(input_mic_file)
		input_mic_file=newstarname
		o1.write('%s\n' %(input_mic_file))
		input_starfile=input_mic_file
		input_starfile=newstarname
	o1.write('here3')
	ctf_cmd=''
	ctf_sel_cmd=''
	if ctf_run is True:
		if not os.path.exists('CtfFind_cosmic'):
		        os.makedirs('CtfFind_cosmic')
	        counter=1
        	while counter<1000:
        		if not os.path.exists('CtfFind_cosmic/job%03i' %(counter)):
	                	ctf_outdir='CtfFind_cosmic/job%03i' %(counter)
        	                os.makedirs('CtfFind_cosmic/job%03i' %(counter))
                	        counter=10001
	                counter=counter+1
		ctf_cmd='relion_run_ctffind_mpi --i %s --o %s --CS %f --HT %i --AmpCnst %f --XMAG 10000 --DStep %f --Box 512 --ResMin 30 --ResMax 5 --dFMin 5000 --dFMax 50000 --FStep 500 --dAst 100 --ctffind_exe /home/cosmic2/software_dependencies/ctffind-4.1.13/ctffind --ctfWin -1 --is_ctffind4 --fast_search' %(input_mic_file,ctf_outdir,ctf_cs,ctf_kev,ampcontrast,angpix)

		if movie_dose_weighting is True: 	
			ctf_cmd=ctf_cmd+'   --use_noDW '

		o1.write('\n\nctf_cmd:\n')
		o1.write('%s\n' %(ctf_cmd))

		ctf_out_starfile='%s/micrographs_ctf.star' %(ctf_outdir)

		#Command to select bad mics
        	ctf_sel_cmd='relion_star_handler --i %s --o %s_sel.star --select rlnCtfMaxResolution --maxval %i' %(ctf_out_starfile,ctf_out_starfile[:-5],ctf_reslim)

	picking_cmd=''
	extraction_cmd=''
	mics_for_picking_symlink=''
	cryolo_cp_cmd=''
	o1.write('here5\n')
	if cryolo_run is True:
		if ctf_run is False: 
			ctf_out_starfile=input_starfile
		if not os.path.exists('crYOLO_cosmic'):
        	        os.makedirs('crYOLO_cosmic')
	        counter=1
        	while counter<1000:
                	if not os.path.exists('crYOLO_cosmic/job%03i' %(counter)):
                        	picking_outdir='crYOLO_cosmic/job%03i' %(counter)
	                        os.makedirs('crYOLO_cosmic/job%03i' %(counter))
        	                counter=10001
                	counter=counter+1

		if movie_align is True: 
			mics_for_picking_symlink='GLOBIGNORE="*noDW.mrc" && ln -s %s/%s/*/*.mrc %s/%s/ && unset GLOBIGNORE &&' %(jobdir,movie_outdir,jobdir,picking_outdir)

		if movie_align is False: 
			#Get mic name from starfile
			o1.write('here8\n')
			'''
			for line in open(starfilename,'r'): 
				if len(line)<40: 
					continue
				o1.write('%s\n' %(line))
				l=line.split('/')
				del l[-1]
				mic_dir='\t'.join(l)
			mics_for_picking_symlink='ln -s %s/%s/%s/*.mrc %s/' %(userdir,foldername,mic_dir,picking_outdir)
			'''
			mics_to_symlink=starfilename.split('/')
			del mics_to_symlink[-1]
			mics_to_symlink='/'.join(mics_to_symlink)
			mics_for_picking_symlink='ln -s %s/*.mrc %s/' %(mics_to_symlink,picking_outdir)
				
		#Write out config.json file: 
		config_open=open('/home/cosmic2/software_dependencies/crYOLO/config.json','r')
        	new_config=open('config.json','w')
	        for line in config_open:
        		if 'anchors' in line:
                		line=line.replace('xx','%.f' %(particle_diameter_angstroms/angpix))
	                if negstain is True:
				if '1,' in line:
                        		line=line.replace('1,','1')
			if 'filter' in line: 
				if negstain is True:
					continue
			new_config.write(line)
        	new_config.close()
	        config_open.close()
		o1.write('here7\n')		
		picking_cmd='/opt/miniconda3/envs/cryolo/bin/cryolo_predict.py -c config.json -w /home/cosmic2/software_dependencies/crYOLO/gmodel_phosnet_20190218_loss0042.h5 -i %s/ -o %s/ -t 0.2' %(picking_outdir,picking_outdir)

		o1.write('crYOLO:\n')
		o1.write('%s\n' %(picking_cmd))

		#Extract particles
		if not os.path.exists('Extract_cosmic'):
        	        os.makedirs('Extract_cosmic')
	        counter=1
        	while counter<1000:
                	if not os.path.exists('Extract_cosmic/job%03i' %(counter)):
                        	extract_outdir='Extract_cosmic/job%03i' %(counter)
	                        os.makedirs('Extract_cosmic/job%03i' %(counter))
        	                counter=10001
                	counter=counter+1
	
		#Copy STAR coordinates into correctly named subdirectory
		just_dir=newstarname.split('/')
		del just_dir[-1]
		just_dir='/'.join(just_dir)
		copy_star_into='%s/%s/' %(picking_outdir,just_dir)
		o1.write('copy_star_info=%s' %(copy_star_into))
		os.makedirs(copy_star_into)

		cryolo_cp_cmd='cp %s/STAR/*.star %s' %(picking_outdir,copy_star_into)
		o1.write('\n%s\n' %(cryolo_cp_cmd))
		extraction_cmd='relion_preprocess_mpi --i %s/micrographs_ctf_sel.star --coord_dir %s/ --coord_suffix .star --part_star %s/particles.star --part_dir %s --extract --extract_size %i --norm --white_dust 5 --black_dust 5 --invert_contrast' %(ctf_outdir,picking_outdir,extract_outdir,extract_outdir,particle_output_boxsize_unbinned)

		if particle_output_angpix != 0:
			extraction_cmd=extraction_cmd+' --scale %i --bg_radius %i' %(particle_output_boxsize_binned,bg_radius)
		if particle_output_angpix == 0: 
			extraction_cmd=extraction_cmd+' --bg_radius %i' %(bg_radius)
	
	class2d_cmd=''
	#if '--do_2D' in inputline: 
	#	#Prepare 2D classification

	o1.close()

	return movie_align_cmd,ctf_cmd,ctf_sel_cmd,picking_cmd,extraction_cmd,mics_for_picking_symlink,class2d_cmd,cryolo_cp_cmd,DirToSymLink


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
	userdir='%s/' %(GLOBUSTRANSFERSDIR)+usernamedir
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
                if len(rln_line) >= 40:
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
        #cmd = "%s/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir=%s/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://xsede-xdcdb-api.xsede.org/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede' --jobid='{}' --submittime='{}'" %(REMOTESCRIPTSDIR,REMOTESCRIPTSDIR).format('{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)
        cmd = "{}/rerun-gsa.py --curlcommand='/bin/curl' --apikey='/home/cosmic2/.xsede-gateway-attributes-apikey' --pickledir={}/rerunfiles --echocommand='/bin/echo' --mailxcommand='/bin/mailx ' --emailrecipient='kenneth@sdsc.edu' --url='https://xsede-xdcdb-api.xsede.org/gateway/v2/job_attributes' --gatewayuser='{}' --xsederesourcename='{}.sdsc.xsede' --jobid='{}' --submittime='{}'".format(REMOTESCRIPTSDIR, REMOTESCRIPTSDIR,'{}@cosmic2.sdsc.edu'.format(gateway_user), resource, jobid, timestring)

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

if jobtype == 'micassess':
	formatted_starname=prepareMicassess(args['commandline'],jobdir)
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
conda activate cryoassess 
python /home/cosmic2/software_dependencies/Automatic-cryoEM-preprocessing/micassess.py -i %s -m ~/software_dependencies/model_files/micassess_051419.h5 -o %s_micassess_good.star 
zip -r MicAssess.zip MicAssess/
zip -r /projects/cosmic2/meta-data/%s-micassess.zip MicAssess/
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
        %(partition,jobname, runtime, mailuser, args['account'],jobdir,formatted_starname,formatted_starname[:-5],randomString(40))
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
mpirun -np %i %s --j 5 %s >>stdout.txt 2>>stderr.txt
/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' %s
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > done.txt
""" \
	%(partition,jobname, runtime, mailuser, args['account'], nodes,4,jobdir,outdir.split('_cosmic')[0],outdir,numiters,mpi_to_use,relion_command,gpuextra2,username,out_destination,outdir,relion_command,newstarname)
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
	movie_align_cmd,ctf_cmd,ctf_sel_cmd,picking_cmd,extraction_cmd,mics_for_picking_symlink,class2d_cmd,cryolo_cp_cmd,DirToSymLink=preparePreprocessingRun(args['commandline'],jobdir)

	#General parameters
        totcores=24
	runminutes = math.ceil(600)
        hours, minutes = divmod(runminutes, 60)
        runtime = "%02d:%02d:00" % (hours, minutes)
        o1=open('_JOBINFO.TXT','a')
        o1.write('\ncores=%i\n' %(totcores))
        o1.close()
        shutil.copyfile('_JOBINFO.TXT', '_JOBPROPERTIES.TXT')
        jobproperties_dict = getProperties('_JOBPROPERTIES.TXT')
        mailuser = jobproperties_dict['email']
        jobname = jobproperties_dict['JobHandle']
        partition='compute'
	for line in open('_JOBINFO.TXT','r'):
       		if 'User\ Name=' in line:
			username=line.split('=')[-1].strip()

	jobstatus=open('job_status.txt','w')
	jobstatus.write('COSMIC2 job staged and submitted to Comet Supercomputer at SDSC.\n\n')
	jobstatus.write('Job currently in queue\n\n')
	jobstatus.close()

	#Create command
	runcmd=''
	transfercmd=''
	if len(movie_align_cmd)>0:
		runcmd=runcmd+'mpirun -np %i %s >> stdout.txt 2>> stderr.txt\n' %(totcores,movie_align_cmd)
	
		for entry in movie_align_cmd.split():
                        if 'MotionCorr_cosmic' in entry:
                                job=entry.split('/')[1]
		motiondirname='MotionCorr_cosmic/%s' %(job)		
		transfercmd=transfercmd+"/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' empty\n" %(username,DirToSymLink,motiondirname,movie_align_cmd)

	if len(ctf_cmd) >0: 
		runcmd=runcmd+'mpirun -np %i %s >>stdout.txt 2>>stderr.txt\n' %(totcores,ctf_cmd)
		runcmd=runcmd+'%s\n' %(ctf_sel_cmd)
		for entry in ctf_cmd.split():
        	        if 'CtfFind_cosmic' in entry:
                	        job=entry.split('/')[1]
	        ctfdirname='CtfFind_cosmic/%s' %(job)
        	transfercmd=transfercmd+"/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' empty\n" %(username,DirToSymLink,ctfdirname,ctf_cmd)

	if len(picking_cmd)>0:
		runcmd=runcmd+'%s\n' %(mics_for_picking_symlink)
		runcmd=runcmd+'singularity exec /home/cosmic2/software_dependencies/crYOLO/sdsc-comet-ubuntu-cryolo-cpu.simg %s >>stdout.txt 2>>stderr.txt\n' %(picking_cmd) 
		runcmd=runcmd+'%s\n'  %(cryolo_cp_cmd)
        	for entry in picking_cmd.split():
                	if 'crYOLO_cosmic' in entry:
                        	job=entry.split('/')[1]
        	pickdirname='crYOLO_cosmic/%s' %(job)
        	transfercmd=transfercmd+"/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' empty\n" %(username,DirToSymLink,pickdirname,picking_cmd)

	if len(extraction_cmd)>0:
		runcmd=runcmd+'mpirun -np %i %s >>stdout.txt 2>>stderr.txt\n' %(totcores,extraction_cmd)
		for entry in extraction_cmd.split():
                	if 'Extract_cosmic' in entry:
                        	job=entry.split('/')[1]
        	extractdirname='Extract_cosmic/%s' %(job)
	        transfercmd=transfercmd+"/home/cosmic2/COSMIC-CryoEM-Gateway/remote_scripts/transfer_output_relion.py %s '%s' %s stdout.txt stderr.txt '%s' empty\n" %(username,DirToSymLink,extractdirname,extraction_cmd)

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
#SBATCH --ntasks-per-node=24             # Total number of mpi tasks requested
#SBATCH --no-requeue
export MODULEPATH=/share/apps/compute/modulefiles/applications:$MODULEPATH
export MODULEPATH=/share/apps/compute/modulefiles:$MODULEPATH
module purge
module load gnutools
module load intel/2015.6.233
module load intelmpi/2015.6.233
module load relion/3.0_beta_cpu
module load singularity
date 
cd '%s/'
date +'%%s %%a %%b %%e %%R:%%S %%Z %%Y' > start.txt
echo 'Job is now running' >> job_status.txt
pwd > stdout.txt 2>stderr.txt
%s
%s
echo 'Job finished && date' >> job_status.txt
"""%(partition,jobname, runtime, mailuser, args['account'],jobdir,runcmd,transfercmd)
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

