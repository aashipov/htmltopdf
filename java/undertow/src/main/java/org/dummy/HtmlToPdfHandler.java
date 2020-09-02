package org.dummy;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import static org.dummy.HtmlToPdfUtils.*;
import static org.dummy.OsUtils.*;
import static org.dummy.Router.internalServerError;

/**
 * HTML to PDF {@link HttpHandler}.
 * https://stackoverflow.com/a/60584801.
 */
public class HtmlToPdfHandler implements HttpHandler {

    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PDF_ATTACHED = "attachment;filename=\""+ RESULT_PDF +"\"";
    private static final String NO_MULTIPART = "No multipart";
    private static final String INDEX_HTML_NOT_FOUND = INDEX_HTML + " not found";

    private static void storeParts(FormData formData, HtmlToPdfUtils.PrinterOptions po) throws IOException {
        for (String data : formData) {
            for (FormData.FormValue formValue : formData.get(data)) {
                if (formValue.isFileItem()) {
                    // Process file here
                    Path source = formValue.getFileItem().getFile();
                    Path target = po.getWorkdir().resolve(formValue.getFileName());
                    createFile(target);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    deleteFilesAndDirectories(source);
                }
            }
        }
    }

    private static void convert(HttpServerExchange exchange, HtmlToPdfUtils.PrinterOptions po) throws IOException {
        po.htmlToPdf();
        if (po.isPdf()) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, APPLICATION_PDF);
            exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION, PDF_ATTACHED);
            exchange.startBlocking();
            try (OutputStream outputStream = exchange.getOutputStream()) {
                outputStream.write(po.getPdf());
            }
        } else {
            internalServerError(exchange, "No " + RESULT_PDF);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        // https://stackoverflow.com/a/55070318
        // dispatch the request to a blocking thread
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        // Form data is stored here
        FormData formData = exchange.getAttachment(FormDataParser.FORM_DATA);
        if (formData.iterator().hasNext()) {
            String url = exchange.getRequestURL();
            HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions(url);
            // Iterate through form data
            storeParts(formData, po);
            if (po.isIndexHtml()) {
                convert(exchange, po);
            } else {
                internalServerError(exchange, INDEX_HTML_NOT_FOUND);
            }
            deleteFilesAndDirectories(po.getWorkdir());
        } else {
            internalServerError(exchange, NO_MULTIPART);
        }
    }
}
