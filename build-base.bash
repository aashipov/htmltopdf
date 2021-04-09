#!/bin/bash

set -e

#To ease copy-paste
IMAGE="aashipov/htmltopdf:base"
docker build --file=Dockerfile.base --tag=${IMAGE} .
docker push ${IMAGE}
