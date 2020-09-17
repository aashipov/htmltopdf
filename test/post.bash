#!/bin/bash

WORKDIR=$(pwd)
TMP_DIR=${WORKDIR}/tmp
HTML_DIR=${WORKDIR}/html
rm -rf ${TMP_DIR}
mkdir -p ${TMP_DIR}

URL_BASE="http://localhost:8080"
REQUEST_PARAMS="--form files=@${HTML_DIR}/index.html --form files=@${HTML_DIR}/style.css --form files=@${HTML_DIR}/logo.png --form files=${HTML_DIR}/dimension.png --form files=@${HTML_DIR}/Roboto-Bold.ttf --form files=@${HTML_DIR}/Roboto-Regular.ttf"

do_post() {
  FILENAME_PREFIX=$(shuf -i 0-100000 -n 1)
  curl --request POST --url ${URL_BASE}/chromium --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-chromium.pdf
  curl --request POST --url ${URL_BASE}/chromium/landscape --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-chromium-landscape.pdf
  curl --request POST --url ${URL_BASE}/chromium/a3 --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-chromium-a3.pdf
  curl --request POST --url ${URL_BASE}/chromium/a3/landscape --header "Content-Type: multipart/form-data" ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-chromium-a3-landscape.pdf
  curl --request POST --url ${URL_BASE}/html --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html.pdf
  curl --request POST --url ${URL_BASE}/html/landscape --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html-landscape.pdf
  curl --request POST --url ${URL_BASE}/html/a3 --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html-a3.pdf
  curl --request POST --url ${URL_BASE}/html/a3/landscape --header 'Content-Type: multipart/form-data' ${REQUEST_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-html-a3-landscape.pdf
  curl ${URL_BASE}/health -o ${TMP_DIR}/${FILENAME_PREFIX}-health.txt
  echo "done ${1}"
}

# For loop X times
for i in {1..1}; do
  do_post ${i} &
done

wait
echo "All done"
