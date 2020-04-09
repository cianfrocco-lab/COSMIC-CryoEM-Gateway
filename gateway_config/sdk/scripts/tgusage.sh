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
ssh -i ${comet.keyFile} cosmic2@comet.sdsc.edu "mkdir tgusage_${HOSTNAME} 2>/dev/null"
ssh -t -t -i ${comet.keyFile} cosmic2@comet.sdsc.edu \
	"source ${comet.rc}; cd tgusage_${HOSTNAME}; cipres-tgusage -j --username=cosmic2 --begindate=$BEGIN --enddate=$END > tgusage_report.txt"  2> >(grep -v 'Connection to comet.sdsc.edu closed.' 1>&2)
scp -i ${comet.keyFile} cosmic2@comet.sdsc.edu:tgusage_${HOSTNAME}/tgusage_report.txt .

# Need to use full path to file since importTgusage does a cd
importTgusage `pwd`/tgusage_report.txt > `pwd`/tgusage_log.txt 2>&1
