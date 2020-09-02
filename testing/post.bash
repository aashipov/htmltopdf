#!/bin/bash

WORKDIR=$(pwd)
TMP_DIR=${WORKDIR}/tmp
LOAD_TEST_DIR=${WORKDIR}/bin/htmltopdf-load-test
INVOICE_DIR=${LOAD_TEST_DIR}/invoice
TABLE_DIR=${LOAD_TEST_DIR}/table
BORLAND_DIR=${LOAD_TEST_DIR}/borland
TOLSTOY_DIR=${LOAD_TEST_DIR}/tolstoy
rm -rf ${TMP_DIR}
mkdir -p ${TMP_DIR}

URL_BASE="http://localhost:8080"
HTML_PARAMS="--form files=@${INVOICE_DIR}/index.html --form files=@${INVOICE_DIR}/style.css --form files=@${INVOICE_DIR}/logo.png --form files=@${INVOICE_DIR}/dimension.png --form files=@${INVOICE_DIR}/Roboto-Bold.ttf --form files=@${INVOICE_DIR}/Roboto-Regular.ttf"
TABLE_PARAMS="--form files=@${TABLE_DIR}/index.html --form files=@${TABLE_DIR}/style.css"
BORLAND_PARAMS="--form files=@${BORLAND_DIR}/index.html --form files=@${BORLAND_DIR}/sections.css --form files=@${BORLAND_DIR}/stadyn_image1.gif --form files=@${BORLAND_DIR}/stadyn_image2.gif --form files=@${BORLAND_DIR}/stadyn_image3.gif --form files=@${BORLAND_DIR}/stadyn_image4.gif --form files=@${BORLAND_DIR}/stadyn_image5.gif --form files=@${BORLAND_DIR}/stadyn_image6.gif --form files=@${BORLAND_DIR}/stadyn_image7.gif --form files=@${BORLAND_DIR}/stadyn_image8.gif --form files=@${BORLAND_DIR}/stadyn_image9.gif --form files=@${BORLAND_DIR}/stadyn_image10.gif"
TOLSTOY_PARAMS="--form files=@${TOLSTOY_DIR}/index.html --form files=@${TOLSTOY_DIR}/cover.jpg"

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

  curl --request POST --url ${URL_BASE}/chromium/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${BORLAND_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-borland-chromium.pdf
  curl --request POST --url ${URL_BASE}/html/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${BORLAND_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-borland-html.pdf

  curl --request POST --url ${URL_BASE}/chromium/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${TOLSTOY_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-tolstoy-chromium.pdf
  curl --request POST --url ${URL_BASE}/html/top25/right18/bottom20/left19 --header "Content-Type: multipart/form-data" ${TOLSTOY_PARAMS} -o ${TMP_DIR}/${FILENAME_PREFIX}-tolstoy-html.pdf

  echo "done ${1}"
}

do_post

wait
echo "All done"
