package org.dummy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;
import static org.dummy.OsUtils.*;

/**
 * HTML {@link HttpHandler}.
 * Simplified https://gist.github.com/JensWalter/0f19780d131d903879a2.
 */
public class CommonHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger(CommonHandler.class.getSimpleName());
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String TEXT_PLAIN = "text/plain; charset=" + DEFAULT_CHARSET_NAME;
    private static final String STATUS_UP = "{\"status\":\"UP\"}";
    private static final String BOUNDARY = "boundary=";
    private static final String FILES = "\"" + "files" + "\"";
    private static final String DELIMITER_CARRIAGE_RETURN_AND_NEW_LINE = "\r" + DELIMITER_LF;
    private static final byte[] DOUBLE_DELIMITER =
            (DELIMITER_CARRIAGE_RETURN_AND_NEW_LINE + DELIMITER_CARRIAGE_RETURN_AND_NEW_LINE).getBytes(DEFAULT_CHARSET);
    private static final String FILENAME_EQUALS = "filename=";
    private static final String FILENAME_LOOKUP = "; " + FILENAME_EQUALS;

    private static final String FILENAME_HEADER_LOOKUP = DELIMITER_CARRIAGE_RETURN_AND_NEW_LINE + CONTENT_DISPOSITION + ": form-data; name=" + FILES + FILENAME_LOOKUP;
    private static final String RFC_7578_PREPEND = "\r\n--";
    private static final int OK = 200;
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final String CHROMIUM = "chromium";
    private static final String HTML = "html";
    private static final String MULTIPART = "multipart/form-data";
    private static final char DOUBLE_QUOTATION_MARK = '"';
    private static final char SINGLE_QUOTATION_MARK = '\'';

    @SuppressWarnings("java:S3776")
    @Override
    public void handle(HttpExchange httpExchange) {
        String url = httpExchange.getRequestURI().toString();
        if (url.contains(CHROMIUM) || url.contains(HTML)) {
            try {
                Headers headers = httpExchange.getRequestHeaders();
                String contentType = headers.getFirst(CONTENT_TYPE);
                if (!isBlank(contentType) && contentType.startsWith(MULTIPART)) {
                    //found form data
                    String boundary = contentType.substring(contentType.indexOf(BOUNDARY) + BOUNDARY.length());
                    byte[] boundaryBytes = (boundary).getBytes(DEFAULT_CHARSET);
                    MultipartMessage mm = new MultipartMessage();
                    mm.payload = requestBody(httpExchange);
                    List<Integer> offsets = indexesOf(mm.payload, boundaryBytes);
                    for (int idx = 0; idx < offsets.size(); idx++) {
                        int startPart = offsets.get(idx);
                        int endPart;
                        if (idx < offsets.size() - 1) {
                            endPart = offsets.get(idx + 1) - RFC_7578_PREPEND.length();
                        } else {
                            endPart = mm.payload.length;
                        }
                        //look for header
                        List<Integer> headerEnds = indexesOf(mm.payload, DOUBLE_DELIMITER, startPart, endPart);
                        int headerEnd = !headerEnds.isEmpty() ? headerEnds.get(0) : -1;
                        if (headerEnd > 0) {
                            String header = new String(mm.payload, startPart, (headerEnd - startPart), DEFAULT_CHARSET);
                            // extract name from header
                            int nameIndex = header.indexOf(FILENAME_HEADER_LOOKUP);
                            if (nameIndex >= 0) {
                                //check for extra filename field
                                int fileNameStart = header.indexOf(FILENAME_LOOKUP);
                                if (fileNameStart >= 0) {
                                    String filename =
                                            header.substring(
                                                    fileNameStart + FILENAME_LOOKUP.length(),
                                                    header.indexOf(DELIMITER_CARRIAGE_RETURN_AND_NEW_LINE, fileNameStart)
                                            );
                                    mm.filenames.add(clearFilename(filename));
                                    mm.fileStartIdxs.add(headerEnd + RFC_7578_PREPEND.length());
                                    mm.fileEndIdxs.add(endPart);
                                }
                            }
                        }
                    }
                    saveOnDiskAndConvertToPdf(httpExchange, mm);
                } else {
                    textResponse(httpExchange, INTERNAL_SERVER_ERROR, "No " + MULTIPART);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, null, e);
                textResponse(httpExchange, INTERNAL_SERVER_ERROR, "Error decoding multipart " + e.getMessage());
            }
        } else {
            textResponse(httpExchange, OK, STATUS_UP);
        }
    }

    private void saveOnDiskAndConvertToPdf(HttpExchange httpExchange, MultipartMessage mm) {
        String url = httpExchange.getRequestURI().toString();
        HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions(url);
        Path file;
        if (mm.filenames.size() != mm.fileStartIdxs.size()
                || mm.fileStartIdxs.size() != mm.fileEndIdxs.size()
                || mm.fileEndIdxs.size() != mm.filenames.size()) {
            textResponse(httpExchange, INTERNAL_SERVER_ERROR, "list size differ");
        }
        for (int i = 0; i < mm.filenames.size(); i++) {
            file = po.getWorkdir().resolve(mm.filenames.get(i));
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                outputStream.write(mm.payload, mm.fileStartIdxs.get(i), mm.fileEndIdxs.get(i) - mm.fileStartIdxs.get(i));
            } catch (IOException e) {
                textResponse(httpExchange, INTERNAL_SERVER_ERROR, "Error saving multipart");
            }
        }
        if (po.isIndexHtml()) {
            po.htmlToPdf();
            if (po.isPdf()) {
                try (OutputStream outputStream = httpExchange.getResponseBody()) {
                    httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_PDF);
                    httpExchange.getResponseHeaders().add(CONTENT_DISPOSITION, PDF_ATTACHED);
                    httpExchange.sendResponseHeaders(OK, po.getPdf().length);
                    outputStream.write(po.getPdf());
                    outputStream.flush();
                    httpExchange.getRequestBody().close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Error sending " + RESULT_PDF, e);
                    textResponse(httpExchange, INTERNAL_SERVER_ERROR, "Error sending " + RESULT_PDF);
                }
            } else {
                textResponse(httpExchange, INTERNAL_SERVER_ERROR, "No " + RESULT_PDF);
            }
        } else {
            textResponse(httpExchange, INTERNAL_SERVER_ERROR, "No " + INDEX_HTML);
        }
        po.clearWorkdir();
    }

    private static byte[] requestBody(HttpExchange httpExchange) {
        try (InputStream requestStream = httpExchange.getRequestBody()) {
            return requestStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Integer> indexesOf(byte[] haystack, byte[] needle) {
        return indexesOf(haystack, needle, 0, haystack.length - needle.length + 1);
    }

    private static List<Integer> indexesOf(byte[] haystack, byte[] needle, int haystackStart, int haystackEnd) {
        List<Integer> result = new ArrayList<>(0);
        for (int i = haystackStart; i < haystackEnd; i++) {
            boolean found = true;
            for (int j = 0; j < needle.length; j++) {
                if (i + j >= haystack.length || haystack[i + j] != needle[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                result.add(i);
            }
        }
        return result;
    }

    private static class MultipartMessage {
        private byte[] payload;
        private final List<String> filenames = new ArrayList<>(0);
        private final List<Integer> fileStartIdxs = new ArrayList<>(0);
        private final List<Integer> fileEndIdxs = new ArrayList<>(0);
    }

    private static String clearFilename(String filename) {
        int firstLiteral = 0;
        int lastLiteral = filename.length() - 1;
        for (int i = 0; i < filename.length(); i++) {
            if (DOUBLE_QUOTATION_MARK != filename.charAt(i) && SINGLE_QUOTATION_MARK != filename.charAt(i)) {
                firstLiteral = i;
                break;
            }
        }
        for (int i = filename.length() - 1; i >= 0; i--) {
            if (DOUBLE_QUOTATION_MARK != filename.charAt(i) && SINGLE_QUOTATION_MARK != filename.charAt(i)) {
                lastLiteral = i;
                break;
            }
        }
        return filename.substring(firstLiteral, lastLiteral + 1);
    }

    private static void textResponse(HttpExchange exchange, int rCode, String msg) {
        try (OutputStream os = exchange.getResponseBody()) {
            exchange.getResponseHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
            exchange.sendResponseHeaders(rCode, msg.length());
            os.write(msg.getBytes(DEFAULT_CHARSET));
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error sending plain text response", e);
        } finally {
            exchange.close();
        }
    }
}
