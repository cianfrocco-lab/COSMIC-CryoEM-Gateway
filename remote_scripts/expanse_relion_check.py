#!/usr/bin/env python
import sys
import os
import sets
import time
import tarfile
import stat
import string
import subprocess

def jobInQueue():
    cmd = 'squeue -u `whoami` | grep `whoami`'
    p = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    outerr = p.communicate()
    output =  outerr[0]
    err = outerr[1]

    if (len(err) != 0):
        raise SystemError("Error piping qstat thru grep:  %s" % (err))

#login3.stampede(36)$ squeue -u `whoami` | grep `whoami`
#           4850526 development neuron_M  nsguser  R       4:32     16 c557-[302,503,803],c558-[003,102,301,603-604],c560-[503-504,604,701-702,802-804]
#login3.stampede(37)$ squeue -u `whoami` | grep `whoami`
#           4850526 development neuron_M  nsguser CG       5:15      7 c557-302,c558-[003,301,603],c560-[604,701,802]
#login3.stampede(38)$
    output_rows = output.split("\n")
    jobs = []
    for row in output_rows:
        r = row.split()
        if len(r) > 4 and r[4] != "CG":
            jobs.append(r[0])
    return jobs

def main(argv=None):
    sys.stderr.write("start of checkjobs.py\n")
    """
    Usage is:
        checkjobs.py
        Expects a list of jobs on stdin, one per line.  (Just the "short id", the numeric part).
        Returns on stdout, the subset of those jobs that are no longer running or queued.
    """
    if argv is None:
        argv=sys.argv

    # Want to return jobids that are in queryJobs and not in queuedJobs (i.e. jobs that have
    # finished)
    queryJobs = sys.stdin.readlines()
    queryJobs = [ x.strip() for x in queryJobs if x.strip() != '']
    sys.stderr.write("queryJobs (%s)\n" % (string.join(queryJobs,','),))
    queryJobsLists = []
    for qj in queryJobs:
        qj_list = string.split(qj,',')
        queryJobsLists.append(qj_list)

    try:
        queuedJobs = jobInQueue()
    except SystemError, theException:
        print >> sys.stderr, "Caught exception:", theException 
        return 1

    #finishedJobs = list(set(queryJobs) - (set(queuedJobs)))
    finishedJobs = []
    for queryJobsList in queryJobsLists:
        if len(list(set(queryJobsList) - (set(queuedJobs)))) == len(queryJobsList):
            finishedJobs.append(string.join(queryJobsList,','))
        else:
            sys.stderr.write("%s" % (set(queryJobsList) - (set(queuedJobs)),))
    for j in finishedJobs:
        print j
    sys.stderr.write("end of checkjobs.py\n")
'''
    # assume that the modeldir is the only dir in the working dir
    file_list = os.listdir('.')
    modeldir = None
    tar_file_name=None
    for filename in file_list:
        stat_tuple = os.lstat(filename)
        print stat_tuple
        if stat.S_ISDIR(stat_tuple[stat.ST_MODE]):
            modeldir = filename    
    if modeldir == None:
        print "failed to find modeldir in checkjobs!"
        sys.exit(1)




    if tar_file_name is None:
		tar_file_name = modeldir

    tar_file_name += '.tar.gz'

	#creating archive of source directory

    try:
		tarFile = tarfile.open(tar_file_name, mode = 'w:gz')
		tarFile.add(modeldir)
		tarFile.close()
		return True

    except:
		return None

'''
if __name__ == "__main__":
    sys.exit(main())



