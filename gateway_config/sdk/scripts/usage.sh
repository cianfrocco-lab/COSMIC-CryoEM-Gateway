#!/bin/sh

#source $HOME/.bashrc
export PATH=/usr/sbin:$PATH:$SDK_VERSIONS
TODAY=`date +%m-%d-%Y`
#CIPRES_CHARGE=TG-DEB090011
CIPRES_CHARGE=csd547
CIPRES_CHARGE=TG-MCB170058
#IPLANT_CHARGE=TG-MCB110022

#source /users/u2/cosmic2-gw/gatewayVE/bin/activate

cd ${SDK_VERSIONS}
if test ! -d tgusage 
then
  mkdir tgusage
fi
cd ${SDK_VERSIONS}/tgusage
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

