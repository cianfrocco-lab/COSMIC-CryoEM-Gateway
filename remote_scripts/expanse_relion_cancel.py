#!/usr/bin/env python
import sys
import os
import getopt
import string
import subprocess

def deleteJob(jobid, workingdir):
    jobids = jobid.split(',')
    for jobid in jobids:
        if os.path.isfile(workingdir + "cancelJobs"):
            print("In dependency job cancel")
            os.chdir(workingdir)
            cmd = "./cancelJobs %s" %  jobid
        else:
            #cmd = "qdel %d" % jobid
            cmd = "scancel %s" % jobid
        p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        outerr = p.communicate()
        output =  outerr[0]
        err = outerr[1]
        if (p.returncode != 0):
            raise SystemError("Error running '%s', return code is %d. stdout is '%s', stderr is '%s'" % (cmd,
                p.returncode, output, err))


def main(argv=None):
    """
    Usage is:
    delete.py -j jobid [-u url] -d workingdir
    """
    if argv is None:
        argv=sys.argv

    jobid = url = None
    options, remainder = getopt.getopt(argv[1:], "-j:-u:-d:")
    for opt, arg in options:
        if opt in ("-j"):
            jobid = arg
        elif opt in ("-u"):
            url = arg
        elif opt in ("-d"):
            workingdir = arg

    try:
        if not jobid:
            raise SystemError("Internal error, delete.py invoked without jobid.")
        deleteJob(jobid, workingdir)
    except SystemError as theException:
        sys.stderr.write("Caught exception: {}\n".format(theException))
        return 1


if __name__ == "__main__":
    sys.exit(main())
    


