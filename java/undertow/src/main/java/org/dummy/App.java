package org.dummy;

import static org.dummy.HtmlToPdfUtils.PrinterOptions.TMP_DIR;
import static org.dummy.OsUtils.deleteFilesAndDirectories;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;

/**
 * Main class.
 */
public class App {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 8080;

    static Undertow launch() throws ServletException {
        DeploymentInfo servletBuilder;
        servletBuilder = Servlets.deployment()
                .setClassLoader(App.class.getClassLoader())
                .setContextPath("/")
                .setDeploymentName("htmltopdf.war")
                .addServlets(
                        Servlets.servlet("HtmlToPdfServlet", HtmlToPdfServlet.class)
                                .addMapping("/*"))
                                .setDefaultMultipartConfig(new MultipartConfigElement(""));

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect("/"))
                .addPrefixPath("/", manager.start());

        return Undertow.builder()
                .addHttpListener(PORT, HOST)
                .setHandler(path)
                .build();
    }

    public static void main(String[] args) throws ServletException {
        deleteFilesAndDirectories(TMP_DIR);
        HtmlToPdfUtils.restartChromiumHeadless();
        Undertow server = launch();
        server.start();
    }
}
