import http from 'http';

import { chromium, healthCheck, html, htmlToPdf } from './handler.js';
import { createTmpDir } from './printeroptions.js';
import { launchChromiumHeadless } from './chromium.js';
import { chromiumProcess } from './chromium.js'

const defaultPort = 8080;

const server = http.createServer((request, response) => {
    const { url } = request;
    if (url.includes(chromium) || url.includes(html)) {
        htmlToPdf(request, response);
    } else {
        healthCheck(response);
    }
}).listen(defaultPort);

createTmpDir();

launchChromiumHeadless().catch((reason) => console.log(reason));

process.on('SIGTERM', () => {
    console.log('SIGTERM signal received.');
    chromiumProcess.kill('SIGTERM')
    server.close();
});

process.on('SIGINT', () => {
    console.log('SIGINT signal received.');
    chromiumProcess.kill('SIGINT')
    server.close();
});
