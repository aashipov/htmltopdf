package org.dummy;

import io.undertow.Undertow;
import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.deleteFilesAndDirectories;

/**
 * Main class.
 */
public class App {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        deleteFilesAndDirectories(TMP_DIR);
        Undertow server = Undertow.builder().addHttpListener(PORT, HOST).setHandler(new Router()).build();
        server.start();
    }

}