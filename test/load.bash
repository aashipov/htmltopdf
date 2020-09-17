#!/bin/bash

# JMeter load test.
# Copy `load` directory and `load.bash` to JMeter `bin` and run `bash load.bash` there
# On MS Windows copy `html` directory to JMere `bin/load/`

rm -rf load/Load-test.jtl
rm -rf load/web-report/
rm -rf load/pdf/

JVM_ARGS="-Xms4g -Xmx4g" ./jmeter.sh -n -t load/Load-test.jmx -l load/Load-test.jtl -e -o load/web-report/
