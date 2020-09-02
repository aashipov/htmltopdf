package main

import (
	"net/http"
	"strings"
)

func handler(w http.ResponseWriter, r *http.Request) {
	url := r.URL.String()
	switch {
	// convert HTML to PDF
	case strings.Contains(url, html) || strings.Contains(url, chromium):
		htmlToPdf(w, r)
	// otherwise respond {"status":"UP"}
	default:
		health(w, r)
	}
}
