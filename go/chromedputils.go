// Convert HTML to PDF via Chrome DevTools Protocol (chromedp implementation)
package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"path/filepath"

	"github.com/chromedp/cdproto/page"
	"github.com/chromedp/chromedp"
)

var (
	chromedpContext context.Context = nil
)

// https://github.com/chromedp/chromedp/issues/438
func getChromiumwebSocketDebuggerURL() string {
	resp, err := http.Get("http://0.0.0.0:9222/json/version")
	if isError(err) {
		log.Fatal(err)
	}

	var result map[string]interface{}

	if err := json.NewDecoder(resp.Body).Decode(&result); isError(err) {
		log.Fatal(err)
	}
	return result["webSocketDebuggerUrl"].(string)
}

func (opts *printerOptions) buildChromedpPrintToPDF() *page.PrintToPDFParams {
	params := page.PrintToPDF()
	if landscape == opts.orientation {
		params.Landscape = true
	}
	params.PaperWidth = mmToInch(opts.paperSize.widthMm)
	params.PaperHeight = mmToInch(opts.paperSize.heightMm)
	params.MarginTop = mmToInch(opts.top)
	params.MarginBottom = mmToInch(opts.bottom)
	params.MarginLeft = mmToInch(opts.left)
	params.MarginRight = mmToInch(opts.right)
	return params
}

// waitFor blocks until eventName is received.
// Examples of events you can wait for:
//
//	init, DOMContentLoaded, firstPaint,
//	firstContentfulPaint, firstImagePaint,
//	firstMeaningfulPaintCandidate,
//	load, networkAlmostIdle, firstMeaningfulPaint, networkIdle
//
// This is not super reliable, I've already found incidental cases where
// networkIdle was sent before load. It's probably smart to see how
// puppeteer implements this exactly.
// https://github.com/chromedp/chromedp/issues/431
func waitFor(ctx context.Context, eventName string) error {
	ch := make(chan struct{})
	cctx, cancel := context.WithCancel(ctx)
	chromedp.ListenTarget(cctx, func(ev interface{}) {
		switch e := ev.(type) {
		case *page.EventLifecycleEvent:
			if e.Name == eventName {
				cancel()
				close(ch)
			}
		}
	})
	select {
	case <-ch:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func chromedpPrintToPdf(opts *printerOptions, res *[]byte) chromedp.ActionFunc {
	return func(ctx context.Context) error {
		buf, _, err := opts.buildChromedpPrintToPDF().Do(ctx)
		if err != nil {
			return err
		}
		*res = buf
		return nil
	}
}

// https://github.com/chromedp/chromedp/issues/431
func enableLifeCycleEvents() chromedp.ActionFunc {
	return func(ctx context.Context) error {
		err := page.Enable().Do(ctx)
		if isError(err) {
			return err
		}
		err = page.SetLifecycleEventsEnabled(true).Do(ctx)
		if isError(err) {
			return err
		}
		return nil
	}
}

// https://github.com/chromedp/chromedp/issues/431
func navigateAndWaitFor(url string, eventName string) chromedp.ActionFunc {
	return func(ctx context.Context) error {
		_, _, _, err := page.Navigate(url).Do(ctx)
		if isError(err) {
			return err
		}
		return waitFor(ctx, eventName)
	}
}

func buildChromedpTasks(opts *printerOptions, res *[]byte) chromedp.Tasks {
	return chromedp.Tasks{
		enableLifeCycleEvents(),
		navigateAndWaitFor("file://"+filepath.Join(opts.workdir, indexHTML), networkIdleEventName),
		chromedpPrintToPdf(opts, res),
	}
}

func (opts *printerOptions) viaChromedp(ctx context.Context) error {
	if chromedpContext == nil {
		chromedpContext, _ = chromedp.NewRemoteAllocator(context.Background(), getChromiumwebSocketDebuggerURL())
	}
	resolver := func() error {
		taskCtx, cancelCtxt := chromedp.NewContext(chromedpContext) // create new tab
		defer cancelCtxt()
		return chromedp.Run(taskCtx, buildChromedpTasks(opts, &opts.pdf))
	}
	if devtConnections < maxDevtConnections {
		devtConnections++
		err := resolver()
		devtConnections--
		if isError(err) {
			return err
		}
		return nil
	}
	select {
	case lockChrome <- struct{}{}:
		err := resolver()
		<-lockChrome // release
		if isError(err) {
			return err
		}
		return nil
	case <-ctx.Done():
		return errors.New("timed out")
	}
}
