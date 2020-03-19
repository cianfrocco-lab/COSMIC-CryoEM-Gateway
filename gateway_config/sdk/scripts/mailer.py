#!/usr/bin/env python
"""
    Functions for sending email from cipres cron jobs. 

    Note that caller will get exceptions if there's a problem talking to the smtp server or the
    email is badly formed, but not if the mail can't be delivered.  If mail can't be delivered
    to one or more of the recipients the fromaddr account can expect to receive an email
    about it though.

    Sorry, lots of stuff is hardcoded here.  See sendMail function below for a fairly generic but
    limited method to send an email message.

"""
import sys
import os
import re
import string
import subprocess
import tempfile
import getopt
import smtplib
import pystache
if sys.version_info.major == 2:
    from email.MIMEMultipart import MIMEMultipart
    from email.MIMEText import MIMEText
else:
    from email.mime.multipart import MIMEMultipart
    from email.mime.text import MIMEText

ccaddr = "${email.adminAddr}"
fromaddr = "${email.adminAddr}" 

passwordFile = os.path.expandvars("${SDK_VERSIONS}/db_password.txt")

def overLimitWarning(toaddr, username, template, hours_used, application_name, portal, email):
    msg = MIMEMultipart('related')
    msg['From'] = fromaddr
    msg['To'] = toaddr
    msg['Cc'] = ccaddr

    with open(template) as f:
        templateContents = f.read()
    contents = pystache.render(templateContents, 
                                {'hours_used' : hours_used, 
                                'application_name' : application_name,
                                'username': username,
                                'portal_url': portal,
                                'email': email} )

    # Subject of email is taken from first (non-empty) line of body of email 
    lines  = contents.splitlines()
    msg['Subject'] =  [ s for s in lines if s ][0]

    alternatives = MIMEMultipart('alternative')
    msg.attach(alternatives)
    alternatives.attach(MIMEText(contents, 'plain'));

    mail = smtplib.SMTP("${email.smtpServer}", ${email.smtpServer.port})
    if "${mailSender}" == "gmail.mailSender" :
        mail.ehlo()
        mail.starttls()
        mail.login("${mailSender.gmail.username}", "${mailSender.gmail.password}")
        

    # mail.set_debuglevel(1)
    toaddrs = [toaddr] + [ccaddr]

    # print "Sending mail from %s, to %s" % (fromaddr, toaddrs)
    mail.sendmail(fromaddr, toaddrs, msg.as_string())
    mail.quit()

def sendMail(toA, fromA, ccA, subject, file): 
    # print "sendMail(%s, %s, %s)" % (toA, subject, file)

    msg = MIMEMultipart('related')
    msg['From'] = fromA
    msg['To'] = toA
    msg['Cc'] = ccA
    if subject:
        msg['Subject'] = subject 
    alternatives = MIMEMultipart('alternative')
    msg.attach(alternatives)

    plain= open(file, 'r').read()
    alternatives.attach(MIMEText(plain, 'plain'));

    mail = smtplib.SMTP("${email.smtpServer}", ${email.smtpServer.port})
    if "${mailSender}" == "gmail.mailSender" :
        mail.ehlo()
        mail.starttls()
        mail.login("${mailSender.gmail.username}", "${mailSender.gmail.password}")

    # mail.set_debuglevel(1)
    toA = [toA] + [ccA]
    mail.sendmail(fromA, toA, msg.as_string())
    mail.quit()

