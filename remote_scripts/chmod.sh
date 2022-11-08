#!/bin/sh
. /expanse/projects/cosmic2/expanse/gatewaydev/bashrc.dev
find "$1" -type d -exec chmod g+rwx {} \; -print
find "$1" -type f -exec chmod g+rw {} \; -print
