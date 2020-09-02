package org.dummy;

import org.junit.jupiter.api.BeforeAll;

class AppTest extends AppBaseTest {

    AppTest() {
        super();
    }

    @BeforeAll
    static void setUp() {
        HtmlToPdfUtils.restartChromiumHeadless();
        ApplicationKt.createApplicationEngine().start(false);
    }
}
