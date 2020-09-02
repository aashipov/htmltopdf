#!/bin/bash

# chromium harness comparison

check_cgroup_v1_enabled() {
    mount | grep -q "cgroup "
    if [[ $? -ne 0 ]]; then
        printf "Enable cgroup v1 for java 11 not to OOM in containers\n"
        exit 1
    fi
}

check_chromium_converter_enabled_in_test() {
    local TEST_FILE=${_SCRIPT_DIR}/bin/htmltopdf-load-test/htmltopdf-load-test.jmx
    local HTML_ENABLED=">html<"
    local CHROMIUM_ENABLED=">chromium<"
    if [ ! -f ${TEST_FILE} ]; then
        printf "No JMeter test file ${TEST_FILE} found\n"
        exit 1
    fi
    if ! grep -q ${CHROMIUM_ENABLED} ${TEST_FILE}; then
        sed -i -e "s+${HTML_ENABLED}+${CHROMIUM_ENABLED}+g" ${TEST_FILE}
    fi
    if ! grep -q ${CHROMIUM_ENABLED} ${TEST_FILE}; then
        printf "Can not change harness\n"
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
        if [[ "go" == "${implementation}" ]]; then
            local HARNESSES=("cdp" "chromedp")
        else
            local HARNESSES=("jvppeteer" "chrome-devtools-kotlin")
        fi
        for harness in "${HARNESSES[@]}"; do
            local TAKE_NO=1
            while [ ${TAKE_NO} -le ${TAKES_COUNT} ]; do
                stop_and_remove_htmltopdf_container
                docker run -d --name=${HTMLTOPDF_CONTAINER_NAME} -p 8080:8080 -e CHROMIUM_HARNESS=${harness} aashipov/htmltopdf:centos-${implementation}
                rm -rf ${_SCRIPT_DIR}/bin/htmltopdf-load-test/invoicepdf
                rm -rf ${_SCRIPT_DIR}/bin/htmltopdf-load-test/tablepdf
                rm -rf ${_SCRIPT_DIR}/client
                rm -rf ${_SCRIPT_DIR}/server
                docker-compose -f docker-compose-jmeter.yml up
                docker-compose -f docker-compose-jmeter.yml down
                cp ${_SCRIPT_DIR}/client/htmltopdf-load-test.jtl ${_SCRIPT_DIR}/stats/${implementation}-${harness}-${TAKE_NO}.jtl
                TAKE_NO=$((${TAKE_NO} + 1))
                stop_and_remove_htmltopdf_container
            done
        done
    done

    cd ${_SCRIPT_DIR}/stats
    ./do-stats.bash
}

# Main procedure

# https://stackoverflow.com/a/1482133
_SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$0")")
cd ${_SCRIPT_DIR}

if grep -q "HOST_TO_TEST=<host-IP>" ${_SCRIPT_DIR}/.env; then
    printf "Replace <host-IP> with host IP in .env variable HOST_TO_TEST and repeat\n"
    exit 1
fi

HTMLTOPDF_CONTAINER_NAME=htmltopdf

check_cgroup_v1_enabled
check_chromium_converter_enabled_in_test
main
