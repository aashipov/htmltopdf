package org.dummy;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import static org.dummy.EmptinessUtils.isBlank;
import static org.dummy.HtmlToPdfUtils.*;
import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.*;

/**
 * Common {@link AbstractHandler}.
 * @see <a href="https://github.com/jetty-project/embedded-jetty-cookbook">Jetty Cookbook</a>
 */
public class CommonHandler extends AbstractHandler {

    private static final String JETTY_TMP = "jetty-" + getRandomUUID();
    private static final String HTML = "html";
    private static final String CHROMIUM = "chromium";
    private static final String POST = "POST";
    private static final String TEXT_PLAIN = "text/plain; charset=" + DEFAULT_CHARSET_NAME;
    private static final String STATUS_UP = "{\"status\":\"UP\"}";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";
    private static final String INDEX_HTML_NOT_FOUND = INDEX_HTML + " not found";
    private static final String NO_MULTIPART = "No multipart";
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_DISPOSITION = "Content-Disposition";

    private final MultipartConfigElement multipartConfig;

    /**
     * Constructor.
     */
    public CommonHandler() {
        super();
        deleteFilesAndDirectories(TMP_DIR);
        Path multipartTmpDir = TMP_DIR.resolve(JETTY_TMP);
        createDirectory(multipartTmpDir);
        this.multipartConfig = new MultipartConfigElement(multipartTmpDir.toString());
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if ((request.getRequestURI().contains(HTML) || request.getRequestURI().contains(CHROMIUM))
                && request.getMethod().equalsIgnoreCase(POST)) {
            request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig);
            processParts(request, response);
        } else {
            health(response);
        }
        baseRequest.setHandled(true);
    }

    private static void storeParts(HttpServletRequest request, HtmlToPdfUtils.PrinterOptions po) throws IOException, ServletException {
        for (Part part : request.getParts()) {
            String filename = part.getSubmittedFileName();
            if (!isBlank(filename)) {
                // ensure we don't have "/" and ".." in the raw form.
                filename = URLEncoder.encode(filename, DEFAULT_CHARSET_NAME);
                Path file = po.getWorkdir().resolve(filename);
                try (InputStream inputStream = part.getInputStream();
                     OutputStream outputStream = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    inputStream.transferTo(outputStream);
                } catch (Exception e) {
                    //
                }
            }
        }
    }

    private static void convert(HttpServletResponse response, HtmlToPdfUtils.PrinterOptions po) throws IOException {
        po.htmlToPdf();
        if (po.isPdf()) {
            response.setContentType(APPLICATION_PDF);
            response.setHeader(CONTENT_DISPOSITION, PDF_ATTACHED);
            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(po.getPdf());
            }
        } else {
            internalServerError(response, po.getWrapper().getOutputString() + DELIMITER_LF + po.getWrapper().getErrorString());
        }
    }

    private static void processParts(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!request.getParts().isEmpty()) {
            HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions();
            String url = request.getRequestURI();
            po.printoutSettings(url);
            createDirectory(po.getWorkdir());
            storeParts(request, po);
            Path indexHtml = po.getWorkdir().resolve(INDEX_HTML);
            if (indexHtml.toFile().exists() && indexHtml.toFile().canRead()) {
                convert(response, po);
            } else {
                internalServerError(response, INDEX_HTML_NOT_FOUND);
            }
            deleteFilesAndDirectories(po.getWorkdir());
        } else {
            internalServerError(response, NO_MULTIPART);
        }
    }

    private static void plainTextUtf8Response(HttpServletResponse response) {
        response.setContentType(TEXT_PLAIN);
    }

    private static void health(HttpServletResponse response) throws IOException {
        plainTextUtf8Response(response);
        response.getWriter().print(STATUS_UP);
    }

    private static void internalServerError(HttpServletResponse response, String reason) throws IOException {
        plainTextUtf8Response(response);
        response.setStatus(INTERNAL_SERVER_ERROR);
        response.getWriter().print(reason);
    }
}
