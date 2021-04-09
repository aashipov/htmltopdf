#!/bin/bash

set -e

#To ease copy-paste
IMAGE="aashipov/htmltopdf:buildbed"
docker build --file=Dockerfile.buildbed --tag=${IMAGE} .
docker push ${IMAGE}
