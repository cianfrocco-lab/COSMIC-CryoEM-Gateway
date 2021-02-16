#!/bin/bash

if [[ "$OSTYPE" == "darwin"* ]] ; then
	BEGIN=`date -v-30d  +%m-%d-%Y`
else
	BEGIN=`date +%m-%d-%Y --date='1 month ago'`
fi
END=`date +%m-%d-%Y`
HOSTNAME=`hostname`

if [ "$1" != "" ];  then
	BEGIN=$1
fi
if [ "$2" != "" ];  then
	END=$2
fi

if [ "$3" != "" ]; then
	ACCOUNT="$3"
fi

#echo "Importing tgusage data for $BEGIN to $END"

# This gets usage info for all accounts (cosmic2 community account like always, but also individual allocations and iplant)
ssh -i /users/u2/cosmic2-gw/.ssh/id_rsa cosmic2@login.expanse.sdsc.edu "mkdir tgusage.expanse_${HOSTNAME} 2>/dev/null"
ssh -t -t -i /users/u2/cosmic2-gw/.ssh/id_rsa cosmic2@login.expanse.sdsc.edu \
	"source /expanse/projects/cosmic2/expanse/gatewaydev/bashrc.dev; cd tgusage.expanse_${HOSTNAME}; cipres-tgusage -j --username=cosmic2 --begindate=$BEGIN --enddate=$END > tgusage.expanse_report.txt"  2> >(grep -v 'Connection to login.expanse.sdsc.edu closed.' 1>&2)
scp -i /users/u2/cosmic2-gw/.ssh/id_rsa cosmic2@login.expanse.sdsc.edu:tgusage.expanse_${HOSTNAME}/tgusage.expanse_report.txt .

# Need to use full path to file since importTgusage does a cd
importTgusage `pwd`/tgusage.expanse_report.txt > `pwd`/tgusage.expanse_log.txt 2>&1
