package org.dummy;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.dummy.EmptinessUtils.isBlank;

/**
 * Operating system utils.
 * Simpler than zt-exec or Apache Commons Exec, {@link Process} cross-platform mismatch bypass
 */
public final class OsUtils {

    private static final Logger LOG = Logger.getLogger(OsUtils.class.getSimpleName());

    private static final ExecutorService OS_CMD_EXECUTOR_SERVICE =
            Executors.newWorkStealingPool(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8);

    private static final String ESCAPED_DOUBLE_QUOTATION_MARK = "\"";
    private static final String ESCAPED_SINGLE_QUOTATION_MARK = "\'";
    public static final String DELIMITER_SPACE = " ";
    public static final String DELIMITER_LF = "\n";
    public static final String EMPTY_STRING = "";
    private static final String DELIMITER_PERIOD = ".";

    private static final int BUFFER_SIZE = 8192;

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final String DEFAULT_CHARSET_NAME = DEFAULT_CHARSET.name();
    private static final String WINDOWS_1251_CHARSET_NAME = "windows-1251";

    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String OS_VERSION_PROPERTY = "os.version";
    private static final String WIN = "win";
    private static final String LINUX = "linux";
    private static final String JAVA_IO_TMPDIR_PROPERTY = "java.io.tmpdir";
    private static final BigDecimal MIN_WMIC_WINDOWS_VERSION = new BigDecimal("6.0");
    private static final int FIRST_PROCESS_ID_INDEX = 2;

    /**
     * Process/command exit codes.
     */
    private static final int EXIT_CODE_RUNNING = -1;
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_ERROR = 1;

    private static final String TIMEOUT_MSG = "Timeout";
    private static final long PROCESS_INNER_STATE_CHANGE_TIMEOUT = 10L;

    private static final String CMD_LINE_TOKENIZER_DELIMITERS =
            ESCAPED_DOUBLE_QUOTATION_MARK + ESCAPED_SINGLE_QUOTATION_MARK + DELIMITER_SPACE;

    private static final boolean IS_WINDOWS = isWindowsInternal();
    private static final boolean IS_LINUX = isLinuxInternal();

    /**
     * Constructor
     */
    private OsUtils() {
        //
    }

    /**
     * Join collection of string.
     * @param c collection of string
     * @param d delimiter
     * @return join
     */
    private static String collectionOfStringsToString(Collection<String> c, String d) {
        if (null != c && !c.isEmpty()) {
            StringJoiner joiner = new StringJoiner(d);
            for (String item : c) {
                if (!isBlank(item)) {
                    joiner.add(item);
                }
            }
            return joiner.toString();
        } else {
            return EMPTY_STRING;
        }
    }

    /**
     * InputStream to List of String.
     * @param source InputStream
     * @param charset charset
     * @param inout List of String
     */
    public static void inputStreamToListOfStrings(InputStream source, Charset charset, List<String> inout) {
        String line;
        try (InputStreamReader inputStreamReader = new InputStreamReader(source, charset);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((line = bufferedReader.readLine()) != null) {
                inout.add(line);
            }
        } catch (IOException e) {
            // no interest in
        }
    }

    /**
     * Java Process API wrapper.
     */
    public static class OsCommandWrapper {

        private final String cmd;
        private int pid = EXIT_CODE_RUNNING;
        private int exitCode = EXIT_CODE_RUNNING;
        private final List<String> error = new ArrayList<>(0);
        private final List<String> output = new ArrayList<>(0);
        private final Timestamp start = Timestamp.from(Instant.now());
        private Integer maxExecuteTime = null;
        private Path workdir = null;
        private boolean translateCmd = false;

        /**
         * Constructor.
         * @param cmd command text
         */
        public OsCommandWrapper(String cmd) {
            this.cmd = cmd;
        }

        public String getCmd() {
            return cmd;
        }

        public int getPid() {
            return pid;
        }

        public int getExitCode() {
            return exitCode;
        }

        public List<String> getError() {
            return error;
        }

        public List<String> getOutput() {
            return output;
        }

        public Timestamp getStart() {
            return start;
        }

        public Integer getMaxExecuteTime() {
            return maxExecuteTime;
        }

        public OsCommandWrapper setExitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public OsCommandWrapper setPid(int pid) {
            this.pid = pid;
            return this;
        }

        public OsCommandWrapper setMaxExecuteTime(Integer maxExecuteTime) {
            this.maxExecuteTime = maxExecuteTime;
            return this;
        }

        public Path getWorkdir() {
            return workdir;
        }

        public OsCommandWrapper setWorkdir(Path workdir) {
            this.workdir = workdir;
            return this;
        }

        public boolean isTranslateCmd() {
            return translateCmd;
        }

        public OsCommandWrapper setTranslateCmd(boolean translateCmd) {
            this.translateCmd = translateCmd;
            return this;
        }

        /**
         * If a wrapper has PID?.
         * @return has it?
         */
        public boolean hasPid() {
            return this.pid != EXIT_CODE_RUNNING;
        }

        /**
         * Is command running longer than maxExecuteTime?.
         * @return is it?
         */
        public boolean isOverdue() {
            return null != this.maxExecuteTime
                    && ((System.currentTimeMillis() - this.getStart().toInstant().toEpochMilli()) > this.maxExecuteTime);
        }

        /**
         * Is exitCode == 0?.
         * @return is?
         */
        public boolean isOK() {
            return this.exitCode == EXIT_CODE_SUCCESS;
        }

        /**
         * Err due to timeout.
         */
        public void timeout() {
            this.setExitCode(EXIT_CODE_ERROR).getError().add(TIMEOUT_MSG);
        }

        /**
         * Set EXIT_CODE_SUCCESS if it's EXIT_CODE_RUNNING.
         */
        public void successOutOfRunning() {
            if (EXIT_CODE_RUNNING == this.getExitCode()) {
                this.setExitCode(EXIT_CODE_SUCCESS);
            }
        }

        public String getErrorString() {
            return collectionOfStringsToString(this.error, DELIMITER_LF);
        }

        public String getOutputString() {
            return collectionOfStringsToString(this.output, DELIMITER_LF);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OsCommandWrapper.class.getSimpleName() + "[", "]")
                    .add("cmd='" + cmd + "'")
                    .add("pid=" + pid)
                    .add("exitCode=" + exitCode)
                    .add("error=" + error)
                    .add("output=" + output)
                    .add("start=" + start)
                    .add("maxExecuteTime=" + maxExecuteTime)
                    .add("workdir=" + workdir)
                    .toString();
        }
    }

    /**
     * {@link Callable} {@link OsCommandWrapper}.
     */
    private static class OsCommandCallable implements Callable<Void> {

        private final OsCommandWrapper result;

        /**
         * Constructor by wrapper.
         * @param wrapper wrapper
         */
        public OsCommandCallable(OsCommandWrapper wrapper) {
            this.result = wrapper;
        }

        @Override
        public Void call() throws Exception {
            if (null != this.result) {
                execute(this.result);
            }
            return null;
        }
    }

    /**
     * Is Operating System Microsoft Windows (R).
     * @return is MS Windows?
     */
    private static boolean isWindowsInternal() {
        return (System.getProperty(OS_NAME_PROPERTY).toLowerCase(Locale.ENGLISH).indexOf(WIN) >= 0);
    }

    /**
     * Is Operating System Microsoft Windows (R).
     * @return is MS Windows?
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Is Operating System a Linux Distro.
     * @return is Linux?
     */
    private static boolean isLinuxInternal() {
        return (System.getProperty(OS_NAME_PROPERTY).toLowerCase(Locale.ENGLISH).indexOf(LINUX) >= 0);
    }

    /**
     * Is Operating System a Linux Distro.
     * @return is Linux?
     */
    public static boolean isLinux() {
        return IS_LINUX;
    }

    /**
     * Get Operating System version.
     * @return operating system version.
     */
    private static String getOSVersion() {
        return System.getProperty(OS_VERSION_PROPERTY);
    }

    /**
     * Is MS Windows a WMIC one (version equal 6.0 or newer).
     * @return Is MS Windows version equal 6.0 or newer?
     */
    private static boolean isWmicWindows() {
        return isWindows() && (new BigDecimal(getOSVersion()).compareTo(MIN_WMIC_WINDOWS_VERSION) >= 0);
    }

    /**
     * Assume OS console codepage name.
     * @return codepage name
     */
    public static Charset getConsoleCodepage() {
        if (isWindows()) {
            return Charset.forName(WINDOWS_1251_CHARSET_NAME);
        } else {
            return DEFAULT_CHARSET;
        }
    }

    /**
     * Get random UUID as string.
     * @return random UUID
     */
    public static String getRandomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get {@link Path} to OS directory for temporary files (e.g., /tmp in most of *nix).
     * @return {@link Path}
     */
    public static Path getTempDirectory() {
        return Paths.get(System.getProperty(JAVA_IO_TMPDIR_PROPERTY));
    }

    /**
     * Get {@link Path} to a temporary file/directory in OS directory for temporary files.
     * @return {@link Path}
     * Won't create file/directory
     */
    public static Path getTempInTempDirectory() {
        return getTempInTempDirectory(EMPTY_STRING);
    }

    /**
     * Get {@link Path} to a temporary file/directory in OS directory for temporary files.
     * @param extension file extension. e.g. "zip"
     * @return {@link Path}
     * Won't create file/directory
     */
    public static Path getTempInTempDirectory(String extension) {
        if (!isBlank(extension)) {
            return getTempDirectory().resolve(getRandomUUID() + DELIMITER_PERIOD + extension);
        }
        return getTempDirectory().resolve(getRandomUUID());
    }

    /**
     * Crack a command line.
     * @param toProcess the command line to process
     * @return the command line broken into strings. An empty or null toProcess
     * parameter results in a zero sized array
     * Copyright see org.apache.commons.exec.CommandLine
     * Removes single quote and double quote characters
     */
    @SuppressWarnings("java:S3776")
    public static String[] translateCommandline(final String toProcess) {
        if (null == toProcess || 0 == toProcess.length()) {
            // no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine
        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, CMD_LINE_TOKENIZER_DELIMITERS, true);
        final ArrayList<String> list = new ArrayList<>(0);
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;
        while (tok.hasMoreTokens()) {
            final String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if (ESCAPED_SINGLE_QUOTATION_MARK.equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if (ESCAPED_DOUBLE_QUOTATION_MARK.equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if (ESCAPED_SINGLE_QUOTATION_MARK.equals(nextTok)) {
                        state = inQuote;
                    } else if (ESCAPED_DOUBLE_QUOTATION_MARK.equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (DELIMITER_SPACE.equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || 0 != current.length()) {
                            list.add(current.toString());
                            current = new StringBuilder();
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if (lastTokenHasBeenQuoted || 0 != current.length()) {
            list.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("Unbalanced quotes in " + toProcess);
        }
        final String[] args = new String[list.size()];
        return list.toArray(args);
    }

    private static void inputStreamsToWrapper(InputStream inputStream, InputStream errorStream, OsCommandWrapper wrapper) {
        try {
            if (inputStream.available() > 0) {
                inputStreamToListOfStrings(inputStream, getConsoleCodepage(), wrapper.getOutput());
            }
            if (errorStream.available() > 0) {
                inputStreamToListOfStrings(errorStream, getConsoleCodepage(), wrapper.getError());
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "InputStream to wrapper copy error", e);
        }
    }

    private static void processInputStreamsToWrapper(Process p, OsCommandWrapper wrapper) {
        inputStreamsToWrapper(p.getInputStream(), p.getErrorStream(), wrapper);
    }

    /**
     * Execute wrapped OS command in the same thread.
     * @param wrapper wrapper
     */
    @SuppressWarnings("java:S3776")
    public static void execute(OsCommandWrapper wrapper) {
        Process p = null;
        String[] callee = wrapper.isTranslateCmd() ? translateCommandline(wrapper.getCmd()) : wrapper.getCmd().split(DELIMITER_SPACE);
        try {
            if (null == wrapper.getWorkdir()) {
                p = Runtime.getRuntime().exec(callee);
            } else {
                p = Runtime.getRuntime().exec(callee, null, wrapper.getWorkdir().toFile());
            }
            if (p != null) {
                TimeUnit.MILLISECONDS.sleep(PROCESS_INNER_STATE_CHANGE_TIMEOUT);
                wrapper.setPid((int) p.pid());
                if (wrapper.hasPid()) {
                    TimeUnit.MILLISECONDS.sleep(PROCESS_INNER_STATE_CHANGE_TIMEOUT);
                    if (p.isAlive()) {
                        while (p.isAlive()) {
                            TimeUnit.MILLISECONDS.sleep(PROCESS_INNER_STATE_CHANGE_TIMEOUT);
                            processInputStreamsToWrapper(p, wrapper);
                        }
                    }
                    processInputStreamsToWrapper(p, wrapper);
                    wrapper.setExitCode(p.exitValue());
                } else {
                    wrapper.getError().add("Error creating OS process");
                    wrapper.setExitCode(EXIT_CODE_ERROR);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error executing OS command {0} {1}", new Object[]{wrapper, e.getMessage()});
            wrapper.setExitCode(EXIT_CODE_ERROR);
            wrapper.getError().add(e.getLocalizedMessage());
        } catch (InterruptedException e) {
            LOG.log(Level.INFO, "OS command execution interrupted {0} {1}", new Object[]{wrapper, e.getMessage()});
            wrapper.getError().add(e.getLocalizedMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute OS command synchronously.
     * @param cmd command
     * @return {@link OsCommandWrapper}
     */
    public static OsCommandWrapper execute(String cmd) {
        OsCommandWrapper wrapper = new OsCommandWrapper(cmd);
        execute(wrapper);
        return wrapper;
    }

    private static void timeoutAndCleanUp(OsCommandWrapper wrapper, Future<Void> future) {
        wrapper.timeout();
        future.cancel(true);
        if (wrapper.hasPid()) {
            killProcessTree(String.valueOf(wrapper.getPid()));
        }
    }

    /**
     * Execute OS command in another thread.
     * @param wrapper {@link OsCommandWrapper}
     */
    public static void executeAsync(OsCommandWrapper wrapper) {
        Future<Void> future = OS_CMD_EXECUTOR_SERVICE.submit(new OsCommandCallable(wrapper));
        try {
            if (null != wrapper.getMaxExecuteTime()) {
                future.get(wrapper.getMaxExecuteTime(), TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            if (wrapper.isOverdue() || !future.isDone()) {
                timeoutAndCleanUp(wrapper, future);
            } else {
                wrapper.successOutOfRunning();
            }
        } catch (ExecutionException | TimeoutException e) {
            timeoutAndCleanUp(wrapper, future);
        } catch (InterruptedException e) {
            timeoutAndCleanUp(wrapper, future);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute OS command asynchronously.
     * @param cmd command
     * @return {@link OsCommandWrapper}
     */
    public static OsCommandWrapper executeAsync(String cmd) {
        OsCommandWrapper wrapper = new OsCommandWrapper(cmd);
        executeAsync(wrapper);
        return wrapper;
    }

    /**
     * Execute OS command in another thread.
     * @param cmd            command
     * @param maxExecuteTime timeout
     * @return {@link OsCommandWrapper}
     */
    public static OsCommandWrapper executeAsync(String cmd, Integer maxExecuteTime) {
        OsCommandWrapper wrapper = new OsCommandWrapper(cmd);
        if (null != maxExecuteTime) {
            wrapper.setMaxExecuteTime(maxExecuteTime);
        }
        executeAsync(wrapper);
        return wrapper;
    }

    /**
     * Get PID list for a given parent PID.
     * @param parentPid PPID
     * @return PID list
     */
    private static List<String> getChildrenPidByParentPid(String parentPid) {
        String command = EMPTY_STRING;
        List<String> result = new ArrayList<>(0);
        List<String> temp;
        if (isWmicWindows()) {
            command = "wmic process where (parentprocessid = " + parentPid + ") get processid";
        } else if (isLinux()) {
            command = "pgrep -P " + parentPid;
        }
        temp = execute(command).getOutput();
        if (!temp.isEmpty()) {
            if (isLinux()) {
                result = new ArrayList<>(temp);
            }
            if (isWmicWindows()) {
                String s;
                for (int i = FIRST_PROCESS_ID_INDEX; i < temp.size(); i++) {
                    s = temp.get(i).trim();
                    if (!isBlank(s)) {
                        result.add(s);
                    }
                }
            }
        }
        for (String s : new ArrayList<>(result)) {
            List<String> level = getChildrenPidByParentPid(s);
            result.addAll(level);
        }
        return result;
    }

    /**
     * Kill process tree.
     * @param rootPid PID of the root process
     */
    private static void killProcessTree(String rootPid) {
        StringJoiner joiner = new StringJoiner(DELIMITER_SPACE);
        List<String> pids = getChildrenPidByParentPid(rootPid);
        pids.add(rootPid);
        if (isWindows()) {
            joiner.add("taskkill /f /t");
            for (String s : pids) {
                joiner.add("/pid");
                joiner.add(s);
            }
        } else {
            joiner.add("kill -9");
            joiner.add(collectionOfStringsToString(pids, DELIMITER_SPACE));
        }
        execute(joiner.toString());
    }

    /**
     * Create directory.
     * @param dir {@link Path}
     * @return {@link Path}
     */
    public static Path createDirectory(Path dir) {
        if (!dir.toFile().exists()) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException(String.format("Cannot create directory %s",dir));
            }
        }
        return dir;
    }

    /**
     * Create file.
     * @param file {@link Path}
     * @return {@link Path}
     */
    public static Path createFile(Path file) {
        if (!file.toFile().exists()) {
            try {
                if (!file.getParent().toFile().exists()) {
                    createDirectory(file.getParent());
                }
                Files.createFile(file);
            } catch (Exception e) {
                throw new IllegalStateException(String.format("Cannot create file %s", file));
            }
        }
        return file;
    }

    /**
     * Recursively delete files and directories.
     * @param path {@link Path}
     */
    @SuppressWarnings({"java:S4042", "java:S899"})
    public static void deleteFilesAndDirectories(Path path) {
        if (path.toFile().exists() && path.toFile().canWrite()) {
            if (path.toFile().isDirectory()) {
                for (File subDir : path.toFile().listFiles()) {
                    deleteFilesAndDirectories(subDir.toPath());
                }
            }
            path.toFile().delete();
        }
    }

    /**
     * Copy {@link InputStream} to {@link OutputStream}.
     * @param input  {@link InputStream}
     * @param output {@link OutputStream}
     * @throws IOException read or write
     */
    public static void copy(InputStream input, OutputStream output) throws IOException {
        int bytesRead;
        final byte[] buffer = new byte[BUFFER_SIZE];
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Is Process running?.
     * @param pidStr Process ID string
     * @return is running?
     */
    public static boolean isProcessAlive(String pidStr) {
        String command = "";
        if (isWmicWindows()) {
            command = "wmic process where (processid = " + pidStr + ") get processid";
        } else if (isLinux()) {
            command = "ps -p " + pidStr + " --format pid";
        }
        return isProcessRunning(pidStr, command);
    }

    private static boolean isProcessRunning(String pid, String command) {
        OsCommandWrapper wrapper = new OsCommandWrapper(command);
        execute(wrapper);
        if (wrapper.isOK()) {
            String expected = pid;
            String actual = wrapper.getOutputString();
            return !isBlank(actual) && actual.contains(expected);
        }
        return false;
    }

    /**
     * Получить список PID экземпляров процесса по его имени.
     * @param processName имя процесса
     * @return список PID экземпляров процесса
     */
    public static List<String> getProcessIdByProcessName(String processName) {
        List<String> result = new ArrayList<>();
        if (isLinux()) {
            result = execute("pgrep -f " + processName).getOutput();
        }
        if (isWmicWindows()) {
            List<String> raw = execute("wmic process where \"name like \'%" + processName + "%\'\" get processid").getOutput();
            if (raw.size() > 2) {
                for (int i = FIRST_PROCESS_ID_INDEX; i < raw.size(); i++) {
                    if (!isBlank(raw.get(i).trim())) {
                        result.add(raw.get(i).trim());
                    }
                }
            }
        }
        return result;
    }
}
