#!/bin/bash

WORKDIR=$(pwd)
TMP_DIR=${WORKDIR}/tmp
HTML_DIR=${WORKDIR}/html
TABLE_DIR=${WORKDIR}/table
rm -rf ${TMP_DIR}
mkdir -p ${TMP_DIR}

URL_BASE="http://localhost:8080"
HTML_PARAMS="--form files=@${HTML_DIR}/index.html --form files=@${HTML_DIR}/style.css --form files=@${HTML_DIR}/logo.png --form files=${HTML_DIR}/dimension.png --form files=@${HTML_DIR}/Roboto-Bold.ttf --form files=@${HTML_DIR}/Roboto-Regular.ttf"
TABLE_PARAMS="--form files=@${TABLE_DIR}/index.html --form files=@${TABLE_DIR}/style.css"

do_post() {
  FILENAME_PREFIX=$(shuf -i 0-100000 -n 1)
  curl --request POST --url ${URL_BASE}/chromium/top10/right10/bottom10 --header "Content-Type: multipart/form-data" ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-chromium.pdf
  curl --request POST --url ${URL_BASE}/chromium/landscape/top20/right5/bottom5/left5 --header "Content-Type: multipart/form-data" ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-chromium-landscape.pdf
  curl --request POST --url ${URL_BASE}/chromium/a3 --header "Content-Type: multipart/form-data" ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-chromium-a3.pdf
  curl --request POST --url ${URL_BASE}/chromium/a3/landscape --header "Content-Type: multipart/form-data" ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-chromium-a3-landscape.pdf
  curl --request POST --url ${URL_BASE}/html/top10/right10/bottom10 --header 'Content-Type: multipart/form-data' ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-html.pdf
  curl --request POST --url ${URL_BASE}/html/landscape/top20/right5/bottom5/left5 --header 'Content-Type: multipart/form-data' ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-html-landscape.pdf
  curl --request POST --url ${URL_BASE}/html/a3 --header 'Content-Type: multipart/form-data' ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-html-a3.pdf
  curl --request POST --url ${URL_BASE}/html/a3/landscape --header 'Content-Type: multipart/form-data' ${HTML_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-invoice-html-a3-landscape.pdf
  
  curl ${URL_BASE}/health -o ${TMP_DIR}/${FILENAME_PREFIX}-health.txt

  curl --request POST --url ${URL_BASE}/chromium/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${TABLE_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-table-chromium.pdf
  curl --request POST --url ${URL_BASE}/html/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${TABLE_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-table-html.pdf

  echo "done ${1}"
}

# For loop X times
for i in {1..1}; do
  do_post ${i} &
done

wait
echo "All done"
