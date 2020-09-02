#!/bin/bash

set -x

NODE_NAMES=("htmltopdf1" "htmltopdf2" "htmltopdf3")
NODE_NAMES_SPACE_SEPARATED=""
for node_name in "${NODE_NAMES[@]}"
do
    NODE_NAMES_SPACE_SEPARATED+=" ${node_name}"
done
NODE_NAMES_SPACE_SEPARATED=${NODE_NAMES_SPACE_SEPARATED:1}

HAPROXY=htmltopdf-haproxy
NETWORK_NAME=htmltopdf

docker stop ${HAPROXY} ${NODE_NAMES_SPACE_SEPARATED}
docker rm ${HAPROXY} ${NODE_NAMES_SPACE_SEPARATED}

docker network rm ${NETWORK_NAME}
