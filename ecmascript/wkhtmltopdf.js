import { spawnSync } from 'child_process';
import { indexHtml, resultPdf, sendPdf } from './handler.js';

const wkhtmltopdfExecutable = 'wkhtmltopdf';
const wkhtmltopdfTimeout = 600_000;

const buildSpawnOptions = (printerOptions) => (['--enable-local-file-access', '--print-media-type', '--no-stop-slow-scripts', '--disable-smart-shrinking',
    '--margin-bottom', printerOptions.bottom, '--margin-left', printerOptions.left, '--margin-right', printerOptions.right, '--margin-top', printerOptions.top,
    '--page-width', printerOptions.paperSize.widthMm, '--page-height', printerOptions.paperSize.heightMm, '--orientation', printerOptions.orientation,
    indexHtml, resultPdf]);

export const viaWkhtmltopdf = async (res, printerOptions) => {
    let spawnSyncReturns = spawnSync(wkhtmltopdfExecutable, buildSpawnOptions(printerOptions), { cwd: printerOptions.workDir, timeout: wkhtmltopdfTimeout});
    if (spawnSyncReturns.status === 0) {
        sendPdf(res, printerOptions);
        printerOptions.removeWorkDir();
    } else {
        const msg = `Error calling wkhtmltopdf ${error}`;
        console.log(msg);
        res.statusCode = 500;
        res.write(msg);
        printerOptions.removeWorkDir();
        res.end();
    }
}
