#!/bin/bash

# Clear csv, png leftovers of preceding run
# cut elapsed column from jtl, paste to single-column csv
# paste csv into paste.csv
# call stats

# https://stackoverflow.com/a/1482133
_SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$0")")
cd ${_SCRIPT_DIR}

main() {
    #pip install --user statsmodels seaborn
    pip install statsmodels seaborn

    rm -rf paste.csv anova.csv ks.csv desc.csv tukeyhsd.csv *.png

    # https://stackoverflow.com/a/55817578
    JTL_FILES=$(find . -type f -name "*.jtl" | sed 's/^\.\///g' | sort)

    # https://stackoverflow.com/a/1469863
    for jtl_file in ${JTL_FILES}; do
        filename=$(printf "${jtl_file}\n" | cut -d '.' -f1)
        printf "${filename}\n" >"${filename}".csv
        cat ${jtl_file} | cut -d ',' -f2 >>${filename}.csv
        # https://stackoverflow.com/a/15400287
        # remove elapsed
        sed -i '2d' ${filename}.csv
    done

    paste -d ',' *.csv >paste.csv
    python statistics.py
}

main
