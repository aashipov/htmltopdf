#!/bin/bash

set -x

NODE_NAMES=("htmltopdf1" "htmltopdf2" "htmltopdf3")
HAPROXY=htmltopdf-haproxy
NETWORK_NAME=htmltopdf

docker stop ${HAPROXY}
docker rm ${HAPROXY}

for node_name in "${NODE_NAMES[@]}"
do
    docker stop ${node_name}
    docker rm ${node_name}
done

docker network rm ${NETWORK_NAME}
