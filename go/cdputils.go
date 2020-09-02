// Convert HTML to PDF via Chrome DevTools Protocol (cdp implementation)
// Simplified copy-paste from https://github.com/thecodingmachine/gotenberg
package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"path/filepath"

	"github.com/mafredri/cdp"
	"github.com/mafredri/cdp/devtool"
	"github.com/mafredri/cdp/protocol/network"
	"github.com/mafredri/cdp/protocol/page"
	"github.com/mafredri/cdp/protocol/target"
	"github.com/mafredri/cdp/rpcc"
	"golang.org/x/sync/errgroup"
)

var (
	devtClient *cdp.Client = nil
)

func startCDPClient() *cdp.Client {
	ctx := context.Background()
	devt, err := devtool.New("http://0.0.0.0:9222").Version(ctx)
	if isError(err) {
		log.Fatal(err)
	}
	// connect to WebSocket URL (page) that speaks the Chrome DevTools Protocol.
	devtConn, err := rpcc.DialContext(ctx, devt.WebSocketDebuggerURL)
	if isError(err) {
		log.Fatal(err)
	}
	// create a new CDP Client that uses conn.
	return cdp.NewClient(devtConn)
}

// Copy-paste https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func runBatch(fn ...func() error) error {
	// run all functions simultaneously and wait until
	// execution has completed or an error is encountered.
	eg := errgroup.Group{}
	for _, f := range fn {
		eg.Go(f)
	}
	return eg.Wait()
}

// Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func cdpEnableEvents(ctx context.Context, client *cdp.Client) error {
	// enable all the domain events that we're interested in.
	return runBatch(
		func() error { return client.DOM.Enable(ctx, nil) },
		func() error { return client.Network.Enable(ctx, network.NewEnableArgs()) },
		func() error { return client.Page.Enable(ctx) },
		func() error {
			return client.Page.SetLifecycleEventsEnabled(ctx, page.NewSetLifecycleEventsEnabledArgs(true))
		},
		func() error { return client.Runtime.Enable(ctx) },
	)
}

// Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
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
	if _, err := client.Page.Navigate(ctx, page.NewNavigateArgs("file://"+filepath.Join(opts.workdir, indexHTML))); isError(err) {
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
	printToPdfArgs.SetPaperWidth(mmToInch(opts.paperSize.widthMm))
	printToPdfArgs.SetPaperHeight(mmToInch(opts.paperSize.heightMm))
	if landscape == opts.orientation {
		printToPdfArgs.SetLandscape(true)
	}
	printToPdfArgs.SetMarginLeft(mmToInch(opts.left))
	printToPdfArgs.SetMarginRight(mmToInch(opts.right))
	printToPdfArgs.SetMarginTop(mmToInch(opts.top))
	printToPdfArgs.SetMarginBottom(mmToInch(opts.bottom))
	return printToPdfArgs, nil
}

// Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
func (opts *printerOptions) viaCdpInner(ctx context.Context) error {
	createBrowserContextArgs := target.NewCreateBrowserContextArgs()
	if devtClient == nil {
		devtClient = startCDPClient()
	}
	newContextTarget, err := devtClient.Target.CreateBrowserContext(ctx, createBrowserContextArgs)
	if isError(err) {
		log.Fatal(err)
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
	opts.pdf = printToPDF.Data
	return nil
}

// Simplified https://github.com/thecodingmachine/gotenberg/blob/master/internal/pkg/printer/chrome.go
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
