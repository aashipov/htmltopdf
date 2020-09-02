package org.dummy;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import static io.undertow.util.StatusCodes.INTERNAL_SERVER_ERROR;
import static org.dummy.OsUtils.DEFAULT_CHARSET_NAME;

/**
 * Router {@link HttpHandler}.
 */
public class Router implements HttpHandler {

    private static final String TEXT_PLAIN = "text/plain; charset=" + DEFAULT_CHARSET_NAME;
    private static final String HTML = "html";
    private static final String CHROMIUM = "chromium";
    private static final String STATUS_UP = "{\"status\":\"UP\"}";

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if ((exchange.getRequestURL().contains(HTML) || exchange.getRequestURL().contains(CHROMIUM))
                && exchange.getRequestMethod().equals(Methods.POST)) {
            // Parses HTTP POST form data and passes it to a handler asynchronously
            FormDataParser parser = null;
            try {
                parser = FormParserFactory.builder().build().createParser(exchange);
                HtmlToPdfHandler handler = new HtmlToPdfHandler();
                parser.parse(handler);
            } finally {
                if (null != parser) {
                    parser.close();
                }
            }
        } else {
            health(exchange);
        }
    }

    private static void plainTextUtf8Response(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, TEXT_PLAIN);
    }

    private static void health(HttpServerExchange exchange) {
        plainTextUtf8Response(exchange);
        exchange.getResponseSender().send(STATUS_UP);
    }

    /**
     * Respond {@link io.undertow.util.StatusCodes#INTERNAL_SERVER_ERROR}.
     * @param exchange {@link HttpServerExchange}
     * @param reason   reason
     */
    public static void internalServerError(HttpServerExchange exchange, String reason) {
        plainTextUtf8Response(exchange);
        exchange.setStatusCode(INTERNAL_SERVER_ERROR);
        exchange.getResponseSender().send(reason);
    }
}
