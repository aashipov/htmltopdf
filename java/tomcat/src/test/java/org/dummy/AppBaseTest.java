package org.dummy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;
import static org.dummy.OsUtils.*;
import static org.dummy.OsUtils.OsCommandWrapper.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AppBaseTest {
    private static final Logger LOG = Logger.getLogger(AppBaseTest.class.getSimpleName());
    private static final char[] MULTIPART_CHARS =
            "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    .toCharArray();
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String MULTIPART = "multipart/form-data";
    private static final String FILENAME_EQUALS = "filename=";
    private static final String BOUNDARY = "boundary=";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final Random RANDOM_GENERATOR = new Random();

    protected static String generateBoundary() {
        // a random size from 30 to 40
        int count = RANDOM_GENERATOR.nextInt(11) + 30;
        char[] boundary = new char[count];
        for (int i = 0; i < count; i++) {
            boundary[i] = MULTIPART_CHARS[RANDOM_GENERATOR.nextInt(MULTIPART_CHARS.length)];
        }
        return new String(boundary);
    }

    protected static final int DEFAULT_HTTP_PORT = 8080;
    protected static final String BASE_URL = "http://0.0.0.0:" + DEFAULT_HTTP_PORT;
    protected static final String FLOWER_JPG = "flower.jpg";
    protected static final byte[] SAMPLE_HTML_FILE_CONTENT = readResource(INDEX_HTML);
    protected static final byte[] SAMPLE_JPG_FILE_CONTENT = readResource(FLOWER_JPG);

    protected static byte[] readResource(String resourceName) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            return inputStream.readAllBytes();
        } catch (IOException | NullPointerException e) {
            LOG.log(Level.SEVERE, "can not read resource " + resourceName, e);
        }
        return new byte[0];
    }

    protected static void doTestConvertWithCurl(String url) throws IOException {
        if (isLinux()) {
            Path dir = getTempInTempDirectory();
            createDirectory(dir);
            Files.write(dir.resolve(INDEX_HTML), SAMPLE_HTML_FILE_CONTENT);
            Files.write(dir.resolve(FLOWER_JPG), SAMPLE_JPG_FILE_CONTENT);
            StringJoiner sj = new StringJoiner(" ");
            sj.add("curl");
            sj.add("--request POST");
            sj.add("--header Content-Type:multipart/form-data");
            sj.add("--form files=@" + dir.resolve(INDEX_HTML).toAbsolutePath());
            sj.add("--form files=@" + dir.resolve(FLOWER_JPG).toAbsolutePath());
            sj.add("--url");
            sj.add(url);
            sj.add("-o");
            sj.add(dir.resolve(RESULT_PDF).toAbsolutePath().toString());
            OsCommandWrapper wrapper = execute(sj.toString());
            if (!wrapper.isOK()) {
                throw new IllegalStateException("Can not convert to pdf " + url + " " + dir);
            }
            assertTrue(Files.exists(dir.resolve(RESULT_PDF)));
            wrapper = execute("file " + dir.resolve(RESULT_PDF));
            assertTrue(wrapper.isOK());
            assertTrue(wrapper.getOutputString().contains("PDF document"));
            deleteFilesAndDirectories(dir);
        }
    }

    //https://urvanov.ru/2020/08/18/java-11-httpclient-multipart-form-data/
    protected static HttpRequest.BodyPublisher ofMultipartData(Map<Object, Object> data,
                                                               String boundary) throws IOException {
        List<byte[]> byteArrays = new ArrayList<>();
        byte[] separator = ("--" + boundary
                + "\r\n" + HtmlToPdfServlet.CONTENT_DISPOSITION + ": form-data; name=")
                .getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof List) {
                for (Path path : (List<Path>) entry.getValue()) {
                    byteArrays.add(separator);
                    String mimeType = Files.probeContentType(path);
                    byteArrays.add((
                            "\"files\"; "
                            + FILENAME_EQUALS + "\"" + path.getFileName() + "\"\r\n"
                            + CONTENT_TYPE + ": " + mimeType
                            + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    byteArrays.add(Files.readAllBytes(path));
                    byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                byteArrays.add(separator);
                byteArrays.add(
                        ("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue()
                                + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        byteArrays
                .add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    protected static void doTestConvertWithHttpClient(String url) throws IOException, InterruptedException, URISyntaxException {
        if (isLinux()) {
            Path dir = getTempInTempDirectory();
            createDirectory(dir);
            Files.write(dir.resolve(INDEX_HTML), SAMPLE_HTML_FILE_CONTENT);
            Files.write(dir.resolve(FLOWER_JPG), SAMPLE_JPG_FILE_CONTENT);
            Path html = dir.resolve(INDEX_HTML);
            Path jpg = dir.resolve(FLOWER_JPG);
            String boundary = generateBoundary();
            Map<Object, Object> data = Map.of("files", List.of(html, jpg));

            HttpRequest request;
            request = HttpRequest.newBuilder().uri(new URI(url))
                    .headers(CONTENT_TYPE, MULTIPART + ";" + BOUNDARY + boundary)
                    .POST(ofMultipartData(data, boundary))
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().length > 100);

            Path pdf = dir.resolve(RESULT_PDF);
            Files.write(pdf, response.body());
            assertTrue(Files.exists(dir.resolve(RESULT_PDF)));
            OsCommandWrapper wrapper = execute("file " + dir.resolve(RESULT_PDF));
            assertTrue(wrapper.isOK());
            assertTrue(wrapper.getOutputString().contains("PDF document"));
            deleteFilesAndDirectories(dir);
        }
    }

    @Test
    void upTest() {
        if (isLinux()) {
            OsCommandWrapper wrapper = execute("curl " + BASE_URL);
            assertTrue(wrapper.isOK());
            assertEquals(HtmlToPdfServlet.STATUS_UP, wrapper.getOutputString());
        }
    }

    @Test
    void jvppeteerTest() throws IOException, URISyntaxException, InterruptedException {
        doTestConvertWithCurl(BASE_URL + "/" + HtmlToPdfServlet.CHROMIUM);
        doTestConvertWithHttpClient(BASE_URL + "/" + HtmlToPdfServlet.CHROMIUM);
    }

    @Test
    void htmlTest() throws IOException, URISyntaxException, InterruptedException {
        doTestConvertWithCurl(BASE_URL + "/" + HtmlToPdfServlet.HTML);
        doTestConvertWithHttpClient(BASE_URL + "/" + HtmlToPdfServlet.HTML);
    }
}
