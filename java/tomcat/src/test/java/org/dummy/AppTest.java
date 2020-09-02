package org.dummy;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.TimeUnit;

class AppTest extends AppBaseTest {
    static Tomcat TOMCAT = null;

    AppTest() {
        super();
    }

    @BeforeAll
    static void setUp() throws InterruptedException, LifecycleException {
        HtmlToPdfUtils.restartChromiumHeadless();
        TimeUnit.SECONDS.sleep(1L);
        TOMCAT = App.tomcat();
        TOMCAT.start();
    }

    @AfterAll
    static void tearDown() throws LifecycleException {
        if (TOMCAT != null) {
            TOMCAT.stop();
        }
    }
}
