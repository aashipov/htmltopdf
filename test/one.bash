#!/bin/bash

# Run one instance of aashipov/htmltopdf in container
set -x

TAG="latest"
#TAG="chromedp"
HTML_TO_PDF_IMAGE="aashipov/htmltopdf:${TAG}"
THIS_DIR=$(pwd)
NETWORK_NAME=htmltopdf
PORTS_TO_PUBLISH="-p8080:8080"

docker pull ${HTML_TO_PDF_IMAGE}
docker container stop ${NETWORK_NAME}
docker container rm ${NETWORK_NAME}
docker network rm ${NETWORK_NAME}

docker network create -d bridge ${NETWORK_NAME}
docker run -d --name=${NETWORK_NAME} --hostname=${NETWORK_NAME} --net=${NETWORK_NAME} ${PORTS_TO_PUBLISH} ${HTML_TO_PDF_IMAGE}
