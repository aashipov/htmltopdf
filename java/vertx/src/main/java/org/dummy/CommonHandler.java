package org.dummy;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dummy.upload.MFileUploadImpl;

import static io.vertx.core.http.HttpHeaders.*;
import static org.dummy.EmptinessUtils.isBlank;
import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;
import static org.dummy.OsUtils.*;

/**
 * {@link RoutingContext} {@link Handler}.
 */
public class CommonHandler implements Handler<RoutingContext> {

    private static final String HTML = "html";
    private static final String CHROMIUM = "chromium";
    private static final String TEXT_PLAIN = "text/plain; charset=" + DEFAULT_CHARSET_NAME;
    private static final String STATUS_UP = "{\"status\":\"UP\"}";
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final String INDEX_HTML_NOT_FOUND = INDEX_HTML + " not found";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";

    @Override
    public void handle(RoutingContext event) {
        String uri = event.request().uri();
        if (uri.contains(HTML) || uri.contains(CHROMIUM)) {
            convert(event, uri);
        } else {
            health(event);
        }
    }

    private static void convert(RoutingContext event, String uri) {
        HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions();
        po.printoutSettings(uri);
        for (FileUpload fileUpload : event.fileUploads()) {
            if (!isBlank(fileUpload.fileName())) {
                try {
                    MFileUploadImpl mfui = (MFileUploadImpl) fileUpload;
                    if (mfui.getData().length > 0) {
                        Path currentFile = po.getWorkdir().resolve(mfui.fileName());
                        createFile(currentFile);
                        Files.write(currentFile, mfui.getData());
                    }
                } catch (IOException e) {
                    internalServerError(event, e.getMessage());
                }
            }
        }
        Path indexHtml = po.getWorkdir().resolve(INDEX_HTML);
        if (indexHtml.toFile().exists() && indexHtml.toFile().canRead()) {
            po.htmlToPdf();
            if (po.isPdf()) {
                sendPdf(event, po);
            } else {
                internalServerError(event, po.getWrapper().getOutputString() + DELIMITER_LF + po.getWrapper().getErrorString());
            }
        } else {
            internalServerError(event, INDEX_HTML_NOT_FOUND);
        }
        deleteFilesAndDirectories(po.getWorkdir());
    }

    private static void plainTextUtf8Response(RoutingContext event) {
        event
                .response()
                .putHeader(CONTENT_TYPE, TEXT_PLAIN);
    }

    private static void health(RoutingContext event) {
        plainTextUtf8Response(event);
        event.response().end(STATUS_UP);
    }

    private static void internalServerError(RoutingContext event, String reason) {
        plainTextUtf8Response(event);
        event
                .response()
                .setStatusCode(INTERNAL_SERVER_ERROR)
                .end(reason);
    }

    private static void sendPdf(RoutingContext event, HtmlToPdfUtils.PrinterOptions po) {
        event
                .response()
                .putHeader(CONTENT_TYPE, APPLICATION_PDF)
                .putHeader(CONTENT_DISPOSITION, PDF_ATTACHED)
                .putHeader(CONTENT_LENGTH, "" + po.getPdf().length)
                .send(Buffer.buffer(po.getPdf()));
    }
}
