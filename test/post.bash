#!/bin/bash

WORKDIR=$(pwd)
TMP_DIR=${WORKDIR}/tmp
rm -rf ${TMP_DIR}
mkdir -p ${TMP_DIR}

URL_BASE="http://localhost:8080"
REQUEST_PARAMS="--form files=@index.html --form files=@style.css --form files=@logo.png --form files=dimension.png"

do_post() {
  FILENAME_PREFIX=$(shuf -i 0-100000 -n 1)
  curl --request POST --url ${URL_BASE}/wkhtmltopdf --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-wkhtmltopdf.pdf
  curl --request POST --url ${URL_BASE}/html --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html.pdf
  curl ${URL_BASE}/health -o ${TMP_DIR}/${FILENAME_PREFIX}-health.txt
  echo "done ${1}"
}

# For loop X times
for i in {1..1}; do
  do_post ${i} &
done

wait
echo "All done"
