#!/usr/bin/env python
# as a temporary measure, disable users who have used too much SU
USERDISABLESCRIPT='/projects/cosmic2/gateway/scripts/userCanSubmit'
MAXSU=3000
PROTECTEDUSERS=('mona@xsede.org', 'mcianfro@umich.edu')
ECHO='/usr/bin/echo'
MAILX='/usr/bin/mailx'
WARNRECIPIENTS='kenneth@sdsc.edu,kenneth808@protonmail.com,mcianfro@umich.edu,mona@sdsc.edu'

import subprocess
import time
import os

def warn(message, subject, recipient) :
    cmd = ECHO + ' ' + "'%s'" % message + ' | ' + MAILX + ' -s ' + '"%s"' % subject + " %s" % recipient
    os.system(cmd)


def timedrun(command,timeout) :
    child_obj = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    returncode = None
    iterations = 0
    while returncode == None and iterations <= timeout :
        returncode = child_obj.poll()
        if returncode == None :
            iterations = iterations + 1
            time.sleep(1)
        else :
            break
    if returncode == None :
        # child failed to beat timeout
        print "(%s) failed to return in (%s)!" % (command, timeout)
        os.kill(child_obj.pid)
        return ("timedrun (%s) failed!" % (command,), -1, '')
    else :
        (stdout, stderr) = child_obj.communicate()
        return (stdout, returncode, stderr)

# read /users/u2/cosmic2-gw/tgusage/cipres_su_per_user_total.csv
import re
headerline_pat = r"^username\s+institution\s+user_id\s+email\s+sum\(tgusage.su\)\s*"
headerline_reo = re.compile(headerline_pat)
userline_pat = r"^(?P<username>\S+)\s+(?P<institution>.*)\s+(?P<user_id>\d+)\s+(?P<email>\S+)\s+(?P<su>\d+)\s*$"
userline_reo = re.compile(userline_pat)
disableduser_pat = r"Task submission disabled for user \S+\s*"
disableduser_reo = re.compile(disableduser_pat)
import fileinput
starteduserlines = False
for line in fileinput.input('/users/u2/cosmic2-gw/tgusage/cipres_su_per_user_total.csv'):
    print("processing line: ({})".format(line))
    if starteduserlines == False:
        if headerline_reo.match(line):
            print("matches header line ({})".format(line))
            starteduserlines = True
        else:
            print("does not match header line ({})".format(line))
        continue
    userline_mo = None
    userline_mo = userline_reo.match(line)
    if userline_mo == None:
        print("failed to match {}".format(line))
    else:
        print("matched ({}) username: ({}) institution: ({}) user_id: ({}) email: ({}) su: ({})".format(line, userline_mo.group('username'), userline_mo.group('institution'), userline_mo.group('user_id'), userline_mo.group('email'), userline_mo.group('su')))
        if not userline_mo.group('username') in PROTECTEDUSERS:
            if int(userline_mo.group('su')) > MAXSU:
                # is user already disabled?
                cmdstring = USERDISABLESCRIPT + ' ' + userline_mo.group('username')
                output, returncode, error = timedrun(cmdstring, 60)
                if disableduser_reo.match(output):
                    print("username ({}) already disabled output ({}) returncode ({}) error ({})".format(userline_mo.group('username'), output, returncode, error))
                else:
                    print("disabling submit by ({}) with SU ({})".format(userline_mo.group('username'),userline_mo.group('su')))
                    #call userCanSubmit username false
                    cmdstring = USERDISABLESCRIPT + ' ' + userline_mo.group('username') + ' false'
                    output, returncode, error = timedrun(cmdstring, 60)
                    print("running ({}) returned output ({}) returncode ({}) error ({})".format(cmdstring, output, returncode, error))
                    warn("running ({}) returned output ({}) returncode ({}) error ({})".format(cmdstring, output, returncode, error), "user ({}) disabled for high usage ({})".format(userline_mo.group('username'), userline_mo.group('su')), WARNRECIPIENTS)
