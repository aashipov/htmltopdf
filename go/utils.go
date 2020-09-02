// Common functions, environment and wkhtmltopdf
package main

import (
	"bytes"
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
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"
)

const (
	osName               = runtime.GOOS
	chromium             = "chromium"
	tmp                  = "tmp"
	slash                = "/"
	html                 = "html"
	wkhtmltopdf          = "wkhtmltopdf"
	indexHTML            = "index." + html
	resultPdf            = "result.pdf"
	noIndexHTML          = "no " + indexHTML
	unsupportedOs        = "Unsupported Operating System"
	osCmdTimeout         = 600 * time.Second
	portrait             = "portrait"
	landscape            = "landscape"
	a3                   = "a3"
	left                 = "left"
	right                = "right"
	top                  = "top"
	bottom               = "bottom"
	oneOrMoreDigits      = `\d+`
	defaultMargin        = "20" // all margins, mm
	mmInInch             = 25.4
	maxDevtConnections   = 20
	networkIdleEventName = "networkIdle"
)

var (
	// A4 Paper size A4
	A4 = paperSize{widthMm: "210", heightMm: "297"}
	// A3 Paper size A3
	A3                       = paperSize{widthMm: "297", heightMm: "420"}
	oneOrMoreDigitsRe        = regexp.MustCompile(oneOrMoreDigits)
	marginNameReMap          = fillMarginNameReMap()
	htmlToPdfConverterFailed = []byte("Something went wrong with HTML to PDF converter")
	// nolint: gochecknoglobals
	lockChrome = make(chan struct{}, 1)
	// nolint: gochecknoglobals
	devtConnections = 0
	statusUp        = []byte("{\"status\":\"UP\"}")
)

func getChromiumExecutableFileName() (string, error) {
	if osName == "linux" {
		return chromium, nil
	}
	if osName == "windows" {
		return "chrome.exe", nil
	}
	return "", errors.New("OS not supported")
}

// margin name -> regexp
func fillMarginNameReMap() map[string]*regexp.Regexp {
	m := make(map[string]*regexp.Regexp)
	m[left] = regexp.MustCompile(left + oneOrMoreDigits)
	m[right] = regexp.MustCompile(right + oneOrMoreDigits)
	m[top] = regexp.MustCompile(top + oneOrMoreDigits)
	m[bottom] = regexp.MustCompile(bottom + oneOrMoreDigits)
	return m
}

func isError(err error) bool {
	return nil != err
}

func enableGracefulShutdown(server *http.Server, chromiumProcess *os.Process) {
	gracefulShutdown := make(chan os.Signal, 1)
	signal.Notify(gracefulShutdown, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-gracefulShutdown
		log.Printf("%s received, shutdown", sig)
		server.Close()
		chromiumProcess.Kill()
		os.Exit(0)
	}()
}

func launchBrowser() *os.Process {
	chromiumExecutableFileName, err := getChromiumExecutableFileName()
	if err != nil {
		log.Printf("Can not get chromium executable file name, %s", err)
		os.Exit(0)
	}
	cmd := exec.Command(chromiumExecutableFileName, "--headless", "--remote-debugging-address=0.0.0.0", "--remote-debugging-port=9222", "--no-sandbox", "--no-zygote", "--disable-setuid-sandbox", "--disable-notifications", "--disable-geolocation", "--disable-infobars", "--disable-session-crashed-bubble", "--disable-dev-shm-usage", "--disable-gpu", "--disable-translate", "--disable-extensions", "--disable-features=site-per-process", "--disable-hang-monitor", "--disable-popup-blocking", "--disable-prompt-on-repost", "--disable-background-networking", "--disable-breakpad", "--disable-client-side-phishing-detection", "--disable-sync", "--disable-default-apps", "--hide-scrollbars", "--metrics-recording-only", "--mute-audio", "--no-first-run", "--enable-automation", "--password-store=basic", "--use-mock-keychain", "--unlimited-storage", "--safebrowsing-disable-auto-update", "--font-render-hinting=none", "--disable-sync-preferences")
	err = cmd.Start()
	if err != nil {
		log.Printf("Can launch chromium headless, %s", err)
		os.Exit(0)
	}
	return cmd.Process
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
	indexHTMLReceived := false
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
		if indexHTML == part.FileName() {
			indexHTMLReceived = true
		}
	}
	if !indexHTMLReceived {
		return errors.New(noIndexHTML)
	}
	return nil
}

// Send PDF to client
func (opts *printerOptions) sendPdf(w http.ResponseWriter) error {
	if bytes.Equal(htmlToPdfConverterFailed, opts.pdf) {
		return errors.New(string(htmlToPdfConverterFailed))
	}
	w.Write(opts.pdf)
	w.Header().Set("Content-Disposition", "attachment;filename=\""+resultPdf+"\"")
	w.Header().Set("Content-Type", "application/pdf")
	return nil
}

func health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write(statusUp)
}

func (opts *printerOptions) readResultPdf() error {
	buf, err := os.ReadFile(filepath.Join(opts.workdir, resultPdf))
	opts.pdf = buf
	if isError(err) {
		return err
	}
	return nil
}

func (opts *printerOptions) print(w http.ResponseWriter) error {
	ctx, cancel := context.WithTimeout(context.Background(), osCmdTimeout)
	defer cancel()
	if chromium == opts.executableName {
		chromiumHarness, found := syscall.Getenv("CHROMIUM_HARNESS")
		if found && chromiumHarness == "chromedp" {
			opts.viaChromedp(ctx)
		} else {
			opts.viaCdp(ctx)
		}
	} else if wkhtmltopdf == opts.executableName {
		cmd := *exec.CommandContext(ctx, wkhtmltopdf,
			"--enable-local-file-access", "--print-media-type", "--no-stop-slow-scripts", "--disable-smart-shrinking",
			"--margin-bottom", opts.bottom, "--margin-left", opts.left, "--margin-right", opts.right, "--margin-top", opts.top,
			"--page-width", opts.paperSize.widthMm, "--page-height", opts.paperSize.heightMm, "--orientation", opts.orientation,
			filepath.Join(opts.workdir, indexHTML), filepath.Join(opts.workdir, resultPdf))
		if err := cmd.Run(); isError(err) {
			return err
		}
		opts.readResultPdf()
	} else {
		return errors.New("unknown executable " + opts.executableName)
	}
	return opts.sendPdf(w)
}

func htmlToPdf(w http.ResponseWriter, r *http.Request) {
	workdir := createWorkDir()
	defer os.RemoveAll(workdir)
	opts := buildPrinterOpions(workdir, r)
	// Store multipart
	if err := receiveFiles(w, r, workdir); isError(err) {
		log.Print(err)
		buildInternalServerError(w, err)
		return
	}
	// convert
	if err := opts.print(w); isError(err) {
		log.Print(err)
		buildInternalServerError(w, err)
		return
	}
}

// Office paper size
type paperSize struct {
	widthMm  string // millimeters
	heightMm string
}

// Converter task definition
type printerOptions struct {
	workdir        string // directory to run converter in
	executableName string // either wkhtmltopdf or chromium executable name
	orientation    string // either portrait or landscape
	paperSize      *paperSize
	left           string // margins in mm
	right          string
	top            string
	bottom         string
	pdf            []byte
}

func buildPrinterOpions(workdir string, r *http.Request) *printerOptions {
	opts := new(printerOptions)
	opts.workdir = workdir
	opts.pdf = htmlToPdfConverterFailed
	url := r.URL.String()
	if strings.Contains(url, landscape) {
		opts.orientation = landscape
	} else {
		opts.orientation = portrait
	}
	if strings.Contains(url, html) {
		opts.executableName = wkhtmltopdf
	} else if strings.Contains(url, chromium) {
		opts.executableName = chromium
	}
	if strings.Contains(url, a3) {
		opts.paperSize = &A3
	} else {
		opts.paperSize = &A4
	}
	// margin initialization
	for marginName, re := range marginNameReMap {
		marginDigits := defaultMargin
		marginNameWithDigits := re.FindString(url)
		if len(marginNameWithDigits) > 0 {
			marginDigits = oneOrMoreDigitsRe.FindString(marginNameWithDigits)
		}
		if len(marginDigits) > 0 {
			if left == marginName {
				opts.left = marginDigits
			}
			if right == marginName {
				opts.right = marginDigits
			}
			if top == marginName {
				opts.top = marginDigits
			}
			if bottom == marginName {
				opts.bottom = marginDigits
			}
		}
	}
	return opts
}

func mmToInch(mm string) float64 {
	if inch, err := strconv.ParseFloat(mm, 64); !isError(err) {
		return inch / mmInInch
	}
	return 0
}
