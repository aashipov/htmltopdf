package org.dummy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dummy.OsUtils.DEFAULT_CHARSET;
import static org.dummy.OsUtils.DELIMITER_LF;
import static org.dummy.OsUtils.DELIMITER_SPACE;
import org.dummy.OsUtils.OsCommandWrapper;
import static org.dummy.OsUtils.OsCommandWrapper.executeAsync;
import static org.dummy.OsUtils.OsCommandWrapper.executeAsynchronously;
import static org.dummy.OsUtils.createDirectory;
import static org.dummy.OsUtils.deleteFilesAndDirectories;
import static org.dummy.OsUtils.getProcessIdByProcessName;
import static org.dummy.OsUtils.getRandomUUID;
import static org.dummy.OsUtils.isBlank;
import static org.dummy.OsUtils.isWindows;
import static org.dummy.OsUtils.killProcessTree;

import com.ruiyun.jvppeteer.api.core.Browser;
import com.ruiyun.jvppeteer.api.core.Page;
import com.ruiyun.jvppeteer.cdp.core.Puppeteer;
import com.ruiyun.jvppeteer.cdp.entities.ConnectOptions;
import com.ruiyun.jvppeteer.cdp.entities.GoToOptions;
import com.ruiyun.jvppeteer.cdp.entities.PDFOptions;
import com.ruiyun.jvppeteer.common.PuppeteerLifeCycle;

/**
 * HTML to PDF via chromium or wkhtmltopdf.
 */
public final class HtmlToPdfUtils {

    private static final Logger LOG = Logger.getLogger(HtmlToPdfUtils.class.getSimpleName());
    private static final String CHROMIUM_OPTIONS = "--headless --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 --no-sandbox --no-zygote --disable-setuid-sandbox --disable-notifications --disable-geolocation --disable-infobars --disable-session-crashed-bubble --disable-dev-shm-usage --disable-gpu --disable-translate --disable-extensions --disable-features=site-per-process --disable-hang-monitor --disable-popup-blocking --disable-prompt-on-repost --disable-background-networking --disable-breakpad --disable-client-side-phishing-detection --disable-sync --disable-default-apps --hide-scrollbars --metrics-recording-only --mute-audio --no-first-run --enable-automation --password-store=basic --use-mock-keychain --unlimited-storage --safebrowsing-disable-auto-update --font-render-hinting=none --disable-sync-preferences --user-data-dir=" + Paths.get(".").resolve(".config").resolve("headless_shell");
    private static final String CHROMIUM_EXECUTABLE = isWindows() ? "chrome.exe" : "chromium";
    private static OsCommandWrapper chromiumHeadlessWrapper = null;
    private static Future<Void> chromiumHeadlessFuture = null;
    private static Browser puppeteerBrowser = null;
    public static final String INDEX_HTML = "index.html";
    public static final String RESULT_PDF = "result.pdf";

    public static void restartChromiumHeadless() {
        shutdownChromiumHeadless();
        chromiumHeadlessWrapper = new OsCommandWrapper(CHROMIUM_EXECUTABLE + DELIMITER_SPACE + CHROMIUM_OPTIONS);
        chromiumHeadlessFuture = executeAsynchronously(chromiumHeadlessWrapper);
        Runtime.getRuntime().addShutdownHook(new Thread(HtmlToPdfUtils::shutdownChromiumHeadless));
    }

    private static void shutdownChromiumHeadless() {
        if (chromiumHeadlessFuture != null) {
            chromiumHeadlessFuture.cancel(true);
        }
        if (chromiumHeadlessWrapper != null && chromiumHeadlessWrapper.hasPid()) {
            killProcessTree("" + chromiumHeadlessWrapper.getPid());
        } else {
            Collection<String> chromiumPids = getProcessIdByProcessName(CHROMIUM_EXECUTABLE);
            for (String chromiumPid : chromiumPids) {
                killProcessTree(chromiumPid);
            }
        }
    }

    /**
     * Build {@link Puppeteer} {@link GoToOptions} for rendering to finish.
     *
     * @return {@link GoToOptions}
     */
    private static GoToOptions buildPuppeteerGoToOptions() {
        GoToOptions goToOptions = new GoToOptions();
        goToOptions.setWaitUntil(List.of(PuppeteerLifeCycle.load, PuppeteerLifeCycle.domcontentloaded, PuppeteerLifeCycle.networkIdle, PuppeteerLifeCycle.networkIdle2));
        return goToOptions;
    }

    /**
     * Constructor.
     */
    private HtmlToPdfUtils() {
        //
    }

    /**
     * Printer options.
     */
    public static class PrinterOptions {

        private static final String TMP = "tmp";
        private static final String DEFAULT_MARGIN = "20";
        private static final String MANY_SYMBOLS = ".*";
        private static final String A_3_PAPER_SIZE_NAME = MANY_SYMBOLS + "a3" + MANY_SYMBOLS;
        private static final String LANDSCAPE_REGEX = MANY_SYMBOLS + "landscape" + MANY_SYMBOLS;
        private static final String CHROMIUM_REGEX = MANY_SYMBOLS + "chromium" + MANY_SYMBOLS;
        private static final String LEFT_PARENTHESIS = "(";
        private static final String RIGHT_PARENTHESIS = ")";
        private static final String ONE_OR_MORE_DIGITS_REGEX = "\\d+";
        private static final String ONE_OR_MORE_DIGITS_GROUP = LEFT_PARENTHESIS + ONE_OR_MORE_DIGITS_REGEX + RIGHT_PARENTHESIS;
        private static final String LEFT_MARGIN_NAME = "left";
        private static final String RIGHT_MARGIN_NAME = "right";
        private static final String TOP_MARGIN_NAME = "top";
        private static final String BOTTOM_MARGIN_NAME = "bottom";
        private static final String MILLIMETER_ACRONYM = "mm";
        private static final String FILE_URI_PREFIX = "file://";
        private static final Map<String, String> MARGIN_NAME_TO_REGEX = fillMarginNameRegexMap();
        static final Path TMP_DIR = Paths.get(".").resolve(TMP);
        private static final byte[] HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER =
                "Something went wrong with HTML to PDF converter".getBytes(DEFAULT_CHARSET);
        private static final int MAX_EXECUTE_TIME = 600_000;
        private static final double MM_IN_INCH = 25.4;
        private static final GoToOptions PUPPETEER_PAGE_READY = buildPuppeteerGoToOptions();

        private PaperSize paperSize = PaperSize.A4;
        private boolean landscape = false;
        private String left = DEFAULT_MARGIN;
        private String right = DEFAULT_MARGIN;
        private String top = DEFAULT_MARGIN;
        private String bottom = DEFAULT_MARGIN;
        private final Path workdir = TMP_DIR.resolve(getRandomUUID());
        private Boolean chromium = Boolean.FALSE;
        private OsCommandWrapper wrapper;
        private byte[] pdf = HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER;

        /**
         * Constructor.
         *
         * @param url url with converter name and printout settings
         */
        public PrinterOptions(String url) {
            this.printoutSettings(url);
            createDirectory(this.workdir);
        }

        public Path getWorkdir() {
            return workdir;
        }

        public void clearWorkdir() {
            deleteFilesAndDirectories(this.workdir);
        }

        /**
         * Is there an index.html file in workdir?.
         *
         * @return is there?
         */
        public boolean isIndexHtml() {
            Path indexHtml = this.getWorkdir().resolve(INDEX_HTML);
            return indexHtml.toFile().exists() && indexHtml.toFile().canRead();
        }

        /**
         * Get PDF file content.
         *
         * @return bytes
         */
        public byte[] getPdf() {
            return this.pdf;
        }

        /**
         * Was PDF created?
         *
         * @return was?
         */
        public boolean isPdf() {
            return this.pdf != HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER;
        }

        /**
         * HTML to PDF.
         */
        @SuppressWarnings("java:S2142")
        public void htmlToPdf() {
            if (Boolean.TRUE.equals(this.chromium)) {
                viaChromium();
            } else {
                viaWkhtmltoPdf();
            }
        }

        /**
         * Does string matches regex
         *
         * @param regex  regex
         * @param string string
         * @return matches?
         */
        private static boolean matches(String regex, String string) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return pattern.matcher(string).matches();
        }

        /**
         * Extract groups matching regex.
         *
         * @param regex  regex
         * @param string string
         * @return {@link List} {@link String} of groups matched
         */
        private static List<String> groups(String regex, String string) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(string);
            List<String> result = new ArrayList<>(0);
            while (matcher.find()) {
                result.add(matcher.group());
            }
            return result;
        }

        /**
         * Extract paper size and margins from URL.
         *
         * @param url request URL
         */
        @SuppressWarnings("java:S3776")
        private void printoutSettings(String url) {
            if (!isBlank(url)) {
                if (matches(A_3_PAPER_SIZE_NAME, url)) {
                    this.paperSize = PaperSize.A3;
                }
                if (matches(LANDSCAPE_REGEX, url)) {
                    this.landscape = true;
                }
                if (matches(CHROMIUM_REGEX, url)) {
                    this.chromium = Boolean.TRUE;
                }
                String marginNameWithDigits;
                String marginDigits;
                String marginName;
                String marginRegex;
                List<String> found;
                for (Map.Entry<String, String> entry : MARGIN_NAME_TO_REGEX.entrySet()) {
                    marginName = entry.getKey();
                    marginRegex = entry.getValue();
                    found = groups(marginRegex, url);
                    if (!found.isEmpty()) {
                        marginNameWithDigits = found.get(0);
                        found = groups(ONE_OR_MORE_DIGITS_GROUP, marginNameWithDigits);
                        if (!found.isEmpty()) {
                            marginDigits = found.get(0);
                            if (!isBlank(marginDigits)) {
                                if (LEFT_MARGIN_NAME.equals(marginName)) {
                                    this.left = marginDigits;
                                }
                                if (RIGHT_MARGIN_NAME.equals(marginName)) {
                                    this.right = marginDigits;
                                }
                                if (TOP_MARGIN_NAME.equals(marginName)) {
                                    this.top = marginDigits;
                                }
                                if (BOTTOM_MARGIN_NAME.equals(marginName)) {
                                    this.bottom = marginDigits;
                                }
                            }
                        }
                    }
                }
            }
        }

        private static Map<String, String> fillMarginNameRegexMap() {
            Map<String, String> map = new HashMap<>();
            map.put(LEFT_MARGIN_NAME, LEFT_PARENTHESIS + LEFT_MARGIN_NAME + RIGHT_PARENTHESIS + ONE_OR_MORE_DIGITS_REGEX);
            map.put(RIGHT_MARGIN_NAME, LEFT_PARENTHESIS + RIGHT_MARGIN_NAME + RIGHT_PARENTHESIS + ONE_OR_MORE_DIGITS_GROUP);
            map.put(TOP_MARGIN_NAME, LEFT_PARENTHESIS + TOP_MARGIN_NAME + RIGHT_PARENTHESIS + ONE_OR_MORE_DIGITS_GROUP);
            map.put(BOTTOM_MARGIN_NAME, LEFT_PARENTHESIS + BOTTOM_MARGIN_NAME + RIGHT_PARENTHESIS + ONE_OR_MORE_DIGITS_GROUP);
            return map;
        }
        
        private static ConnectOptions connectOptions() {
            ConnectOptions o = new ConnectOptions();
            o.setBrowserURL("http://0.0.0.0:9222");
            return o;
        }

        private static Page getPuppeteerNewPage() {
            if (puppeteerBrowser == null) {
                try {
                    puppeteerBrowser = Puppeteer.connect(connectOptions());
                } catch (Exception ex) {
                    Logger.getLogger(HtmlToPdfUtils.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return puppeteerBrowser.newPage();
        }

        private PDFOptions buildPuppeteerChromiumPDFOptions() {
            PDFOptions opts = new PDFOptions();
            opts.setLandscape(this.landscape);
            opts.setWidth(this.paperSize.width + MILLIMETER_ACRONYM);
            opts.setHeight(this.paperSize.height + MILLIMETER_ACRONYM);
            opts.getMargin().setTop(this.top + MILLIMETER_ACRONYM);
            opts.getMargin().setRight(this.right + MILLIMETER_ACRONYM);
            opts.getMargin().setBottom(this.bottom + MILLIMETER_ACRONYM);
            opts.getMargin().setLeft(this.left + MILLIMETER_ACRONYM);
            return opts;
        }

        private String buildWkhtmltopdfCmd() {
            StringJoiner sj = new StringJoiner(DELIMITER_SPACE);
            sj.add("wkhtmltopdf --enable-local-file-access --print-media-type --no-stop-slow-scripts --disable-smart-shrinking --margin-left");
            sj.add(this.left);
            sj.add("--margin-right");
            sj.add(this.right);
            sj.add("--margin-top");
            sj.add(this.top);
            sj.add("--margin-bottom");
            sj.add(this.bottom);
            sj.add("--page-width");
            sj.add(this.paperSize.width);
            sj.add("--page-height");
            sj.add(this.paperSize.height);
            if (this.landscape) {
                sj.add("--orientation");
                sj.add("landscape");
            }
            sj.add(INDEX_HTML);
            sj.add(RESULT_PDF);
            return sj.toString();
        }

        private PrinterOptions buildWkhtmltopdfWrapper() {
            this.wrapper = new OsUtils.OsCommandWrapper(this.buildWkhtmltopdfCmd());
            this.wrapper.setWorkdir(this.workdir).setMaxExecuteTime(MAX_EXECUTE_TIME);
            return this;
        }

        private void viaChromium() {
            try {
                Page page = getPuppeteerNewPage();
                page.setDefaultTimeout(MAX_EXECUTE_TIME);
                page.setDefaultNavigationTimeout(MAX_EXECUTE_TIME);
                page.goTo(FILE_URI_PREFIX + this.getWorkdir().resolve(INDEX_HTML).toAbsolutePath(), PUPPETEER_PAGE_READY);
                this.pdf = page.pdf(buildPuppeteerChromiumPDFOptions());
                page.close();
            } catch (IOException | InterruptedException | ExecutionException e) {
                LOG.log(Level.SEVERE, "Chromium error", e);
            }
        }

        private void viaWkhtmltoPdf() {
            this.buildWkhtmltopdfWrapper();
            executeAsync(this.wrapper);
            if (!this.wrapper.isOK()) {
                LOG.info(this.wrapper.getOutputString() + DELIMITER_LF + this.wrapper.getErrorString());
            }
            try {
                this.pdf = Files.readAllBytes(this.getWorkdir().resolve(RESULT_PDF));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Can not read " + RESULT_PDF, e);
            }
        }
    }

    /**
     * Office paper size.
     */
    private enum PaperSize {
        A4("210", "297"),
        A3("297", "420");

        private final String width;
        private final String height;

        /**
         * Constructor.
         *
         * @param width  width
         * @param height height
         */
        PaperSize(String width, String height) {
            this.width = width;
            this.height = height;
        }
    }
}
