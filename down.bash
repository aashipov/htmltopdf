#!/bin/bash

set - x

CONTAINER_NAME=htmltopdf-dev

docker stop ${CONTAINER_NAME} ; docker rm ${CONTAINER_NAME}
