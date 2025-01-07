package org.dummy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;
import static org.dummy.OsUtils.ESCAPED_DOUBLE_QUOTATION_MARK;
import static org.dummy.OsUtils.isBlank;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@WebServlet(name = "HtmlToPdfServlet", urlPatterns = "/*", loadOnStartup = 1)
public class HtmlToPdfServlet extends HttpServlet {

    static final String STATUS_UP = "{\"status\":\"UP\"}";
    static final String CHROMIUM = "chromium";
    static final String HTML = "html";
    static final String FILENAME = "filename";
    static final String DELIMITER_SEMICOLON = ";";
    static final String DELIMITER_EQUALS_SIGN = "=";
    static final String APPLICATION_PDF = "application/pdf";
    static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";
    static final String CONTENT_DISPOSITION = "Content-Disposition";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().println(STATUS_UP);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURL().toString();
        if (url.contains(HTML) || url.contains(CHROMIUM)) {
            HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions(url);
            for (Part part : req.getParts()) {
                String filename = getFileName(part);
                if (!isBlank(filename)) {
                    Path file = po.getWorkdir().resolve(filename);
                    try (InputStream inputStream = part.getInputStream(); OutputStream outputStream = Files.newOutputStream(file)) {
                        inputStream.transferTo(outputStream);
                    }
                } else {
                    notAcceptable(resp, "No filename");
                    break;
                }
            }
            if (po.isIndexHtml()) {
                po.htmlToPdf();
                if (po.isPdf()) {
                    resp.setContentType(APPLICATION_PDF);
                    resp.addHeader(CONTENT_DISPOSITION, PDF_ATTACHED);
                    resp.setContentLength(po.getPdf().length);
                    try (OutputStream outputStream = resp.getOutputStream()) {
                        outputStream.write(po.getPdf());
                        outputStream.flush();
                    }
                } else {
                    notAcceptable(resp, "No " + RESULT_PDF);
                }
            } else {
                notAcceptable(resp, "No " + INDEX_HTML);
            }
            po.clearWorkdir();
        } else {
            notAcceptable(resp, "No converter specified");
        }
    }

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
}
