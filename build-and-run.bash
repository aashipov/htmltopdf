#!/bin/bash

set -x

#To ease copy-paste
IMAGE="aashipov/htmltopdf"
CONTAINER_NAME=htmltopdf-dev
TOP_COMMIT=$(git log --pretty=format:'%h' -n 1)

docker build --file=Dockerfile --tag=${IMAGE}:latest --tag=${IMAGE}:${TOP_COMMIT} .
source ./down.bash
docker run -d --name=${CONTAINER_NAME} --hostname=${CONTAINER_NAME} -p 8080:8080 ${IMAGE}:${TOP_COMMIT}
# Push VCS commit sha as docker hub tag to bypass nexus bug
docker push ${IMAGE}: --all-tags
