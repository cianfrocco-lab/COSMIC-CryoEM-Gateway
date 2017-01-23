#!/bin/bash

# Bash wrapper to perform pre-processing for the Relion 2D task
# Author: Mona Wong, 2016
# Functions
#    1.	Get the zip file
#    2.	Unzip to data/ subdirectory.  If unzip created another subdirectory
#	in data/ then we'll just move it up one level and remove data/.  This
#	way is more robust than using unzip -l and parsing the string output
#	to figure out if there is a subdirectory in the zip file.  Different
#	zip programs can produce different listing style.
#    3. Get the .star filename
# Return	0 - success
#		1 - missing/multiple zip file found
#		2 - unzip error encountered
#		3 - problem moving zip subdirectory
#		4 - no .star

DATA_SUBDIR="data/"

# Function to print to both stdout and stderr
echoerr() { echo "$@" ; echo "$@" 1>&2; }

# Check for a single zip file
#ZIP_FILE=(*.zip)
#COUNT=${#ZIP_FILE[@]}
#echo COUNT $COUNT
#if [ $COUNT -ne 1 ]
#then
#    echoerr "Wrapper error: missing/multiple zip file found"
#    exit 1
#fi

# Get --o argument and create output/ directory
while [[ $# -gt 1 ]]
do
    key="$1"
    case $key in
	--o )
	OUTPUT_DIR=$2
	#echo OUTPUT_DIR $OUTPUT_DIR
	`mkdir $OUTPUT_DIR`
	shift
	;;

	--i )
	ZIP_FILE=$2
	shift
	;;

	*)
	# Do nothing
	;;
    esac
    shift
done

# Unzip file into data/
ZIP_OUTPUT=`unzip -n -d $DATA_SUBDIR $ZIP_FILE`
ZIP_RESULT=$?
if [ $ZIP_RESULT -ne 0 ]
then
    echoerr "Wrapper error: problem encountered during unzip"
    exit 2
fi

# If unzip produced a subdirectory in data/ then we'll move that sudirectory up
# and remove data/
UNZIP_SUBDIR=($DATA_SUBDIR"*")
#echo UNZIP_SUBDIR ${UNZIP_SUBDIR[0]}
COUNT=${#UNZIP_SUBDIR[@]}
#echo COUNT $COUNT
if [ $COUNT -ne 1 ] || [ ! -d ${UNZIP_SUBDIR[0]} ]
then
    echoerr "Wrapper error: problem preparing unzip data"
    exit 3
fi

# Now move the unzipped subdirectory out of data/
NEW_DATA_SUBDIR="`echo ${UNZIP_SUBDIR[0]} | cut -d / -f 2`"
`mv ${UNZIP_SUBDIR[0]} .`
`rm -fr $DATA_SUBDIR`
DATA_SUBDIR=$NEW_DATA_SUBDIR"/"
echo new DATA_SUBDIR $DATA_SUBDIR


# Find -i <input>.star
#STAR_FILE=($DATA_SUBDIR"*.star")
#NO_STAR_FILE=$DATA_SUBDIR"\*.star"
#echo STAR_FILE ${STAR_FILE[0]}
##if [[ "${STAR_FILE[0]}" =~ ".**.*" ]]
#echo NO_STAR_FILE $NO_STAR_FILE
#if [ "${STAR_FILE[0]}" == "$DATA_SUBDIR'*.star'" ]
#then
#    echo contains asterick!
#    echoerr "Wrapper error: no .star file found"
#    exit 4
#fi

# Find -i <input>.star
STAR_FILE=(`find $DATA_SUBDIR -maxdepth 1 -mindepth 1 -name "*.star"`)
echo STAR_FILE $STAR_FILE
COUNT=${#STAR_FILE[@]}
#echo COUNT $COUNT
#echo first ${STAR_FILE[0]}
if [ $COUNT -ne 1 ]
then
    echoerr "Wrapper error: no/multiple .star files found"
    exit 5
fi

# Calculate nodes for scheduler.conf
WC=(`wc -l $STAR_FILE`)
#echo WC $WC
LINES=${WC[0]}
#echo LINES $LINES
SCHEDULER_FILE="scheduler.conf"
SCHEDULER_STRING="nodes="
if [ $LINES -le 20000 ]
then
    echo $SCHEDULER_STRING"2" >> $SCHEDULER_FILE
elif [ $LINES -le 40000 ]
then
    echo $SCHEDULER_STRING"3" >> $SCHEDULER_FILE
elif [ $LINES -le 60000 ]
then
    echo $SCHEDULER_STRING"4" >> $SCHEDULER_FILE
elif [ $LINES -le 80000 ]
then
    echo $SCHEDULER_STRING"5" >> $SCHEDULER_FILE
elif [ $LINES -le 100000 ]
then
    echo $SCHEDULER_STRING"6" >> $SCHEDULER_FILE
elif [ $LINES -le 120000 ]
then
    echo $SCHEDULER_STRING"7" >> $SCHEDULER_FILE
elif [ $LINES -le 140000 ]
then
    echo $SCHEDULER_STRING"8" >> $SCHEDULER_FILE
elif [ $LINES -le 160000 ]
then
    echo $SCHEDULER_STRING"9" >> $SCHEDULER_FILE
elif [ $LINES -le 180000 ]
then
    echo $SCHEDULER_STRING"10" >> $SCHEDULER_FILE
elif [ $LINES -le 200000 ]
then
    echo $SCHEDULER_STRING"11" >> $SCHEDULER_FILE
elif [ $LINES -gt 220000 ]
then
    echo $SCHEDULER_STRING"12" >> $SCHEDULER_FILE
else
    echo $SCHEDULER_STRING"2" >> $SCHEDULER_FILE
fi

# Now finally run the Relion command
#echo run $*

exit 0
