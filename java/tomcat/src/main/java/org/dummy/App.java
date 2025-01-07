package org.dummy;

import java.nio.file.Paths;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.deleteFilesAndDirectories;

/**
 * Main class.
 */
public class App {
    private static final int PORT = 8080;

    /**
     * @see <a href="https://www.codejava.net/servers/tomcat/how-to-embed-tomcat-server-into-java-web-applications">CodeJava</a>
     */
    static Tomcat launch() {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir("tmp");
        tomcat.setPort(PORT);
        tomcat.getConnector().setMaxPostSize(1024 * 1024 * 10);

        String contextPath = "";
        Context context = tomcat.addContext(contextPath, Paths.get(".").toAbsolutePath().toString());
        context.setAllowCasualMultipartParsing(true);
        HtmlToPdfServlet servlet = new HtmlToPdfServlet();

        String servletName = "HtmlToPdfServlet";
        tomcat.addServlet(contextPath, servletName, servlet);
        context.addServletMappingDecoded("/", servletName);
        return tomcat;
    }

    public static void main(String[] args) throws LifecycleException {
        deleteFilesAndDirectories(TMP_DIR);
        HtmlToPdfUtils.restartChromiumHeadless();
        Tomcat tomcat = launch();
        tomcat.start();
        tomcat.getServer().await();
    }
}
