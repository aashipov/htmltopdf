package main

import (
	"log"
	"net/http"
)

func main() {
	http.HandleFunc(slash, handler)
	chromiumProcess := launchBrowser()
	server := http.Server{Addr: ":8080", Handler: nil}
	enableGracefulShutdown(&server, chromiumProcess)
	if err := server.ListenAndServe(); isError(err) {
		log.Fatal(err)
	}
}
