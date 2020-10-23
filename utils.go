package main

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
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

	"github.com/mafredri/cdp"
	"github.com/mafredri/cdp/devtool"
	"github.com/mafredri/cdp/protocol/network"
	"github.com/mafredri/cdp/protocol/page"
	"github.com/mafredri/cdp/protocol/target"
	"github.com/mafredri/cdp/rpcc"
	"golang.org/x/sync/errgroup"
)

const (
	osName               = runtime.GOOS
	linux                = "linux"
	windows              = "windows"
	tmp                  = "tmp"
	slash                = "/"
	html                 = "html"
	wkhtmltopdf          = "wkhtmltopdf"
	chromium             = "chromium"
	indexHtml            = "index." + html
	resultPdf            = "result.pdf"
	noIndexHtml          = "No " + indexHtml
	unsupportedOs        = "Unsupported Operating System"
	osCmdTimeout         = 30 * time.Second
	portrait             = "portrait"
	landscape            = "landscape"
	a3                   = "a3"
	maxDevtConnections   = 10
	networkIdleEventName = "networkIdle"
	left                 = `left`
	right                = `right`
	top                  = `top`
	bottom               = `bottom`
	oneOrMoreDigits      = `\d+`
	defaultMargin        = `20` // all margins, mm
)

var (
	wkhtmltopdfExecutableName = getWkhtmltopdfExecutableName()
	// nolint: gochecknoglobals
	lockChrome = make(chan struct{}, 1)
	// nolint: gochecknoglobals
	devtConnections = 0
	// A4 Paper size A4
	A4 = paperSize{widthMm: "210", widthIn: 8.5, heightMm: "297", heightIn: 11.71}
	// A3 Paper size A3
	A3                = paperSize{widthMm: "297", widthIn: 11.71, heightMm: "420", heightIn: 16.54}
	marginNames       = []string{left, right, top, bottom}
	oneOrMoreDigitsRe = regexp.MustCompile(oneOrMoreDigits)
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
	w.Header().Set("Content-Disposition", "attachment;filename=\"result.pdf\"")
	w.Header().Set("Content-Type", "application/pdf")
	return file.Close()
}

func health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte("{\"status\":\"UP\"}"))
}

//Copy-paste https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func runBatch(fn ...func() error) error {
	// run all functions simultaneously and wait until
	// execution has completed or an error is encountered.
	eg := errgroup.Group{}
	for _, f := range fn {
		eg.Go(f)
	}
	return eg.Wait()
}

//Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func cdpEnableEvents(ctx context.Context, client *cdp.Client) error {
	// enable all the domain events that we're interested in.
	return runBatch(
		func() error { return client.DOM.Enable(ctx) },
		func() error { return client.Network.Enable(ctx, network.NewEnableArgs()) },
		func() error { return client.Page.Enable(ctx) },
		func() error {
			return client.Page.SetLifecycleEventsEnabled(ctx, page.NewSetLifecycleEventsEnabledArgs(true))
		},
		func() error { return client.Runtime.Enable(ctx) },
	)
}

//Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func (opts *printerOptions) cdpListenEventsAndNavigate(ctx context.Context, client *cdp.Client) error {
	// make sure Page events are enabled.
	if err := client.Page.Enable(ctx); isError(err) {
		return err
	}
	// make sure Network events are enabled.
	if err := client.Network.Enable(ctx, nil); isError(err) {
		return err
	}
	// create all clients for events.
	domContentEventFired, err := client.Page.DOMContentEventFired(ctx)
	if isError(err) {
		return err
	}
	defer domContentEventFired.Close()
	loadEventFired, err := client.Page.LoadEventFired(ctx)
	if isError(err) {
		return err
	}
	defer loadEventFired.Close()
	lifecycleEvent, err := client.Page.LifecycleEvent(ctx)
	if isError(err) {
		return err
	}
	defer lifecycleEvent.Close()
	loadingFinished, err := client.Network.LoadingFinished(ctx)
	if isError(err) {
		return err
	}
	defer loadingFinished.Close()
	// Navigate
	if _, err := client.Page.Navigate(ctx, page.NewNavigateArgs("file://"+filepath.Join(opts.workdir, indexHtml))); isError(err) {
		return err
	}
	// wait for all events.
	return runBatch(
		func() error {
			if _, err := domContentEventFired.Recv(); isError(err) {
				return err
			}
			return nil
		},
		func() error {
			if _, err := loadEventFired.Recv(); isError(err) {
				return err
			}
			return nil
		},
		func() error {
			for {
				ev, err := lifecycleEvent.Recv()
				if isError(err) {
					return err
				}
				if ev.Name == networkIdleEventName {
					break
				}
			}
			return nil
		},
		func() error {
			if _, err := loadingFinished.Recv(); isError(err) {
				return err
			}
			return nil
		},
	)
}

func (opts *printerOptions) cdpPrintToPDFArgs() (*page.PrintToPDFArgs, error) {
	printToPdfArgs := page.NewPrintToPDFArgs()
	printToPdfArgs.SetPaperWidth(opts.paperSize.widthIn)
	printToPdfArgs.SetPaperHeight(opts.paperSize.heightIn)
	if landscape == opts.orientation {
		printToPdfArgs.SetLandscape(true)
	}
	// easier to set those in CSS
	printToPdfArgs.SetMarginLeft(0).SetMarginRight(0).SetMarginTop(0).SetMarginBottom(0)
	return printToPdfArgs, nil
}

//Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func (opts *printerOptions) viaCdpInner(ctx context.Context) error {
	devt, err := devtool.New("http://localhost:9222").Version(ctx)
	if isError(err) {
		return err
	}
	// connect to WebSocket URL (page) that speaks the Chrome DevTools Protocol.
	devtConn, err := rpcc.DialContext(ctx, devt.WebSocketDebuggerURL)
	if isError(err) {
		return err
	}
	defer devtConn.Close()
	// create a new CDP Client that uses conn.
	devtClient := cdp.NewClient(devtConn)
	createBrowserContextArgs := target.NewCreateBrowserContextArgs()
	newContextTarget, err := devtClient.Target.CreateBrowserContext(ctx, createBrowserContextArgs)
	if isError(err) {
		return err
	}
	/*
		close the browser context when done.
		we're not using the "default" context
		as it may timeout before actually closing
		the browser context.
		see: https://github.com/mafredri/cdp/issues/101#issuecomment-524533670
	*/
	disposeBrowserContextArgs := target.NewDisposeBrowserContextArgs(newContextTarget.BrowserContextID)
	defer devtClient.Target.DisposeBrowserContext(context.Background(), disposeBrowserContextArgs) // nolint: errcheck
	// create a new blank target with the new browser context.
	createTargetArgs := target.
		NewCreateTargetArgs("about:blank").
		SetBrowserContextID(newContextTarget.BrowserContextID)
	newTarget, err := devtClient.Target.CreateTarget(ctx, createTargetArgs)
	if isError(err) {
		return err
	}
	// connect the client to the new target.
	newTargetWsURL := fmt.Sprintf("ws://127.0.0.1:9222/devtools/page/%s", newTarget.TargetID)
	newContextConn, err := rpcc.DialContext(
		ctx,
		newTargetWsURL,
		/*
			see:
			https://github.com/thecodingmachine/gotenberg/issues/108
			https://github.com/mafredri/cdp/issues/4
			https://github.com/ChromeDevTools/devtools-protocol/issues/24
		*/
		//rpcc.WithWriteBufferSize(int(p.opts.RpccBufferSize)),
		rpcc.WithCompression(),
	)
	if isError(err) {
		return err
	}
	defer newContextConn.Close()
	// create a new CDP Client that uses newContextConn.
	targetClient := cdp.NewClient(newContextConn)
	/*
		close the target when done.
		we're not using the "default" context
		as it may timeout before actually closing
		the target.
		see: https://github.com/mafredri/cdp/issues/101#issuecomment-524533670
	*/
	closeTargetArgs := target.NewCloseTargetArgs(newTarget.TargetID)
	defer targetClient.Target.CloseTarget(context.Background(), closeTargetArgs) // nolint: errcheck
	if err := cdpEnableEvents(ctx, targetClient); isError(err) {
		return err
	}
	// listen for all events.
	if err := opts.cdpListenEventsAndNavigate(ctx, targetClient); isError(err) {
		return err
	}

	printToPdfArgs, err := opts.cdpPrintToPDFArgs()
	if isError(err) {
		return err
	}
	// printToPDF the page to PDF.
	printToPDF, err := targetClient.Page.PrintToPDF(ctx, printToPdfArgs)
	if isError(err) {
		return err
	}
	if err := ioutil.WriteFile(filepath.Join(opts.workdir, resultPdf), printToPDF.Data, os.ModePerm); isError(err) {
		return err
	}
	return nil
}

//Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func (opts *printerOptions) viaCdp(ctx context.Context) error {
	if devtConnections < maxDevtConnections {
		devtConnections++
		err := runBatch(func() error { return opts.viaCdpInner(ctx) })
		devtConnections--
		if isError(err) {
			return err
		}
		return nil
	}
	select {
	case lockChrome <- struct{}{}:
		// lock acquired.
		devtConnections++
		err := runBatch(func() error { return opts.viaCdpInner(ctx) })
		devtConnections--
		<-lockChrome // we release the lock.
		if isError(err) {
			return err
		}
		return nil
	case <-ctx.Done():
		// failed to acquire lock before
		// deadline.
		return errors.New("timed out")
	}
}

func (opts *printerOptions) print() error {
	ctx, cancel := context.WithTimeout(context.Background(), osCmdTimeout)
	defer cancel()
	if chromium == opts.executableName {
		return opts.viaCdp(ctx)
	} else if wkhtmltopdfExecutableName == opts.executableName {
		cmd := *exec.CommandContext(ctx, wkhtmltopdfExecutableName,
			"--enable-local-file-access", "--print-media-type", "--no-stop-slow-scripts",
			"--margin-bottom", opts.bottom, "--margin-left", opts.left, "--margin-right", opts.right, "--margin-top", opts.top,
			"--page-width", opts.paperSize.widthMm, "--page-height", opts.paperSize.heightMm, "--orientation", opts.orientation,
			filepath.Join(opts.workdir, indexHtml), filepath.Join(opts.workdir, resultPdf))
		return cmd.Run()
	} else {
		return errors.New("Unknown executable " + opts.executableName)
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

// Office paper size
// mm for wkhtml
// in for chromium
type paperSize struct {
	widthMm  string  // millimeters
	widthIn  float64 // inches
	heightMm string
	heightIn float64
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
}

func buildPrinterOpions(workdir string, url string) *printerOptions {
	opts := new(printerOptions)
	opts.workdir = workdir
	if strings.Contains(url, landscape) {
		opts.orientation = landscape
	} else {
		opts.orientation = portrait
	}
	if strings.Contains(url, html) {
		opts.executableName = wkhtmltopdfExecutableName
	} else if strings.Contains(url, chromium) {
		opts.executableName = chromium
	}
	if strings.Contains(url, a3) {
		opts.paperSize = &A3
	} else {
		opts.paperSize = &A4
	}
	// margin initialization
	for _, marginName := range marginNames {
		marginNameWithDigitsRe := regexp.MustCompile(marginName + oneOrMoreDigits)
		marginNameWithDigits := marginNameWithDigitsRe.FindString(url)
		marginDigits := defaultMargin
		if len(marginNameWithDigits) > 0 {
			log.Print(`found margin ` + marginNameWithDigits)
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
