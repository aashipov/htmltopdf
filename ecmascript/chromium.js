import path from 'path';
import { spawn } from 'child_process';
import { chromium as playwrightChromium } from 'playwright-chromium';
import puppeteer from 'puppeteer-core';
import { indexHtml, sendPdf } from './handler.js';
import { buildCurrentPdfFilePath, landscape } from './printeroptions.js';

const mm = 'mm';
const browserTimeout = 600_000;

const getChromiumExecutable = () => {
  const os = process.platform;
  if ('win32' === os) {
    return 'chrome.exe';
  }
  if ('linux' === os) {
    return 'chromium';
  }
  return 'OS not supported';
};

const chromiumEvents = ['load', 'domcontentloaded', 'networkidle0', 'networkidle2'];
const chromiumArgs = '--headless --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 --no-sandbox --no-zygote --disable-setuid-sandbox --disable-notifications --disable-geolocation --disable-infobars --disable-session-crashed-bubble --disable-dev-shm-usage --disable-gpu --disable-translate --disable-extensions --disable-features=site-per-process --disable-hang-monitor --disable-popup-blocking --disable-prompt-on-repost --disable-background-networking --disable-breakpad --disable-client-side-phishing-detection --disable-sync --disable-default-apps --hide-scrollbars --metrics-recording-only --mute-audio --no-first-run --enable-automation --password-store=basic --use-mock-keychain --unlimited-storage --safebrowsing-disable-auto-update --font-render-hinting=none --disable-sync-preferences'.split(' ');

let chromiumProcess;
let puppeteerBrowser;
let playwrightbrowser;

const launchSuccess = () => console.log(`Chromium (re)started`);
const launchFailure = (reason) => {
  console.error(`Chromium failed to (re)start ${reason}`);
  process.exit(1);
};

const launchBrowser = async () => {
  chromiumProcess = spawn(getChromiumExecutable(), chromiumArgs);
  await new Promise(resolve => setTimeout(resolve, 3000));
  puppeteerBrowser = await puppeteer.connect({ browserURL: 'http://0.0.0.0:9222' });
  playwrightbrowser = await playwrightChromium.connectOverCDP('http://0.0.0.0:9222');
};

export const launchChromiumHeadless = async () => {
  await launchBrowser().then(launchSuccess, launchFailure);
};

const buildFileUrl = (printerOptions) =>
  `file://${path.join(printerOptions.workDir, indexHtml)}`;

const buildPdfOpts = (printerOptions) => ({
  preferCSSPageSize: false,
  path: buildCurrentPdfFilePath(printerOptions),
  width: printerOptions.paperSize.widthMm + mm,
  height: printerOptions.paperSize.heightMm + mm,
  landscape: printerOptions.orientation.includes(landscape),
  margin: {
    top: printerOptions.top + mm,
    right: printerOptions.right + mm,
    bottom: printerOptions.bottom + mm,
    left: printerOptions.left + mm
  },
  timeout: browserTimeout
});

const viaPlaywright = async (res, printerOptions) => {
  if (!playwrightbrowser.isConnected()) {
    await launchChromiumHeadless();
  }
  const page = await playwrightbrowser.newPage();
  await page.goto(
    buildFileUrl(printerOptions),
    {
      waitUntil: 'domcontentloaded',
      timeout: browserTimeout
    }
  );
  await page.emulateMedia({ media: 'print' });
  await page.pdf(buildPdfOpts(printerOptions));
  await page.close();
  sendPdf(res, printerOptions);
  printerOptions.removeWorkDir();
};

const viaPuppeteer = async (res, printerOptions) => {
  if (!puppeteerBrowser.isConnected()) {
    await launchChromiumHeadless();
  }
  const page = await puppeteerBrowser.newPage();
  await page.setOfflineMode(true);
  await page.goto(
    buildFileUrl(printerOptions),
    {
      waitUntil: chromiumEvents,
      timeout: browserTimeout
    }
  );
  await page.emulateMediaType('print');
  // page.pdf() is currently supported only in headless mode.
  // @see https://bugs.chromium.org/p/chromium/issues/detail?id=753118
  await page.pdf(buildPdfOpts(printerOptions));
  await page.close();
  sendPdf(res, printerOptions);
  printerOptions.removeWorkDir();
};

export const viaChromium = async (res, printerOptions) => {
  if (process.env.CHROMIUM_HARNESS === 'playwright') {
    await viaPlaywright(res, printerOptions);
  } else {
    await viaPuppeteer(res, printerOptions);
  }
}
