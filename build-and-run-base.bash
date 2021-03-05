#!/bin/bash

set -x

#To ease copy-paste
IMAGE="aashipov/htmltopdf:base"
docker pull ${IMAGE}
docker build --file=Dockerfile --tag=${IMAGE} .
docker push ${IMAGE}
