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
	"strings"
	"syscall"
	"time"
)

const (
	osName        = runtime.GOOS
	linux         = "linux"
	windows       = "windows"
	tmp           = "tmp"
	html          = "html"
	wkhtmltopdf   = "wkhtmltopdf"
	chromium      = "chromium"
	indexHtml     = "index." + html
	resultPdf     = "result.pdf"
	noIndexHtml   = "No " + indexHtml
	unsupportedOs = "Unsupported Operating System"
	osCmdTimeout  = 30 * time.Second
	portrait      = "portrait"
	landscape     = "landscape"
	a3            = "a3"
)

var (
	wkhtmltopdfExecutableName = getWkhtmltopdfExecutableName()
	chromiumExecutableName    = getChromiumExecutableName()
	A4                        = paperSize{width: "210", height: "297"}
	A3                        = paperSize{width: "297", height: "420"}
)

func getWkhtmltopdfExecutableName() string {
	if linux == osName {
		return wkhtmltopdf
	}
	if windows == osName {
		return "wkhtmltopdf.exe"
	}
	return unsupportedOs
}

func getChromiumExecutableName() string {
	if linux == osName {
		return chromium
	}
	if windows == osName {
		return "chrome.exe"
	}
	return unsupportedOs
}

func isError(err error) bool {
	if err != nil {
		return true
	}
	return false
}

// Get request workdir
func createWorkDir() string {
	goWorkDir, err := filepath.Abs(filepath.Dir(os.Args[0]))
	if isError(err) {
		log.Fatal(err)
	}
	requestWorkDir := filepath.Join(goWorkDir, tmp, strconv.Itoa(rand.Int()))
	if err := os.MkdirAll(requestWorkDir, os.ModePerm); isError(err) {
		log.Fatal(err)
	}
	return requestWorkDir
}

func buildInternalServerError(w http.ResponseWriter, err error) {
	if isError(err) {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

// Store files from Multipart
// http://sanatgersappa.blogspot.com/2013/03/handling-multiple-file-uploads-in-go.html
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

func buildCmd(opts *printerOptions, ctx context.Context) (*exec.Cmd, error) {
	cmd := exec.Cmd{}
	if chromiumExecutableName == opts.executableName {
		cmd = *exec.CommandContext(ctx, chromiumExecutableName, "--headless", "--no-sandbox", "--disable-setuid-sandbox", "--no-zygote", "--single-process", "--disable-notifications", "--disable-geolocation", "--disable-infobars", "--disable-session-crashed-bubble", "--unlimited-storage", "--disable-dev-shm-usage", "--disable-gpu", "--disable-translate", "--disable-extensions", "--disable-background-networking", "--safebrowsing-disable-auto-update", "--disable-sync", "--disable-default-apps", "--hide-scrollbars", "--metrics-recording-only", "--mute-audio", "--no-first-run", "--virtual-time-budget=1000", "--print-to-pdf="+filepath.Join(opts.workdir, resultPdf), filepath.Join(opts.workdir, indexHtml))
	} else if wkhtmltopdfExecutableName == opts.executableName {
		cmd = *exec.CommandContext(ctx, wkhtmltopdfExecutableName,
			"--enable-local-file-access", "--print-media-type", "--no-stop-slow-scripts",
			"--margin-bottom", "0", "--margin-left", "0", "--margin-right", "0", "--margin-top", "0",
			"--page-width", opts.pageWidth, "--page-height", opts.pageHeight, "--orientation", opts.orientation,
			filepath.Join(opts.workdir, indexHtml), filepath.Join(opts.workdir, resultPdf))
	} else {
		return nil, errors.New("Unknown executable " + opts.executableName)
	}
	cmd.Dir = opts.workdir
	return &cmd, nil
}

func callExecutable(opts *printerOptions) error {
	ctx, cancel := context.WithTimeout(context.Background(), osCmdTimeout)
	defer cancel()
	cmd, err := buildCmd(opts, ctx)
	if isError(err) {
		return err
	}
	log.Printf("executing %s in %s", opts.executableName, opts.workdir)
	return cmd.Run()
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

type paperSize struct {
	width  string
	height string
}

type printerOptions struct {
	workdir        string // directory to run converter in
	executableName string // either wkhtmltopdf or chromium executable name
	orientation    string // either Portrait or Landscape
	pageWidth      string // paper width, mm
	pageHeight     string // paper height, mm
}

func newPrinterOptions(workdir string) *printerOptions {
	opts := new(printerOptions)
	opts.workdir = workdir
	opts.orientation = portrait
	opts.pageWidth = A4.width
	opts.pageHeight = A4.height
	return opts
}

func buildPrinterOpions(workdir string, url string) *printerOptions {
	opts := newPrinterOptions(workdir)
	if strings.Contains(url, landscape) {
		opts.orientation = landscape
	}
	if strings.Contains(url, html) {
		opts.executableName = wkhtmltopdfExecutableName
	}
	if strings.Contains(url, chromium) {
		opts.executableName = chromiumExecutableName
	}
	if strings.Contains(url, a3) {
		opts.pageHeight = A3.height
		opts.pageWidth = A3.width
	}
	return opts
}
