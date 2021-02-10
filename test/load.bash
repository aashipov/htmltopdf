#!/bin/bash

# Run multiple JMeter Remote and a Client in docker against htmltopdf
# source https://www.blazemeter.com/blog/jmeter-distributed-testing-with-docker
# 

set -x

NETWORK_NAME="load-test-remote"
# Add as many as needed
SERVER_NODE_NAMES=("jmeter-server1" "jmeter-server2" "jmeter-server3")
CLIENT_NODE_NAME="jmeter-client"
IMAGE_NAME="aashipov/htmltopdf:base"

# Bypass Docker on Windows bug - can not find host by hostname from within container
DNS_SERVER_IP=192.168.1.1

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
JMETER_CATALOG_HOST=$(pwd)
# opening slash to bypass docker on Windows bug
JMETER_CATALOG_IN_CONTAINER="//deployment/jmeter"
LOAD_TEST_DIR="${JMETER_CATALOG_IN_CONTAINER}/bin/load"
# opening slash to bypass docker on Windows bug
VOLUMES="-v /${JMETER_CATALOG_HOST}:/deployment/jmeter"

ENVIRONMENT="-e JAVA_HOME=//usr/lib/jvm/jre"

echo "Clean up"
rm -rf ${JMETER_CATALOG_HOST}/client/
rm -rf ${JMETER_CATALOG_HOST}/server/
rm -rf ${JMETER_CATALOG_HOST}/bin/load/invoicepdf
rm -rf ${JMETER_CATALOG_HOST}/bin/load/tablepdf

for server_node_name in "${SERVER_NODE_NAMES[@]}"
do
	docker container stop ${server_node_name}
	docker container rm ${server_node_name}
done
docker container stop ${CLIENT_NODE_NAME}
docker container rm ${CLIENT_NODE_NAME}
docker network rm ${NETWORK_NAME}
docker network create ${NETWORK_NAME}

echo "Create servers"
for server_node_name in "${SERVER_NODE_NAMES[@]}"
do
	docker run -d --dns=${DNS_SERVER_IP} --hostname=${server_node_name} --name=${server_node_name} --network=${NETWORK_NAME} ${ENVIRONMENT} ${VOLUMES} ${IMAGE_NAME} ${JMETER_CATALOG_IN_CONTAINER}/bin/jmeter -n -s -Jclient.rmi.localport=7000 -Jserver.rmi.ssl.disable=true -Jserver.rmi.localport=60000 -j ${JMETER_CATALOG_IN_CONTAINER}/server/${server_node_name}_${TIMESTAMP}.log
done

echo "Create client"
docker run -d --dns=${DNS_SERVER_IP} --hostname=${CLIENT_NODE_NAME} --name=${CLIENT_NODE_NAME} --network=${NETWORK_NAME} ${ENVIRONMENT} ${VOLUMES} ${IMAGE_NAME} ${JMETER_CATALOG_IN_CONTAINER}/bin/jmeter -n -X -Jclient.rmi.localport=7000 -Jserver.rmi.ssl.disable=true -R $(echo $(printf ",%s" "${SERVER_NODE_NAMES[@]}") | cut -c 2-) -t ${LOAD_TEST_DIR}/Load-test.jmx -l ${JMETER_CATALOG_IN_CONTAINER}/client/Load-test_${TIMESTAMP}.jtl -j ${JMETER_CATALOG_IN_CONTAINER}/client/${CLIENT_NODE_NAME}_${TIMESTAMP}.log -e -o ${JMETER_CATALOG_IN_CONTAINER}/client/web-report-${TIMESTAMP}
