#!/bin/sh
# for removing old job directories
# written by CIPRES/NSG/COSMIC2 developers
# https://stackoverflow.com/questions/4341630/checking-for-the-correct-number-of-arguments
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 DIRECTORY" >&2
  exit 1
fi
if ! [ -e "$1" ]; then
  echo "$1 not found" >&2
  exit 1
fi
if ! [ -d "$1" ]; then
  echo "$1 not a directory" >&2
  exit 1
fi

rootdir=$1
#basedirs="$rootdir $rootdir/ARCHIVE $rootdir/FAILED"
basedirs="$rootdir/ARCHIVE $rootdir/FAILED"
HOME=~
DAYS=31
#PROJECT=NGBW
PROJECT=COSMIC2

echo `date` : Running ws_cleanup on `/usr/bin/hostname --fqdn`. 
echo "Removing job dirs where nothing has been modified in ${DAYS} days." 

for basedir in $basedirs; do
    for jobdir in $basedir/${PROJECT}-*; do
        if [ -d $jobdir ]; then

            # Find any files modified, or accessed recently, skipping "." itself.
			# Important to use mtime (last time data was changed, ignores changes to inode), as
			# shown with ls -lt (ls -lc includes changes to inodes) since we're changing permissions
			# nightly with a cron job.
            # new_file_count=`find $jobdir -mindepth 1 -atime -14 -or -ctime -7 | wc -l`
            #new_file_count=`find -L $jobdir -mindepth 1 -mtime -${DAYS} | wc -l`
            new_file_count=`find $jobdir -mindepth 1 -mtime -${DAYS} | wc -l`

            # If no young files delete the directory
            if [ $new_file_count -eq 0 ]; then
                echo "Deleting $jobdir" 
		rm -rf $jobdir 
            fi
        fi
    done
done
echo



