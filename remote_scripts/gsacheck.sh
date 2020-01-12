#!/bin/sh

#/bin/curl -XPOST --data @$HOME/.xsede-gateway-attributes-apikey https://xsede-xdcdb-api.xsede.org/gateway/v2/jobs
/bin/curl -XPOST --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://xsede-xdcdb-api.xsede.org/gateway/v2/jobs
