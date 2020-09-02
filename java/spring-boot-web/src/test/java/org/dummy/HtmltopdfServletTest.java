package org.dummy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link HtmltopdfServlet} {@link Test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class HtmltopdfServletTest extends AppBaseTest {

    @BeforeAll
    static void setUp() {
        HtmlToPdfUtils.restartChromiumHeadless();
    }

}
