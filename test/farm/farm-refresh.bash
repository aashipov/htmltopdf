#!/bin/bash

# Run 3 htmltopdf containers and haproxy
set -x

HTML_TO_PDF_IMAGE="aashipov/htmltopdf"
HAPROXY_IMAGE="haproxy:1.7"
THIS_DIR=$(pwd)
NODE_1=htmltopdf1
NODE_2=htmltopdf2
NODE_3=htmltopdf3
HAPROXY=haproxy
NETWORK_NAME=htmltopdf
VOLUMES_HAPROXY="-v /${THIS_DIR}/haproxy/:/usr/local/etc/haproxy/:ro"

docker pull ${HTML_TO_PDF_IMAGE}
docker pull ${HAPROXY_IMAGE}
source ${THIS_DIR}/down.bash

docker network create -d bridge ${NETWORK_NAME}
docker run -d --name=${NODE_1} --hostname=${NODE_1} --net=${NETWORK_NAME} ${HTML_TO_PDF_IMAGE}
docker run -d --name=${NODE_2} --hostname=${NODE_2} --net=${NETWORK_NAME} ${HTML_TO_PDF_IMAGE}
docker run -d --name=${NODE_3} --hostname=${NODE_3} --net=${NETWORK_NAME} ${HTML_TO_PDF_IMAGE}
docker run -d --name=${HAPROXY} --hostname=${HAPROXY} --net=${NETWORK_NAME} ${PORTS_TO_PUBLISH_HAPROXY} -p 8080:8080 ${VOLUMES_HAPROXY} ${HAPROXY_IMAGE}
