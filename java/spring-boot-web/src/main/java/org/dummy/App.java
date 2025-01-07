package org.dummy;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.deleteFilesAndDirectories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * Entrypoint.
 */
@ServletComponentScan
@SpringBootApplication
@CrossOrigin(value = "*")
public class App {

    public static void main(String[] args) {
        deleteFilesAndDirectories(TMP_DIR);
        HtmlToPdfUtils.restartChromiumHeadless();
        SpringApplication.run(App.class, args);
    }

    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                super.postProcessContext(context);
                context.setAllowCasualMultipartParsing(true);
            }

            @Override
            protected void customizeConnector(Connector connector) {
                super.customizeConnector(connector);
                connector.setMaxPostSize(1024 * 1024 * 10);
            }
        };
        return factory;
    }
}
