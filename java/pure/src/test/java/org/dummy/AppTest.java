package org.dummy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class AppTest extends AppBaseTest {
    static HttpServer HTTP_SERVER = null;

    AppTest() {
        super();
    }

    @BeforeAll
    static void setUp() throws InterruptedException, IOException {
        HtmlToPdfUtils.restartChromiumHeadless();
        TimeUnit.SECONDS.sleep(1L);
        HTTP_SERVER = App.launch();
    }

    @AfterAll
    static void tearDown() {
        if (HTTP_SERVER != null) {
            HTTP_SERVER.stop(0);
        }
    }
}
