package org.dummy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.dummy.OsUtils.*;
import static org.dummy.OsUtils.OsCommandWrapper.execute;
import static org.dummy.OsUtils.OsCommandWrapper.executeAsync;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OsUtils} tests.
 */
class OsUtilsTest {

    private static final String JAVA_VERSION_CMD = "java -version";
    private static final String JAVA_VERSION_EXPECTED_OUT_MSG = "Runtime Environment";
    private static final Long TIMEOUT_MS = 2_000L;

    private static String get30SecondsSleepCmdText() {
        return isWindows() ? "ping -n 30 127.0.0.1" : "sleep 30000";
    }

    @Test
    void executeTest() {
        OsCommandWrapper result = execute(JAVA_VERSION_CMD);
        assertTrue(result.isOK(), "java -version runs smoothly");
        assertTrue(result.getErrorString().contains(JAVA_VERSION_EXPECTED_OUT_MSG), "java -version prints to stderr");
        assertTrue(result.getOutput().isEmpty(), "Log is empty");
    }

    @Test
    void executeAsyncTest() {
        OsCommandWrapper wrapper = executeAsync(JAVA_VERSION_CMD);
        assertTrue(wrapper.isOK(), "java -version runs smoothly");
        assertTrue(wrapper.getErrorString().contains(JAVA_VERSION_EXPECTED_OUT_MSG), "java -version prints to stderr");
        assertTrue(wrapper.getOutput().isEmpty(), "Log is empty");
    }

    @Test
    void executeAsyncTimeoutTest() {
        String cmd = get30SecondsSleepCmdText();
        long start = System.currentTimeMillis();
        OsCommandWrapper wrapper = executeAsync(cmd, TIMEOUT_MS.intValue());
        assertTrue((System.currentTimeMillis() - start) < 2 * TIMEOUT_MS, "timely");
        assertFalse(wrapper.isOK(), "Error");
        assertTrue(wrapper.hasPid(), "Process had PID");
        assertFalse(isProcessAlive(String.valueOf(wrapper.getPid())), "stopped");
        assertTrue(wrapper.getErrorString().contains("Timeout"), "Timed out");
        if (isWindows()) {
            assertTrue(wrapper.getOutputString().contains("Pinging"), "Pinging");
        } else {
            assertTrue(wrapper.getOutput().isEmpty(), "Empty output");
        }
    }

    @Test
    void executeInWorkDirTest() {
        if (isWindows()) {
            OsCommandWrapper wrapper = new OsCommandWrapper("ls");
            Path workdir = Paths.get(System.getenv("HOMEDRIVE")).resolve(System.getenv("HOMEPATH"));
            wrapper.setWorkdir(workdir);
            execute(wrapper);
            assertTrue(wrapper.isOK(), "success");
            assertTrue(wrapper.getOutput().contains("NTUSER.DAT") || wrapper.getOutput().contains("ntuser.dat"), "NTUSER.DAT");
        }
        if (isLinux()) {
            OsCommandWrapper wrapper = new OsCommandWrapper("ls");
            Path workdir = Paths.get("/");
            wrapper.setWorkdir(workdir);
            execute(wrapper);
            assertTrue(wrapper.isOK(), "success");
            assertTrue(wrapper.getOutputString().contains("bin"), "bin found");
        }
    }

    /**
     * Use JDK 9+ {@link InputStream#transferTo(OutputStream)} to get a {@link String} out of {@link InputStream}.
     *
     * @param inputStream {@link InputStream}
     * @param charset     {@link Charset}
     * @return {@link String} в {@link Charset}
     * @throws IOException copy
     *                     Will not {@link InputStream#close()}
     */
    private static String inputStreamToStringJdk9Plus(InputStream inputStream, Charset charset) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(byteArrayOutputStream);
            byteArrayOutputStream.flush();
            return byteArrayOutputStream.toString(charset);
        }
    }

    @Test
    void whyOsUtils() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder().command(OsCommandWrapper.translateCommandline(get30SecondsSleepCmdText()));
        Process process = processBuilder.start();
        if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            assertTrue(process.pid() > 0L, "some pid");
            assertTrue(isProcessAlive(String.valueOf(process.pid())), "alive");
            assertTrue(process.isAlive());
            process.destroy();
        }
        while (process.isAlive()) {
            TimeUnit.MILLISECONDS.sleep(TIMEOUT_MS);
        }
        assertFalse(isProcessAlive(String.valueOf(process.pid())), "stopped");
        if (isWindows()) {
            assertEquals(1, process.exitValue(), "exit code 1");
            String err = inputStreamToStringJdk9Plus(process.getErrorStream(), getConsoleCodepage());
            String out = inputStreamToStringJdk9Plus(process.getInputStream(), getConsoleCodepage());
            assertTrue(isBlank(err), "no error");
            assertTrue(out.contains("Reply from 127.0.0.1: "));
        }
        if (isLinux()) {
            assertEquals(143, process.exitValue(), "SIGTERM");
            Assertions.assertThrows(
                    IOException.class,
                    () -> inputStreamToStringJdk9Plus(process.getInputStream(), getConsoleCodepage()),
                    "Input Stream closed");
            Assertions.assertThrows(
                    IOException.class,
                    () -> inputStreamToStringJdk9Plus(process.getErrorStream(), getConsoleCodepage()),
                    "Error Stream closed");
        }
    }

    @Test
    void whyTranslateCommandlineTest() {
        if (isLinux()) {
            String cmd = "bash -c \"echo 1+2+3+4+5 | bc -lq\"";
            String expected = "15";
            OsCommandWrapper wrapper;
            wrapper = new OsCommandWrapper(cmd);
            execute(wrapper);
            assertFalse(wrapper.isOK());
            assertTrue(wrapper.getErrorString().contains("unexpected EOF"));
            wrapper = new OsCommandWrapper(cmd);
            wrapper.setTranslateCmd(true);
            execute(wrapper);
            assertTrue(wrapper.isOK());
            assertEquals(expected, wrapper.getOutputString());
        }
    }
}
