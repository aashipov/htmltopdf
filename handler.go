package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
)

const (
	slash              = "/"
	htmlUrl            = slash + html
	htmlLandscapeUrl   = slash + html + slash + landscape
	htmlA3Url          = slash + html + slash + a3
	htmlA3LandscapeUrl = slash + html + slash + a3 + slash + landscape
	chromiumUrl        = slash + chromium
)

func handler(w http.ResponseWriter, r *http.Request) {
	url := r.URL.String()
	switch url {
	case htmlUrl, htmlLandscapeUrl, htmlA3Url, htmlA3LandscapeUrl, chromiumUrl:
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
