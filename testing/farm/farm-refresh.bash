#!/bin/bash

# Run 3 htmltopdf containers and haproxy
set -x

HTML_TO_PDF_IMAGE="aashipov/htmltopdf:cdp"

HAPROXY_IMAGE="haproxy:2.4"
THIS_DIR=$(pwd)
NETWORK_NAME=htmltopdf
HAPROXY=${NETWORK_NAME}-haproxy
VOLUMES_HAPROXY="-v /${THIS_DIR}/haproxy/:/usr/local/etc/haproxy/:ro"
PORT_8080=8080
PORTS_TO_PUBLISH_HAPROXY="-p${PORT_8080}:${PORT_8080} -p9999:9999"

docker pull ${HTML_TO_PDF_IMAGE}
docker pull ${HAPROXY_IMAGE}
source ${THIS_DIR}/down.bash

docker network create -d bridge ${NETWORK_NAME}

i=0
while [ $i -ne 3 ]
do
        i=$(($i+1))
        node_name="${NETWORK_NAME}${i}"
        outer_port=$((${PORT_8080}+${i}))
        docker run -d --name=${node_name} --hostname=${node_name} --net=${NETWORK_NAME} "-p${outer_port}:${PORT_8080}" ${HTML_TO_PDF_IMAGE}
done

docker run -d --name=${HAPROXY} --hostname=${HAPROXY} --net=${NETWORK_NAME} -eHTMLTOPDF_HOST=192.168.1.120 ${PORTS_TO_PUBLISH_HAPROXY} ${VOLUMES_HAPROXY} ${HAPROXY_IMAGE}
