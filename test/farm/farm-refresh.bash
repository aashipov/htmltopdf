#!/bin/bash

# Run 3 htmltopdf containers and haproxy
set -x

HTML_TO_PDF_IMAGE="aashipov/htmltopdf:chromedp"
HAPROXY_IMAGE="haproxy"
THIS_DIR=$(pwd)
NODE_NAMES=("htmltopdf1" "htmltopdf2" "htmltopdf3")
HAPROXY=htmltopdf-haproxy
NETWORK_NAME=htmltopdf
VOLUMES_HAPROXY="-v /${THIS_DIR}/haproxy/:/usr/local/etc/haproxy/:ro"
PORTS_TO_PUBLISH_HAPROXY="-p8080:8080 -p9999:9999"

docker pull ${HTML_TO_PDF_IMAGE}
docker pull ${HAPROXY_IMAGE}
source ${THIS_DIR}/down.bash

docker network create -d bridge ${NETWORK_NAME}

for node_name in "${NODE_NAMES[@]}"
do
    docker run -d --name=${node_name} --hostname=${node_name} --net=${NETWORK_NAME} ${HTML_TO_PDF_IMAGE}
done

docker run -d --name=${HAPROXY} --hostname=${HAPROXY} --net=${NETWORK_NAME} --ulimit nofile=262144:262144 ${PORTS_TO_PUBLISH_HAPROXY} ${VOLUMES_HAPROXY} ${HAPROXY_IMAGE}
