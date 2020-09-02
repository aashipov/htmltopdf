import path from 'path';
import fs from 'fs-extra';

import { buildCurrentPdfFilePath, buildPrinterOptions } from './printeroptions.js';
import { viaChromium } from './chromium.js';
import { viaWkhtmltopdf } from './wkhtmltopdf.js';
import formidable from 'formidable';

export const html = 'html';
export const indexHtml = 'index.' + html;
export const resultPdf = 'result.pdf';
export const chromium = 'chromium';

const isIndexHtml = (fileNames) => {
    for (let i = 0; i < fileNames.length; i++) {
        if (indexHtml === fileNames[i]) {
            return true;
        }
    }
    return false;
};

const internalServerError = (res, printerOptions, reason) => {
    printerOptions.removeWorkDir();
    res.statusCode = 500;
    console.log(reason);
    //res.write(reason);
    res.end();
};

export const healthCheck = (res) => {
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json;charset=utf-8');
    res.write(JSON.stringify({ "status": "UP" }));
    res.end();
};

export const sendPdf = (response, printerOptions) => {
    const currentPdfFile = buildCurrentPdfFilePath(printerOptions);
    try {
        response.writeHead(
            200, {
            'Content-Type': 'application/pdf',
            'Content-Length': fs.statSync(currentPdfFile).size
        }
        );
        fs.createReadStream(currentPdfFile).pipe(response);
    } catch (error) {
        console.log(`Can not send file ${currentPdfFile}, ${error}`)
    }
    // no response.end(); to send PDF properly
};

export const htmlToPdf = async (req, res) => {
    const printerOptions = buildPrinterOptions(req);
    const form = formidable({ multiples: true, uploadDir: printerOptions.workDir });
    try {
        form
            .on('file',
                (fieldName, file) => {
                    printerOptions.fileNames.push(file.originalFilename);
                    try {
                        fs.renameSync(file.filepath, path.join(printerOptions.workDir, file.originalFilename));
                    } catch (err) {
                        internalServerError(res, printerOptions, err.message);
                    }
                }
            )
            .on('end',
                () => {
                    if (isIndexHtml(printerOptions.fileNames)) {
                        try {
                            if (printerOptions.originalUrl.includes(chromium)) {
                                viaChromium(res, printerOptions);
                            } else if (printerOptions.originalUrl.includes(html)) {
                                viaWkhtmltopdf(res, printerOptions);
                            }
                        } catch (err) {
                            internalServerError(res, printerOptions, err.message);
                        }
                    } else {
                        internalServerError(res, printerOptions, `No ${indexHtml}`);
                    }
                }
            ).on('error',
                (err) => {
                    internalServerError(res, printerOptions, err.message);
                }
            );
        form.parse(req);
    } catch (err) {
        internalServerError(res, printerOptions, err.message);
    }
};
