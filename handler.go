package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
)

const (
	slash       = "/"
	htmlUrl     = slash + html
	chromiumUrl = slash + chromium
)

func handler(w http.ResponseWriter, r *http.Request) {
	workdir := ""
	defer os.RemoveAll(workdir)
	// Store multipart
	switch r.URL.String() {
	case htmlUrl, chromiumUrl:
		workdir = createWorkDir()
		if err := receiveFiles(w, r, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	}
	// HTML to PDF or respond health
	switch r.URL.String() {
	case htmlUrl:
		if err := callExecutable(wkhtmltopdf, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	case chromiumUrl:
		if err := callExecutable(chromium, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	default:
		health(w, r)
		return
	}
	if err := sendPdf(w, filepath.Join(workdir, resultPdf)); isError(err) {
		buildInternalServerError(w, err)
	}
}
