#!/bin/sh

source $HOME/.bashrc
PATH=/usr/sbin:$PATH
TODAY=`date +%m-%d-%Y`
#CIPRES_CHARGE=TG-DEB090011
CIPRES_CHARGE=csd547
#IPLANT_CHARGE=TG-MCB110022

cd ~/tgusage
tgusage.sh $1 

usage.py -j  
usage.py -c $CIPRES_CHARGE -a > cipres_tg_jobs.csv
usage.py -c $CIPRES_CHARGE -t > cipres_su_per_user_total.csv
usage.py -c $CIPRES_CHARGE -p > cipres_su_this_period.csv

usage.py -e -c $CIPRES_CHARGE -t > cipres_su_per_email_total.csv
usage.py -e -c $CIPRES_CHARGE -p > cipres_su_per_email_this_period.csv

# usage.py -c $CIPRES_CHARGE -u > cipres_su_per_user_$TODAY.csv

#usage.py -c $IPLANT_CHARGE -a > iplant_tg_jobs.csv
#usage.py -c $IPLANT_CHARGE -t > iplant_su_per_user_total.csv
#usage.py -c $IPLANT_CHARGE -p > iplant_su_this_period.csv

# usage.py -c $IPLANT_CHARGE -u > iplant_su_per_user_$TODAY.csv

#usage.py -c $CIPRES_CHARGE -w 

