#!/bin/sh
#sleep 3600
. /expanse/projects/cosmic2/expanse/gatewaydev/bashrc.dev
#find "$1" -type d -exec chmod g+rwx {} \; -print
#find "$1" -type f -exec chmod g+rw {} \; -print
find "$1" -type d -exec chmod g+rwx {} \;
find "$1" -type f -exec chmod g+rw {} \;
