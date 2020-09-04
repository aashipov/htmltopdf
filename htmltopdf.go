package main

import (
	"bufio"
	"context"
	"errors"
	"io"
	"log"
	"math/rand"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"syscall"
	"time"
)

const (
	wkhtmltopdf     = "wkhtmltopdf"
	chromium        = "chromium"
	indexHtml       = "index.html"
	resultPdf       = "result.pdf"
	noIndexHtml     = "No index.html"
	slash           = "/"
	wkhtmltopdfUrl  = slash + wkhtmltopdf
	htmlUrl         = slash + "html"
	notAnExecutable = "notAnExecutable"
	osCmdTimeout    = 30 * time.Second
)

var (
	wkhtmltopdfExecutableName = getWkhtmltopdfExecutableName()
	chromiumExecutableName    = getChromiumExecutableName()
)

func getOsName() string {
	return runtime.GOOS
}

func getWkhtmltopdfExecutableName() string {
	if getOsName() == "windows" {
		return "wkhtmltopdf.exe"
	}
	if getOsName() == "linux" {
		return wkhtmltopdf
	}
	return notAnExecutable
}

func getChromiumExecutableName() string {
	if getOsName() == "windows" {
		return "chrome.exe"
	}
	if getOsName() == "linux" {
		return chromium
	}
	return notAnExecutable
}

func isError(err error) bool {
	if err != nil {
		return true
	}
	return false
}

func logAndTerminate(err error) {
	if isError(err) {
		log.Fatal(err)
	}
}

// Get dir this program runs in
func getGoWorkDir() string {
	dir, err := filepath.Abs(filepath.Dir(os.Args[0]))
	logAndTerminate(err)
	return dir
}

// Get request workdir
func getWorkDir() string {
	dir := filepath.Join(getGoWorkDir(), "tmp", strconv.Itoa(rand.Int()))
	err := os.MkdirAll(dir, os.ModePerm)
	logAndTerminate(err)
	return dir
}

func buildInternalServerError(w http.ResponseWriter, err error) {
	if isError(err) {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

// Store files from Multipart
//http://sanatgersappa.blogspot.com/2013/03/handling-multiple-file-uploads-in-go.html
func receiveFiles(w http.ResponseWriter, r *http.Request, workdir string) error {
	indexHtmlReceived := false
	reader, err := r.MultipartReader()
	if isError(err) {
		return err
	}
	//copy each part to destination.
	for {
		part, err := reader.NextPart()
		if err == io.EOF {
			break
		}
		//if part.FileName() is empty, skip this iteration.
		if part.FileName() == "" {
			continue
		}
		fileToSave := filepath.Join(workdir, part.FileName())
		dst, err := os.Create(fileToSave)
		if isError(err) {
			return err
		}
		defer dst.Close()
		if _, err := io.Copy(dst, part); isError(err) {
			return err
		}
		if indexHtml == part.FileName() {
			indexHtmlReceived = true
		}
		//log.Printf("Received : %s\n", fileToSave)
	}
	if !indexHtmlReceived {
		return errors.New(noIndexHtml)
	}
	return nil
}

// Send PDF to client
func sendPdf(w http.ResponseWriter, currentPdfFile string) error {
	file, err := os.Open(currentPdfFile)
	if isError(err) {
		return err
	}
	br := bufio.NewReader(file)
	if _, err := io.Copy(w, br); isError(err) {
		return err
	}
	w.Header().Set("Content-Disposition", "attachment; filename="+strconv.Quote(resultPdf))
	w.Header().Set("Content-Type", "application/octet-stream")
	return file.Close()
}

func health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte("{\"status\":\"UP\"}"))
}

func buildCmd(executableName string, workdir string, ctx context.Context) (*exec.Cmd, error) {
	cmd := exec.Cmd{}
	if chromium == executableName {
		cmd = *exec.CommandContext(ctx, chromiumExecutableName, "--headless", "--no-sandbox", "--disable-setuid-sandbox", "--unlimited-storage", "--disable-dev-shm-usage", "--disable-gpu", "--disable-translate", "--disable-extensions", "--disable-background-networking", "--safebrowsing-disable-auto-update", "--disable-sync", "--disable-default-apps", "--hide-scrollbars", "--metrics-recording-only", "--mute-audio", "--no-first-run", "--virtual-time-budget=1000", "--print-to-pdf="+filepath.Join(workdir, resultPdf), filepath.Join(workdir, indexHtml))
	} else if wkhtmltopdf == executableName {
		cmd = *exec.CommandContext(ctx, wkhtmltopdfExecutableName, "--enable-local-file-access", "--print-media-type", "--no-stop-slow-scripts", "--margin-bottom", "0", "--margin-left", "0", "--margin-right", "0", "--margin-top", "0", filepath.Join(workdir, indexHtml), filepath.Join(workdir, resultPdf))
	} else {
		return nil, errors.New("Unknown executable " + executableName)
	}
	cmd.Dir = workdir
	return &cmd, nil
}

func callExecutable(executableName string, workdir string) error {
	ctx, cancel := context.WithTimeout(context.Background(), osCmdTimeout)
	defer cancel()
	cmd, err := buildCmd(executableName, workdir, ctx)
	if isError(err) {
		return err
	}
	log.Printf("executing %s in %s", executableName, workdir)
	return cmd.Run()
}

func commonHandler(w http.ResponseWriter, r *http.Request) {
	workdir := getWorkDir()
	defer os.RemoveAll(workdir)
	// Store multipart
	switch r.URL.String() {
	case wkhtmltopdfUrl, htmlUrl:
		if err := receiveFiles(w, r, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	}
	currentPdfFile := filepath.Join(workdir, resultPdf)
	// HTML to PDF or respond health
	switch r.URL.String() {
	case wkhtmltopdfUrl:
		if err := callExecutable(wkhtmltopdf, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	case htmlUrl:
		if err := callExecutable(chromium, workdir); isError(err) {
			log.Print(err)
			buildInternalServerError(w, err)
			return
		}
	default:
		health(w, r)
		return
	}
	if err := sendPdf(w, currentPdfFile); isError(err) {
		buildInternalServerError(w, err)
	}
}

func enableGracefulShutdown(server *http.Server) {
	gracefulShutdown := make(chan os.Signal)
	signal.Notify(gracefulShutdown, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-gracefulShutdown
		log.Printf("%s received, shutdown", sig)
		server.Close()
		os.Exit(0)
	}()
}

func main() {
	http.HandleFunc(slash, commonHandler)
	server := http.Server{Addr: ":8080", Handler: nil}
	enableGracefulShutdown(&server)
	server.ListenAndServe()
}
