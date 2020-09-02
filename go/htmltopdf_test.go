package main

import (
	"bytes"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

func testHtmltopdf(endpoint string, t *testing.T) {

	t.Log(endpoint)

	bodyBuf := &bytes.Buffer{}
	bodyWriter := multipart.NewWriter(bodyBuf)

	fileWriter, err := bodyWriter.CreateFormFile("file", indexHTML)
	if isError(err) {
		t.Fatal("error writing to buffer")
	}

	fh, err := os.Open(indexHTML)
	if isError(err) {
		t.Fatal("error opening file")
	}
	defer fh.Close()

	_, err = io.Copy(fileWriter, fh)
	if isError(err) {
		t.Fatal(err)
	}

	contentType := bodyWriter.FormDataContentType()
	bodyWriter.Close()

	resp, err := http.Post(endpoint, contentType, bodyBuf)
	if isError(err) {
		t.Fatal(err)
	}

	defer resp.Body.Close()
	respBody, err := io.ReadAll(resp.Body)
	if isError(err) {
		t.Fatal(err)
	}

	/*err = os.WriteFile(resultPdf, respBody, 0777)
	  if err != nil {
	      panic(err)
	  }*/

	respBodyStr := string(respBody)
	if !strings.HasPrefix(respBodyStr, "%PDF-") {
		t.Fatal("Not a PDF file")
	}
}

func testHealth(endpoint string, t *testing.T) {

	t.Log(endpoint)

	response, err := http.Get(endpoint)
	if isError(err) {
		t.Fatal(err)
	}

	responseBody, err := io.ReadAll(response.Body)
	response.Body.Close()
	if isError(err) {
		t.Fatal(err)
	}

	if !bytes.Equal(statusUp, responseBody) {
		t.Fatal("Can not HTTP GET health")
	}
}

func Test(t *testing.T) {
	chromiumProcess := launchBrowser()
	defer chromiumProcess.Kill()
	time.Sleep(5 * time.Second)

	testHttpServer := httptest.NewServer(http.HandlerFunc(handler))
	defer testHttpServer.Close()

	testHealth(testHttpServer.URL+slash+"healthcheck", t)
	
	testHtmltopdf(testHttpServer.URL+slash+html, t)

	t.Log("cdp")
	testHtmltopdf(testHttpServer.URL+slash+chromium, t)

	t.Log("chromedp")
	t.Setenv("CHROMIUM_HARNESS", "chromedp")
	testHtmltopdf(testHttpServer.URL+slash+chromium, t)
}
