#!/bin/bash

# Run JMeter load test via compose

check_cgroup_v1_enabled() {
    mount | grep -q "cgroup "
    if [[ $? -ne 0 ]]; then
        printf "Enable cgroup v1 for java 11 not to OOM in containers\n"
        exit 1
    fi
}

stop_and_remove_htmltopdf_container() {
    docker stop ${HTMLTOPDF_CONTAINER_NAME}
    docker rm ${HTMLTOPDF_CONTAINER_NAME}
}

main() {
    local TAKES_COUNT=5
    local IMPLEMENTATIONS=("go" "pure" "ktor" "tomcat")
    for implementation in "${IMPLEMENTATIONS[@]}"; do
        local TAKE_NO=1
        while [ ${TAKE_NO} -le ${TAKES_COUNT} ]; do
            stop_and_remove_htmltopdf_container
            docker run -d --name=${HTMLTOPDF_CONTAINER_NAME} -p 8080:8080 aashipov/htmltopdf:centos-${implementation}
            rm -rf ${_SCRIPT_DIR}/bin/htmltopdf-load-test/invoicepdf
            rm -rf ${_SCRIPT_DIR}/bin/htmltopdf-load-test/tablepdf
            rm -rf ${_SCRIPT_DIR}/client
            rm -rf ${_SCRIPT_DIR}/server
            docker-compose -f docker-compose-jmeter.yml up
            docker-compose -f docker-compose-jmeter.yml down
            cp ${_SCRIPT_DIR}/client/htmltopdf-load-test.jtl ${_SCRIPT_DIR}/stats/${implementation}-${CONVERTER}-${TAKE_NO}.jtl
            TAKE_NO=$((${TAKE_NO} + 1))
            stop_and_remove_htmltopdf_container
        done
    done

    cd ${_SCRIPT_DIR}/stats
    ./do-stats.bash
}

# Main procedure

# https://stackoverflow.com/a/1482133
_SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$0")")
cd ${_SCRIPT_DIR}

if [ $# -ne 1 ]; then
    printf "usage: $(basename $0) name of converter (html or chromium)\n"
    exit 1
fi

if grep -q "HOST_TO_TEST=<host-IP>" ${_SCRIPT_DIR}/.env; then
    printf "Replace <host-IP> with host IP in .env variable HOST_TO_TEST and repeat\n"
    exit 1
fi

HTMLTOPDF_CONTAINER_NAME=htmltopdf

CONVERTER=${1}

check_cgroup_v1_enabled
main
