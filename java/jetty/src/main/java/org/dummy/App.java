package org.dummy;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;

/**
 * Main class.
 */
public class App {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        Server server = new Server();

        // Setup HTTP Connector
        HttpConfiguration httpConf = new HttpConfiguration();

        // Establish the HTTP ServerConnector
        ServerConnector httpConnector = new ServerConnector(server,
                new HttpConnectionFactory(httpConf));
        httpConnector.setPort(PORT);
        httpConnector.setHost(HOST);
        server.addConnector(httpConnector);

        // Add a Handlers for requests
        HandlerList handlers = new HandlerList();
        handlers.addHandler(new CommonHandler());
        server.setHandler(handlers);

        server.start();
        server.join();
    }

}