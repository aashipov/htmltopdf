package org.dummy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.ruiyun.jvppeteer.core.Puppeteer;
import com.ruiyun.jvppeteer.core.browser.Browser;
import com.ruiyun.jvppeteer.core.page.Page;
import com.ruiyun.jvppeteer.options.LaunchOptions;
import com.ruiyun.jvppeteer.options.LaunchOptionsBuilder;
import com.ruiyun.jvppeteer.options.PDFOptions;
import com.ruiyun.jvppeteer.options.PageNavigateOptions;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.dummy.EmptinessUtils.isBlank;
import static org.dummy.OsUtils.*;

/**
 * HTML to PDF via wkhtmltopdf(.exe).
 */
public final class HtmlToPdfUtils {

    private static final Logger LOG = Logger.getLogger(HtmlToPdfUtils.class.getSimpleName());
    private static final int MAX_EXECUTE_TIME = 600_000;
    private static final String CHROMIUM_OPTIONS = "--headless --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 --no-sandbox --no-zygote --disable-setuid-sandbox --disable-notifications --disable-geolocation --disable-infobars --disable-session-crashed-bubble --disable-dev-shm-usage --disable-gpu --disable-translate --disable-extensions --disable-features=site-per-process --disable-hang-monitor --disable-popup-blocking --disable-prompt-on-repost --disable-background-networking --disable-breakpad --disable-client-side-phishing-detection --disable-sync --disable-default-apps --hide-scrollbars --metrics-recording-only --mute-audio --no-first-run --enable-automation --password-store=basic --use-mock-keychain --unlimited-storage --safebrowsing-disable-auto-update --font-render-hinting=none --disable-sync-preferences --user-data-dir=" + Paths.get(".").resolve(".config").resolve("headless_shell");
    private static final String WKHTMLTOPDF_EXECUTABLE = "wkhtmltopdf";
    private static final String CHROMIUM_EXECUTABLE = System.getProperty("chromium.executable", "/usr/bin/chromium");
    private static final Browser browser = launchChromium();
    private static final PageNavigateOptions pageReady = buildPageNavigateOptions();
    public static final String INDEX_HTML = "index.html";
    public static final String RESULT_PDF = "result.pdf";

    /**
     * Build {@link PageNavigateOptions} for rendering to finish.
     * @return {@link PageNavigateOptions}
     */
    private static PageNavigateOptions buildPageNavigateOptions() {
        PageNavigateOptions no = new PageNavigateOptions();
        no.setWaitUntil(Stream.of("load", "domcontentloaded", "networkidle0", "networkidle2").collect(toUnmodifiableList()));
        return no;
    }

    /**
     * Build headless Chromium {@link LaunchOptions}.
     * @return {@link LaunchOptions}
     */
    private static LaunchOptions buildChromiumLaunchOptions() {
        List<String> args = Arrays.asList(CHROMIUM_OPTIONS.split(DELIMITER_SPACE));
        return (new LaunchOptionsBuilder())
                .withIgnoreDefaultArgs(true)
                .withArgs(args)
                .withExecutablePath(CHROMIUM_EXECUTABLE)
                .build();
    }

    /**
     * Start Chromium headless.
     * @return {@link Browser}
     */
    private static Browser launchChromium() {
        try {
            return Puppeteer.launch(buildChromiumLaunchOptions());
        } catch (IOException e) {
            throw new IllegalStateException("Can not launch Chromium", e);
        }
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

        public static final Path TMP_DIR = Paths.get(".").resolve(TMP);
        private static final byte[] HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER =
                "Something went wrong with HTML to PDF converter".getBytes(DEFAULT_CHARSET);

        private PaperSize paperSize = PaperSize.A4;
        private boolean landscape = false;
        private String left = DEFAULT_MARGIN;
        private String right = DEFAULT_MARGIN;
        private String top  = DEFAULT_MARGIN;
        private String bottom = DEFAULT_MARGIN;
        private final Path workdir = TMP_DIR.resolve(getRandomUUID());
        private Boolean chromium = Boolean.FALSE;
        private OsCommandWrapper wrapper;
        private byte[] pdf = HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER;

        /**
         * Constructor.
         */
        public PrinterOptions() {
            //
        }

        /**
         * HTML to PDF.
         */
        public void htmlToPdf() {
            if (Boolean.TRUE.equals(this.getChromium())) {
                try {
                    Page page = browser.newPage();
                    page.setDefaultTimeout(MAX_EXECUTE_TIME);
                    page.setDefaultNavigationTimeout(MAX_EXECUTE_TIME);
                    page.goTo(FILE_URI_PREFIX + this.getWorkdir().resolve(INDEX_HTML).toAbsolutePath(), pageReady);
                    this.pdf = page.pdf(buildChromiumPDFOptions());
                    page.close();
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Chromium error", e);
                } catch (InterruptedException e) {
                    LOG.log(Level.SEVERE, "Chromium was interrupted", e);
                    Thread.currentThread().interrupt();
                }
            } else {
                this.buildOsCommandWrapper();
                executeAsync(this.getWrapper());
                if (!this.getWrapper().isOK()) {
                    LOG.info(this.getWrapper().getOutputString() + DELIMITER_LF + this.getWrapper().getErrorString());
                }
                try {
                    this.pdf = Files.readAllBytes(this.getWorkdir().resolve(RESULT_PDF));
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Can not read " + RESULT_PDF, e);
                }
            }
        }

        public PaperSize getPaperSize() {
            return paperSize;
        }

        public PrinterOptions setPaperSize(PaperSize paperSize) {
            this.paperSize = paperSize;
            return this;
        }

        public boolean isLandscape() {
            return landscape;
        }

        public PrinterOptions setLandscape(boolean landscape) {
            this.landscape = landscape;
            return this;
        }

        public String getLeft() {
            return left;
        }

        public PrinterOptions setLeft(String left) {
            this.left = left;
            return this;
        }

        public String getRight() {
            return right;
        }

        public PrinterOptions setRight(String right) {
            this.right = right;
            return this;
        }

        public String getTop() {
            return top;
        }

        public PrinterOptions setTop(String top) {
            this.top = top;
            return this;
        }

        public String getBottom() {
            return bottom;
        }

        public PrinterOptions setBottom(String bottom) {
            this.bottom = bottom;
            return this;
        }

        public Path getWorkdir() {
            return workdir;
        }

        public Boolean getChromium() {
            return chromium;
        }

        public PrinterOptions setChromium(Boolean chromium) {
            this.chromium = chromium;
            return this;
        }

        public OsCommandWrapper getWrapper() {
            return wrapper;
        }

        private PrinterOptions buildOsCommandWrapper() {
            this.wrapper = new OsUtils.OsCommandWrapper(this.buildWkhtmltopdfCmd());
            this.wrapper.setWorkdir(this.workdir).setMaxExecuteTime(MAX_EXECUTE_TIME);
            return this;
        }

        /**
         * Get PDF file content.
         * @return bytes
         */
        public byte[] getPdf() {
            return this.pdf;
        }

        /**
         * Was PDF created?
         * @return was?
         */
        public boolean isPdf() {
            return this.pdf != HTML_TO_PDF_CONVERTER_FAILED_PLACEHOLDER;
        }

        /**
         * Does string matches regex
         * @param regex regex
         * @param string string
         * @return matches?
         */
        private static boolean matches(String regex, String string) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return pattern.matcher(string).matches();
        }

        /**
         * Extract groups matching regex.
         * @param regex regex
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
         * @param url request URL
         */
        @SuppressWarnings("java:S3776")
        public void printoutSettings(String url) {
            if (!isBlank(url)) {
                if (matches(A_3_PAPER_SIZE_NAME, url)) {
                    this.setPaperSize(PaperSize.A3);
                }
                if (matches(LANDSCAPE_REGEX, url)) {
                    this.setLandscape(true);
                }
                if (matches(CHROMIUM_REGEX, url)) {
                    this.setChromium(Boolean.TRUE);
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
                                    this.setLeft(marginDigits);
                                }
                                if (RIGHT_MARGIN_NAME.equals(marginName)) {
                                    this.setRight(marginDigits);
                                }
                                if (TOP_MARGIN_NAME.equals(marginName)) {
                                    this.setTop(marginDigits);
                                }
                                if (BOTTOM_MARGIN_NAME.equals(marginName)) {
                                    this.setBottom(marginDigits);
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

        private String buildWkhtmltopdfCmd() {
            StringJoiner sj = new StringJoiner(DELIMITER_SPACE);
            sj.add(WKHTMLTOPDF_EXECUTABLE);
            sj.add("--enable-local-file-access");
            sj.add("--print-media-type");
            sj.add("--no-stop-slow-scripts");
            sj.add("--disable-smart-shrinking");

            if (!isBlank(this.getLeft())) {
                sj.add("--margin-left");
                sj.add(this.getLeft());
            }
            if (!isBlank(this.getRight())) {
                sj.add("--margin-right");
                sj.add(this.getRight());
            }
            if (!isBlank(this.getTop())) {
                sj.add("--margin-top");
                sj.add(this.getTop());
            }
            if (!isBlank(this.getBottom())) {
                sj.add("--margin-bottom");
                sj.add(this.getBottom());
            }

            if (null != this.getPaperSize()) {
                if (!isBlank(this.getPaperSize().getWidth())) {
                    sj.add("--page-width");
                    sj.add(this.getPaperSize().getWidth());
                }
                if (!isBlank(this.getPaperSize().getHeight())) {
                    sj.add("--page-height");
                    sj.add(this.getPaperSize().getHeight());
                }
            }

            if (this.isLandscape()) {
                sj.add("--orientation");
                sj.add("landscape");
            }
            sj.add(INDEX_HTML);
            sj.add(RESULT_PDF);
            return sj.toString();
        }

        private PDFOptions buildChromiumPDFOptions() {
            PDFOptions opts = new PDFOptions();
            opts.setLandscape(this.isLandscape());
            opts.setWidth(this.getPaperSize().getWidth() + MILLIMETER_ACRONYM);
            opts.setHeight(this.getPaperSize().getHeight() + MILLIMETER_ACRONYM);
            opts.getMargin().setTop(this.getTop() + MILLIMETER_ACRONYM);
            opts.getMargin().setRight(this.getRight() + MILLIMETER_ACRONYM);
            opts.getMargin().setBottom(this.getBottom() + MILLIMETER_ACRONYM);
            opts.getMargin().setLeft(this.getLeft() + MILLIMETER_ACRONYM);
            return opts;
        }
    }

    /**
     * Office paper size.
     */
    private enum PaperSize {
        A4("210", "297"),
        A3("297", "420")
        ;

        private final String width;
        private final String height;

        /**
         * Constructor.
         * @param width width
         * @param height height
         */
        PaperSize(String width, String height) {
            this.width = width;
            this.height = height;
        }

        public String getWidth() {
            return width;
        }

        public String getHeight() {
            return height;
        }
    }
}
