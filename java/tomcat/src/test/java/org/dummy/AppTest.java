package org.dummy;

import java.util.concurrent.TimeUnit;

import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class AppTest extends AppBaseTest {
    static Tomcat HTTP_SERVER = null;

    AppTest() {
        super();
    }

    @BeforeAll
    static void setUp() throws Exception {
        HtmlToPdfUtils.restartChromiumHeadless();
        TimeUnit.SECONDS.sleep(1L);
        HTTP_SERVER = App.launch();
        HTTP_SERVER.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (HTTP_SERVER != null) {
            HTTP_SERVER.stop();
        }
    }
}
