package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const (
	slash            = "/"
	htmlUrl          = slash + html
	htmlLandscapeUrl = slash + html + slash + landscape
	chromiumUrl      = slash + chromium
)

func handler(w http.ResponseWriter, r *http.Request) {
	url := r.URL.String()
	switch url {
	case htmlUrl, htmlLandscapeUrl, chromiumUrl:
		workdir := createWorkDir()
		defer os.RemoveAll(workdir)
		opts := newPrinterOptions(workdir)
		if strings.Contains(url, landscape) {
			opts.setOrientation(landscape)
		}
		if strings.Contains(url, html) {
			opts.setExecutableName(wkhtmltopdfExecutableName)
		}
		if strings.Contains(url, chromium) {
			opts.setExecutableName(chromiumExecutableName)
		}
		// Store multipart
		if err := receiveFiles(w, r, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
		// convert
		if err := callExecutable(opts); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
		if err := sendPdf(w, filepath.Join(workdir, resultPdf)); isError(err) {
			buildInternalServerError(w, err)
		}
	default:
		health(w, r)
		return
	}
}
