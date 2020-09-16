#!/bin/bash

set -x

NODE_1=htmltopdf1
NODE_2=htmltopdf2
NODE_3=htmltopdf3
HAPROXY=haproxy
NETWORK_NAME=htmltopdf

docker stop ${HAPROXY} ${NODE_1} ${NODE_2} ${NODE_3} ; docker rm ${HAPROXY} ${NODE_1} ${NODE_2} ${NODE_3} ; docker network rm ${NETWORK_NAME}
