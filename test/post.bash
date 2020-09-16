#!/bin/bash

WORKDIR=$(pwd)
TMP_DIR=${WORKDIR}/tmp
rm -rf ${TMP_DIR}
mkdir -p ${TMP_DIR}

URL_BASE="http://localhost:8080"
REQUEST_PARAMS="--form files=@index.html --form files=@style.css --form files=@logo.png --form files=dimension.png --form files=@Roboto-Bold.ttf --form files=@Roboto-Regular.ttf"

do_post() {
  FILENAME_PREFIX=$(shuf -i 0-100000 -n 1)
  curl --request POST --url ${URL_BASE}/chromium --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-chromium.pdf
  curl --request POST --url ${URL_BASE}/html --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html.pdf
  curl --request POST --url ${URL_BASE}/html/landscape --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html-landscape.pdf
  curl ${URL_BASE}/health -o ${TMP_DIR}/${FILENAME_PREFIX}-health.txt
  echo "done ${1}"
}

# For loop X times
for i in {1..1}; do
  do_post ${i} &
done

wait
echo "All done"
