#!/bin/bash

set -x

# Run multiple JMeter Remote and a Client in docker against htmltopdf
# Put enclosing dir to Apache JMeter dir
# source https://www.blazemeter.com/blog/jmeter-distributed-testing-with-docker
#

# Add as many as needed
SERVER_NODE_NAMES=("jmeter-server1" "jmeter-server2" "jmeter-server3")
# Two variables to hold comma/whitespace-separated server names
SERVER_NODE_NAMES_COMMA_SEPARATED=""
SERVER_NODE_NAMES_SPACE_SEPARATED=""
for node_name in "${SERVER_NODE_NAMES[@]}"
do
    SERVER_NODE_NAMES_COMMA_SEPARATED+=",${node_name}"
	SERVER_NODE_NAMES_SPACE_SEPARATED+=" ${node_name}"
done
SERVER_NODE_NAMES_COMMA_SEPARATED=${SERVER_NODE_NAMES_COMMA_SEPARATED:1}
SERVER_NODE_NAMES_SPACE_SEPARATED=${SERVER_NODE_NAMES_SPACE_SEPARATED:1}

CLIENT_NODE_NAME="jmeter-client"
NETWORK_NAME="load-test-remote"

IMAGE_NAME="aashipov/docker:centosdummyjdk11u"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
JMETER_CATALOG_HOST=$(pwd)
JMETER_CATALOG_IN_CONTAINER="/dummy/jmeter"
LOAD_TEST_DIR="${JMETER_CATALOG_IN_CONTAINER}/bin/htmltopdf-load-test"
VOLUMES="-v ${JMETER_CATALOG_HOST}:${JMETER_CATALOG_IN_CONTAINER}"

echo "Pull image"
docker pull ${IMAGE_NAME}

echo "Clean up"
docker container stop ${CLIENT_NODE_NAME} ${SERVER_NODE_NAMES_SPACE_SEPARATED}
docker container rm ${CLIENT_NODE_NAME} ${SERVER_NODE_NAMES_SPACE_SEPARATED}
docker network rm ${NETWORK_NAME}

rm -rf ${JMETER_CATALOG_HOST}/client/
rm -rf ${JMETER_CATALOG_HOST}/server/
rm -rf ${JMETER_CATALOG_HOST}/bin/load/invoicepdf
rm -rf ${JMETER_CATALOG_HOST}/bin/load/tablepdf

echo "Create network"
docker network create ${NETWORK_NAME}

echo "Create servers"
for server_node_name in "${SERVER_NODE_NAMES[@]}"
do
	docker run -d --hostname=${server_node_name} --name=${server_node_name} --network=${NETWORK_NAME} ${VOLUMES} ${IMAGE_NAME} ${JMETER_CATALOG_IN_CONTAINER}/bin/jmeter -n -s -Jclient.rmi.localport=7000 -Jserver.rmi.ssl.disable=true -Jserver.rmi.localport=60000 -j ${JMETER_CATALOG_IN_CONTAINER}/server/${server_node_name}_${TIMESTAMP}.log
done

echo "Create client"
docker run -d --hostname=${CLIENT_NODE_NAME} --name=${CLIENT_NODE_NAME} --network=${NETWORK_NAME} ${VOLUMES} ${IMAGE_NAME} ${JMETER_CATALOG_IN_CONTAINER}/bin/jmeter -n -X -Jclient.rmi.localport=7000 -Jserver.rmi.ssl.disable=true -R ${SERVER_NODE_NAMES_COMMA_SEPARATED} -t ${LOAD_TEST_DIR}/htmltopdf-load-test.jmx -l ${JMETER_CATALOG_IN_CONTAINER}/client/Load-test_${TIMESTAMP}.jtl -j ${JMETER_CATALOG_IN_CONTAINER}/client/${CLIENT_NODE_NAME}_${TIMESTAMP}.log -e -o ${JMETER_CATALOG_IN_CONTAINER}/client/web-report-${TIMESTAMP}
