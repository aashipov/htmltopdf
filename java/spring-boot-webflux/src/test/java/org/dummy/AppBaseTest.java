package org.dummy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dummy.OsUtils.*;
import static org.dummy.OsUtils.OsCommandWrapper.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AppBaseTest {

    private static final Logger LOG = Logger.getLogger(AppBaseTest.class.getSimpleName());
    protected static final int DEFAULT_HTTP_PORT = 8080;
    protected static final String BASE_URL = "http://0.0.0.0:" + DEFAULT_HTTP_PORT;
    protected static final String CHROMIUM = "chromium";
    protected static final String HTML = "html";
    protected static final String STATUS_UP = "{\"status\":\"UP\"}";
    protected static final String INDEX_HTML = "index.html";
    protected static final String RESULT_PDF = "result.pdf";
    protected static final byte[] SAMPLE_FILE_CONTENT = readResource(INDEX_HTML);

    protected static byte[] readResource(String resourceName) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "can not read resource " + resourceName, e);
        }
        return new byte[0];
    }

    protected static void doTestConvert(String url) throws IOException {
        if (isLinux()) {
            Path dir = getTempInTempDirectory();
            createDirectory(dir);
            Files.write(dir.resolve(INDEX_HTML), SAMPLE_FILE_CONTENT);
            StringJoiner sj = new StringJoiner(" ");
            sj.add("curl");
            sj.add("--request POST");
            sj.add("--header Content-Type:multipart/form-data");
            sj.add("--form files=@" + dir.resolve(INDEX_HTML).toAbsolutePath());
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

    @Test
    void upTest() {
        if (isLinux()) {
            OsCommandWrapper wrapper = execute("curl " + BASE_URL);
            assertTrue(wrapper.isOK());
            assertEquals(STATUS_UP, wrapper.getOutputString());
        }
    }

    @Test
    void jvppeteerTest() throws IOException {
        System.setProperty("chromium.harness", "jvppeteer");
        doTestConvert(BASE_URL + "/" + CHROMIUM);
    }

    @Test
    void chromeDevtoolsKotlinTest() throws IOException {
        System.setProperty("chromium.harness", "chrome-devtools-kotlin");
        doTestConvert(BASE_URL + "/" + CHROMIUM);
    }

    @Test
    void htmlTest() throws IOException {
        doTestConvert(BASE_URL + "/" + HTML);
    }
}
