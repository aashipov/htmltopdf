#!/bin/bash

set -e

#To ease copy-paste
IMAGE="aashipov/htmltopdf:buildbed"
docker pull ${IMAGE}
docker build --file=Dockerfile.buildbed --tag=${IMAGE} .
docker push ${IMAGE}
