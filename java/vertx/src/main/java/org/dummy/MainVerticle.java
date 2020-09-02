package org.dummy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.dummy.upload.MBodyHandler;

/**
 * {@link AbstractVerticle} implementation.
 * https://github.com/agrajm/vertx-fileupload/blob/master/src/main/java/com/examples/vertx/SimpleFormUploadServer.java
 * https://stackoverflow.com/questions/36401510/handling-multipart-form-in-vertx
 */
public class MainVerticle extends AbstractVerticle {

    private static final int PORT = 8080;

    @Override
    public void start(Promise<Void> startPromise) {
        VertxOptions vertxOptions = new VertxOptions()
                .setMaxEventLoopExecuteTime(Long.MAX_VALUE)
                .setPreferNativeTransport(true);
        super.vertx = Vertx.vertx(vertxOptions);

        Router router = Router.router(super.vertx);
        router.route().handler(MBodyHandler.create().setHandleFileUploads(true));
        router.route().handler(new CommonHandler());

        final HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setTcpFastOpen(true)
                .setTcpNoDelay(true)
                .setTcpQuickAck(true)
                .setPort(PORT);
        HttpServer server = super.vertx.createHttpServer(httpServerOptions);
        server.requestHandler(router);
        server.listen();
    }
}
