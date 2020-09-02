import fs from 'fs-extra';
import path from 'path';

import { resultPdf } from './handler.js';

const leftMarginName = 'left';
const rightMarginName = 'right';
const topMarginName = 'top';
const bottomMarginName = 'bottom';

export class PrinterOptions {
    constructor(workDir, fileNames, originalUrl, paperSize, orientation, left, right, top, bottom) {
        this.workDir = workDir;
        this.fileNames = fileNames;
        this.originalUrl = originalUrl;
        this.paperSize = paperSize;
        this.orientation = orientation;
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }
    removeWorkDir() {
        fs.remove(this.workDir);
    }
}

class PaperSize {
    constructor(widthMm, heightMm) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }
}

const A4 = new PaperSize('210', '297');
const A3 = new PaperSize('297', '420');
const a3 = 'a3';
const defaultMargin = '20'
const portrait = 'portrait';
export const landscape = 'landscape';

const oneOrMoreDigitsRe = new RegExp(/\d+/);
const fillMarginNameReMap = () => {
    let m = new Map();
    m.set(leftMarginName, new RegExp(/left\d+/));
    m.set(rightMarginName, new RegExp(/right\d+/));
    m.set(topMarginName, new RegExp(/top\d+/));
    m.set(bottomMarginName, new RegExp(/bottom\d+/));
    return m;
};
const marginNameReMap = fillMarginNameReMap();

const mkdirSync = (dir) => {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir);
    }
};

const tmpDir = path.join(path.resolve(), 'tmp');
export const createTmpDir = () => mkdirSync(tmpDir);
export const buildCurrentPdfFilePath = (printerOptions) => path.join(printerOptions.workDir, resultPdf);

export const buildPrinterOptions = (req) => {
    const workDir = path.join(tmpDir, '' + Math.random() + '-' + Math.random());
    mkdirSync(workDir);
    const printerOptions =
        new PrinterOptions(
            workDir,
            [],
            req.url,
            A4,
            portrait,
            defaultMargin, defaultMargin, defaultMargin, defaultMargin);
    if (printerOptions.originalUrl.includes(a3)) {
        printerOptions.paperSize = A3;
    }
    if (printerOptions.originalUrl.includes(landscape)) {
        printerOptions.orientation = landscape
    }
    for (let [marginName, re] of marginNameReMap.entries()) {
        let marginNameWithDigits = printerOptions.originalUrl.match(re);
        if (null != marginNameWithDigits) {
            let marginDigits = marginNameWithDigits[0].match(oneOrMoreDigitsRe)[0];
            if (leftMarginName === marginName) {
                printerOptions.left = marginDigits;
            }
            if (rightMarginName === marginName) {
                printerOptions.right = marginDigits;
            }
            if (topMarginName === marginName) {
                printerOptions.top = marginDigits;
            }
            if (bottomMarginName === marginName) {
                printerOptions.bottom = marginDigits;
            }
        }
    }
    return printerOptions;
}
