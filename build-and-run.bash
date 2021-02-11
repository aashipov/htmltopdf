#!/bin/bash

set -x

#To ease copy-paste
CONTAINER_NAME=htmltopdf-dev

docker build --file=Dockerfile --tag=${CONTAINER_NAME} .
source ./down.bash
docker run -d --name=${CONTAINER_NAME} --hostname=${CONTAINER_NAME} -p 8080:8080 ${CONTAINER_NAME}
