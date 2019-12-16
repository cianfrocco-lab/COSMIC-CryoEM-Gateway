#!/opt/python/bin/python3
# script to rerun or run gateway-submit-attributes
# https://xsede-xdcdb-api.xsede.org/api/gateways

import sys
import os
import argparse
import subprocess
import uuid
import traceback
import string
import re
import pickle

def warn(message, subject, recipient) :
    print('message: ({}) subject: ({}) recipient: ({})'.format(message, subject, recipient))
    cmd = ECHO + ' ' + "'{}'".format(message) + ' | ' + MAILX + ' -s ' + '"%s"' % subject + " %s" % recipient
    os.system(cmd)


def send_attributes(args_dict):
    global command_completed
    argsfound = args_dict.keys()
    if 'curlcommand' in argsfound and args_dict['curlcommand'] != None:
        CURL = args_dict['curlcommand']
    if 'url' in argsfound and args_dict['url'] != None:
        URL = args_dict['url']
    if 'debug' in argsfound and args_dict['debug'] != None:
        DEBUGARGS = ['--data-urlencode', 'debug=x']
    else:
        DEBUGARGS = []
    argmissing = False
    expected_list = ('gatewayuser', 'xsederesourcename', 'jobid', 'submittime')
    for arg in expected_list:
        # raise exception, if something is missing
        # could use assert statement for that, but the docs say assert
        # is a no-op if optimization is requested.
        if not arg in argsfound or args_dict[arg] == None:
            raise AssertionError('missing or non arg ({}) args_dict is: ({})'.format(arg, args_dict))
        
    if argmissing == False:
        command_list = [CURL, '-XPOST', '--data', '@{}'.format(args_dict['apikey']), '--data-urlencode', 'gatewayuser={}'.format(args_dict['gatewayuser']), '--data-urlencode', 'xsederesourcename={}'.format(args_dict['xsederesourcename']), '--data-urlencode', 'jobid={}'.format(args_dict['jobid']), '--data-urlencode', 'submittime={}'.format(args_dict['submittime'])]
        command_list = command_list + DEBUGARGS +  [URL,]
        save_dict['command_list'] = command_list
        #print(command_list)
        command_completed = subprocess.run(command_list, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=False, timeout=60)
        #print(command_completed)
        #print('cc.stderr: ({})'.format(command_completed.stderr))
        #print('cc.stdout: ({})'.format(command_completed.stdout))
        # check output for readback of attributes, if that fails, store
        # output should be of this form:
        # Job attributes:
        # Job: 4937919
        # Job submitted: 2019-05-31 14:32:00-0700
        # Resource Name: comet.sdsc.xsede
        # Gateway Name: xsede-example-gateway
        # Gateway User: John Doe
        output_pat = '''Job attributes:
  Job: (?P<jobid>\d+)
  Job submitted: (?P<submittime>.*)$
  Resource Name: (?P<xsederesourcename>\S+)$
  Gateway Name: (?P<gatewayname>.*)$
  Gateway User: (?P<gatewayuser>.*)$
(Debug specified. Attributes not staged)*'''
        output_reo = re.compile(output_pat, flags=re.MULTILINE)
        #print(command_completed.stdout.decode())
        #print(command_completed.stderr.decode())
        output_mo = output_reo.match(command_completed.stdout.decode())
        if (output_mo.group('jobid') != None and 
           output_mo.group('submittime') != None and 
           output_mo.group('xsederesourcename') != None and
           output_mo.group('gatewayname') and
           output_mo.group('gatewayuser')):
            pass
        else:
            raise AssertionError(command_completed)
        return command_completed

try:
    # if we have args, check them for job info
    # expecting:
    # --gatewayuser="Firstname Lastname" --xsederesourcename=comet.sdsc.edu
    # --jobid=111111111 --submittime="2019-05-31 14:32 PST"
    parser = argparse.ArgumentParser(description='Submit gateway attributes')
    parser.add_argument('--gatewayuser', dest='gatewayuser', action='store')
    parser.add_argument('--xsederesourcename', dest='xsederesourcename', action='store')
    parser.add_argument('--jobid', dest='jobid', action='store')
    parser.add_argument('--submittime', dest='submittime', action='store')
    parser.add_argument('--curlcommand', dest='curlcommand', action='store')
    parser.add_argument('--pickledir', dest='pickledir', action='store')
    parser.add_argument('--echocommand', dest='echocommand', action='store')
    parser.add_argument('--mailxcommand', dest='mailxcommand', action='store')
    parser.add_argument('--emailrecipient', dest='emailrecipient', action='store')
    parser.add_argument('--url', dest='url', action='store')
    parser.add_argument('--apikey', dest='apikey', action='store')
    parser.add_argument('--debug', dest='debug', action='store_true')
    args = parser.parse_args()
    args_dict = vars(args)
    argsfound = args_dict.keys()
    if 'pickledir' in argsfound and args_dict['pickledir'] != None:
        PICKLEDIR = args_dict['pickledir']
    if 'echocommand' in argsfound and args_dict['echocommand'] != None:
        ECHO = args_dict['echocommand']
    if 'mailxcommand' in argsfound and args_dict['mailxcommand'] != None:
        MAILX = args_dict['mailxcommand']
    if 'apikey' in argsfound and args_dict['apikey'] != None:
        APIKEY = args_dict['apikey']
    if 'emailrecipient' in argsfound and args_dict['emailrecipient'] != None:
        EMAILRECIPIENT = args_dict['emailrecipient']
    # save attributes in a file
    save_dict = args_dict
    rerunlist = []
    for arg in sys.argv:
        rerunlist.append('"\'' + arg + '\'"')
    rerunstring = ' '.join(rerunlist)
    save_dict['rerunstring'] = rerunstring
    myuuid = uuid.uuid1()
    mypicklefile = PICKLEDIR + '/' + myuuid.hex
    MYPICKLEFD = open(mypicklefile,mode='wb')
    pickle.dump(save_dict, MYPICKLEFD, protocol=0)
    MYPICKLEFD.close()

    send_attributes(args_dict)

    # in pickle file
    # need to handle multiple instances running over each other
    # file locking on NFS filesystems not easy for me.  Maybe
    # give each process its own pickle file, then for the re-run
    # open all pickle files in the directory read-only, re-run
    # failures and record success in the process's pickle file.
        

    # end of missing args stanza, if we got here, we can remove
    # the rerun file
    os.unlink(mypicklefile)
    
    # no exceptions, so interface must be up.  Try rerunning any
    # failed ones in rerunfiles dir.
    # rerun any old failed gsas
    rerunfiles = os.listdir(PICKLEDIR)
    for rerunfile in rerunfiles:
        fullpath = PICKLEDIR + '/' + rerunfile
        if os.path.isfile(fullpath) and os.path.getsize(fullpath) > 0:
            FO = open(fullpath,'rb')
            saved_dict = pickle.load(FO)
            # override some non-attribute args:
            if 'curlcommand' in argsfound and args_dict['curlcommand'] != None:
                saved_dict['curlcommand'] = args_dict['curlcommand']
            if 'url' in argsfound and args_dict['url'] != None:
                saved_dict['url'] = args_dict['url']
            if 'debug' in argsfound and args_dict['debug'] != None:
                saved_dict['debug'] = args_dict['debug']
            send_attributes(saved_dict)
        # if we got here, no exceptions raised, so remove the record file.
        os.unlink(fullpath)

except :
    print(' '.join(sys.argv))
    info_tuple = sys.exc_info()
    info_list = ["%s" % info_tuple[0], "%s" % info_tuple[1], '\n']
    traceback.print_tb(info_tuple[2])
    tb_list = traceback.format_tb(info_tuple[2])
    tb_text = ''.join(info_list + tb_list)
    if 'rerunstring' in locals() and rerunstring != None:
        reprerunstring = rerunstring
    else:
        reprerunstring = ''
    #https://stackoverflow.com/questions/3191289/how-to-remove-m
    if 'command_completed' in locals() and command_completed != None:
        ccout = command_completed.stdout.decode(encoding='ascii',errors='replace').replace('\r', '')
        ccerr = command_completed.stderr.decode(encoding='ascii',errors='replace').replace('\r', '')
    else:
        ccout = ''
        ccerr = ''
    message = tb_text + '\n' + 'Something went wrong with attribute reporting.\nrerun by running rerun-gsa.py again:\n{}\n{}\n{}\n'.format(reprerunstring, ccout, ccerr)
    print(message)
    subject = 'rerun-gsa failure and Exit!'
    recipient = EMAILRECIPIENT
    warn(message, subject, recipient)
    sys.exit(1)

sys.exit(0)
