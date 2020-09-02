import http from 'http';

import { chromium, healthCheck, html, htmlToPdf } from './handler.js';
import { createTmpDir } from './printeroptions.js';
import { launchChromiumHeadless } from './chromium.js';

const defaultPort = 8080;

http.createServer((request, response) => {
    const { url } = request;
    if (url.includes(chromium) || url.includes(html)) {
        htmlToPdf(request, response);
    } else {
        healthCheck(response);
    }
}).listen(defaultPort);

createTmpDir();

launchChromiumHeadless().catch((reason) => console.log(reason));
