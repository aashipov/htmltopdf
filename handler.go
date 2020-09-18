package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

func handler(w http.ResponseWriter, r *http.Request) {
	url := r.URL.String()
	switch {
	// convert HTML to PDF
	case strings.Contains(url, html) || strings.Contains(url, chromium):
		workdir := createWorkDir()
		defer os.RemoveAll(workdir)
		opts := buildPrinterOpions(workdir, url)
		// Store multipart
		if err := receiveFiles(w, r, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
		// convert
		if err := opts.print(); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
		if err := sendPdf(w, filepath.Join(workdir, resultPdf)); isError(err) {
			buildInternalServerError(w, err)
		}
	// otherwise respond {"status":"UP"}
	default:
		health(w, r)
	}
}
