#!/bin/bash

set -x

NODE_NAMES=("htmltopdf1" "htmltopdf2" "htmltopdf3")

# https://stackoverflow.com/a/49167382
NODE_NAMES_SPACE_SEPARATED=""
printf -v NODE_NAMES_SPACE_SEPARATED ' %s' "${NODE_NAMES[@]}"
NODE_NAMES_SPACE_SEPARATED=${NODE_NAMES_SPACE_SEPARATED:1}

HAPROXY=htmltopdf-haproxy
NETWORK_NAME=htmltopdf

docker stop ${HAPROXY} ${NODE_NAMES_SPACE_SEPARATED}
docker rm ${HAPROXY} ${NODE_NAMES_SPACE_SEPARATED}

docker network rm ${NETWORK_NAME}
