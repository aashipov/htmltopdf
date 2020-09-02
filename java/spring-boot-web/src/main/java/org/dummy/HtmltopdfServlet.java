package org.dummy;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;
import static org.dummy.OsUtils.*;

/**
 * {@link WebServlet}.
 */
@WebServlet(urlPatterns = "/*")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 5 * 5)
public class HtmltopdfServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String TEXT_PLAIN = "text/plain; charset=" + DEFAULT_CHARSET_NAME;
    static final String STATUS_UP = "{\"status\":\"UP\"}";
    static final String CHROMIUM = "chromium";
    static final String HTML = "html";
    private static final String FILENAME = "filename";
    private static final String DELIMITER_SEMICOLON = ";";
    private static final String DELIMITER_EQUALS_SIGN = "=";

    @SuppressWarnings("java:S3776")
    static String getFileName(Part part) {
        for (String content : part.getHeader(CONTENT_DISPOSITION).split(DELIMITER_SEMICOLON)) {
            String trimmed = content.trim();
            if (trimmed.startsWith(FILENAME) && trimmed.contains(DELIMITER_EQUALS_SIGN)) {
                String filename = trimmed.substring(trimmed.indexOf(DELIMITER_EQUALS_SIGN) + 1);
                while (filename.startsWith(ESCAPED_DOUBLE_QUOTATION_MARK) || filename.endsWith(ESCAPED_DOUBLE_QUOTATION_MARK)) {
                    if (filename.startsWith(ESCAPED_DOUBLE_QUOTATION_MARK)) {
                        filename = filename.substring(1);
                    }
                    if (filename.endsWith(ESCAPED_DOUBLE_QUOTATION_MARK)) {
                        filename = filename.substring(0, filename.length() - 1);
                    }
                }
                return filename;
            }
        }
        return null;
    }

    static void notAcceptable(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
        response.getWriter().println(msg);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType(TEXT_PLAIN);
        response.getWriter().println(STATUS_UP);
    }

    @SuppressWarnings("java:S3776")
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String url = request.getRequestURL().toString();
        if (url.contains(HTML) || url.contains(CHROMIUM)) {
            HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions(url);
            for (Part part : request.getParts()) {
                String filename = getFileName(part);
                if (!isBlank(filename)) {
                    Path file = po.getWorkdir().resolve(filename);
                    try (InputStream inputStream = part.getInputStream(); OutputStream outputStream = Files.newOutputStream(file)) {
                        inputStream.transferTo(outputStream);
                    }
                } else {
                    notAcceptable(response, "No filename");
                    break;
                }
            }
            if (po.isIndexHtml()) {
                po.htmlToPdf();
                if (po.isPdf()) {
                    response.setContentType(APPLICATION_PDF);
                    response.addHeader(CONTENT_DISPOSITION, PDF_ATTACHED);
                    response.setContentLength(po.getPdf().length);
                    try (OutputStream outputStream = response.getOutputStream()) {
                        outputStream.write(po.getPdf());
                        outputStream.flush();
                    }
                } else {
                    notAcceptable(response, "No " + RESULT_PDF);
                }
            } else {
                notAcceptable(response, "No " + INDEX_HTML);
            }
            po.clearWorkdir();
        } else {
            notAcceptable(response, "No converter specified");
        }
    }
}
