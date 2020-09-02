#!/bin/bash

# Run JMeter load test via compose

# https://stackoverflow.com/a/1482133
_SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$0")")
cd ${_SCRIPT_DIR}

if [ $# -ne 1 ]; then
    printf "usage: $(basename $0) name of JTL file (results)\n"
    exit 1
fi

if grep -q "HOST_TO_TEST=<host-IP>" ${_SCRIPT_DIR}/.env; then
    printf "Replace <host-IP> with host IP in .env variable HOST_TO_TEST and repeat\n"
    exit 1
fi

check_cgroup_v1_enabled() {
    mount | grep -q "cgroup on"
    if [[ $? -ne 0 ]]; then
        printf "Enable cgroup v1 for java 11 not to OOM in containers\n"
        exit 1
    fi
}

main() {
    local TAKES_COUNT=5
    local TAKE_NO=1

    while [ ${TAKE_NO} -le ${TAKES_COUNT} ]; do
        rm -rf ${_SCRIPT_DIR}/bin/htmltopdf-load-test/result
        rm -rf ${_SCRIPT_DIR}/client
        rm -rf ${_SCRIPT_DIR}/server
        docker-compose -f docker-compose-jmeter.yml up
        docker-compose -f docker-compose-jmeter.yml down
        cp ${_SCRIPT_DIR}/client/Load-test.jtl ${_SCRIPT_DIR}/stats/${FLAVOR}${TAKE_NO}.jtl
        TAKE_NO=$((${TAKE_NO} + 1))
    done

    cd ${_SCRIPT_DIR}/stats
    ./do-stats.bash
}

FLAVOR=${1}

check_cgroup_v1_enabled
main
