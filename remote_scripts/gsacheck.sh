#!/bin/sh

#/bin/curl -XPOST --data @$HOME/.xsede-gateway-attributes-apikey https://xsede-xdcdb-api.xsede.org/gateway/v2/jobs
#/bin/curl -XPOST --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://xsede-xdcdb-api.xsede.org/gateway/v2/jobs
/bin/curl -XPOST --data "xsederesourcename=expanse.sdsc.xsede.org" --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://allocations-api.access-ci.org/acdb/gateway/v2/jobs
/bin/curl -XPOST --data "xsederesourcename=expanse-gpu.sdsc.xsede.org" --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://allocations-api.access-ci.org/acdb/gateway/v2/jobs
/bin/curl -XPOST --data "xsederesourcename=unknown" --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://allocations-api.access-ci.org/acdb/gateway/v2/job_attributes
/bin/curl -XPOST --data "xsederesourcename=expanse-gpu.sdsc.xsede.org" --data @/home/cosmic2/.xsede-gateway-attributes-apikey https://allocations-api.access-ci.org/acdb/gateway/v2/jobs
