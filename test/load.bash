#!/bin/bash

# JMeter load test.
# Copy `load` directory and `load.bash` to JMeter `bin`, copy `html` directory to JMeter `bin/load/` 
# Run `bash load.bash` in JMeter `bin` directory

rm -rf load/Load-test.jtl
rm -rf load/web-report/
rm -rf load/pdf/

JVM_ARGS="-Xms4g -Xmx4g" ./jmeter.sh -n -t load/Load-test.jmx -l load/Load-test.jtl -e -o load/web-report/
