package org.dummy;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.deleteFilesAndDirectories;

/**
 * Main class.
 */
public class App {
    private static final int PORT = 8080;
    private static final ExecutorService HTTP_EXECUTOR_SERVICE =
            Executors.newWorkStealingPool(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8);

    static HttpServer launch() throws IOException {
        deleteFilesAndDirectories(TMP_DIR);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new CommonHandler());
        server.setExecutor(HTTP_EXECUTOR_SERVICE);
        server.start();
        return server;
    }

    public static void main(String[] args) throws IOException {
        HtmlToPdfUtils.restartChromiumHeadless();
        launch();
    }
}
