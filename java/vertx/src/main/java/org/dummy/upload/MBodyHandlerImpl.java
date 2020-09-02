package org.dummy.upload;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

/**
 * {@link MBodyHandler} impl.
 */
public class MBodyHandlerImpl implements MBodyHandler {
    private static final Logger LOG = Logger.getLogger(MBodyHandlerImpl.class.getSimpleName());
    private static final String BODY_HANDLED = "__body-handled";

    private long bodyLimit = DEFAULT_BODY_LIMIT;
    private boolean handleFileUploads;
    private String uploadsDir;
    private boolean mergeFormAttributes = DEFAULT_MERGE_FORM_ATTRIBUTES;
    private boolean deleteUploadedFilesOnEnd = DEFAULT_DELETE_UPLOADED_FILES_ON_END;
    private boolean isPreallocateBodyBuffer = DEFAULT_PREALLOCATE_BODY_BUFFER;
    private static final int DEFAULT_INITIAL_BODY_BUFFER_SIZE = 1024; //bytes

    public MBodyHandlerImpl() {
        this(true, DEFAULT_UPLOADS_DIRECTORY);
    }

    public MBodyHandlerImpl(boolean handleFileUploads) {
        this(handleFileUploads, DEFAULT_UPLOADS_DIRECTORY);
    }

    public MBodyHandlerImpl(String uploadDirectory) {
        this(true, uploadDirectory);
    }

    private MBodyHandlerImpl(boolean handleFileUploads, String uploadDirectory) {
        this.handleFileUploads = handleFileUploads;
        setUploadsDirectory(uploadDirectory);
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        if (request.headers().contains(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET, true)) {
            context.next();
            return;
        }
        // we need to keep state since we can be called again on reroute
        Boolean handled = context.get(BODY_HANDLED);
        if (handled == null || !handled) {
            long contentLength = isPreallocateBodyBuffer ? parseContentLengthHeader(request) : -1;
            BHandler handler =
                    new BHandler(
                            context,
                            contentLength,
                            this.bodyLimit,
                            this.handleFileUploads,
                            this.deleteUploadedFilesOnEnd,
                            this.mergeFormAttributes
                    );
            request.handler(handler);
            request.endHandler(v -> handler.end());
            context.put(BODY_HANDLED, true);
        } else {
            // on reroute we need to re-merge the form params if that was desired
            if (mergeFormAttributes && request.isExpectMultipart()) {
                request.params().addAll(request.formAttributes());
            }

            context.next();
        }
    }

    @Override
    public MBodyHandlerImpl setHandleFileUploads(boolean handleFileUploads) {
        this.handleFileUploads = handleFileUploads;
        return this;
    }

    @Override
    public MBodyHandlerImpl setBodyLimit(long bodyLimit) {
        this.bodyLimit = bodyLimit;
        return this;
    }

    @Override
    public MBodyHandlerImpl setUploadsDirectory(String uploadsDirectory) {
        this.uploadsDir = uploadsDirectory;
        return this;
    }

    @Override
    public MBodyHandlerImpl setMergeFormAttributes(boolean mergeFormAttributes) {
        this.mergeFormAttributes = mergeFormAttributes;
        return this;
    }

    @Override
    public MBodyHandlerImpl setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd) {
        this.deleteUploadedFilesOnEnd = deleteUploadedFilesOnEnd;
        return this;
    }

    @Override
    public MBodyHandlerImpl setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer) {
        this.isPreallocateBodyBuffer = isPreallocateBodyBuffer;
        return this;
    }

    private long parseContentLengthHeader(HttpServerRequest request) {
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        try {
            long parsedContentLength = Long.parseLong(contentLength);
            return parsedContentLength < 0 ? null : parsedContentLength;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static class EndHandler implements Handler<Void> {
        private final BHandler handler;

        private EndHandler(BHandler h) {
            this.handler = h;
        }

        @Override
        public void handle(Void event) {
            handler.end();
        }
    }

    private static class BHandler implements Handler<Buffer> {
        private static final int MAX_PREALLOCATED_BODY_BUFFER_BYTES = 10_485_760;

        RoutingContext context;
        Buffer body;
        boolean failed;
        AtomicInteger uploadCount = new AtomicInteger();
        AtomicBoolean cleanup = new AtomicBoolean(false);
        boolean ended;
        long uploadSize = 0L;
        final boolean isMultipart;
        final boolean isUrlEncoded;

        //
        private final long bodyLimit;
        private final boolean handleFileUploads;
        private final boolean deleteUploadedFilesOnEnd;
        private final boolean mergeFormAttributes;

        public BHandler(RoutingContext context, long contentLength, long bodyLimit, boolean handleFileUploads, boolean deleteUploadedFilesOnEnd, boolean mergeFormAttributes) {
            this.context = context;
            this.bodyLimit = bodyLimit;
            this.handleFileUploads = handleFileUploads;
            this.deleteUploadedFilesOnEnd = deleteUploadedFilesOnEnd;
            this.mergeFormAttributes = mergeFormAttributes;

            Set<FileUpload> fileUploads = context.fileUploads();

            final String contentType = context.request().getHeader(HttpHeaders.CONTENT_TYPE);
            if (contentType == null) {
                isMultipart = false;
                isUrlEncoded = false;
            } else {
                final String lowerCaseContentType = contentType.toLowerCase();
                isMultipart = lowerCaseContentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString());
                isUrlEncoded = lowerCaseContentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
            }

            initBodyBuffer(contentLength);

            if (isMultipart || isUrlEncoded) {
                context.request().setExpectMultipart(true);

                context.request().uploadHandler(upload -> {
                    if (this.bodyLimit != -1 && upload.isSizeAvailable()) {
                        // we can try to abort even before the upload starts
                        long size = uploadSize + upload.size();
                        if (size > this.bodyLimit) {
                            failed = true;
                            context.fail(413);
                            return;
                        }
                    }
                    if (this.handleFileUploads) {
                        // we actually upload to a file with a generated filename
                        uploadCount.incrementAndGet();

                        // NOTE: upload buffer stream 을 file upload directory 에 저장 하지 않음.
                        // upload stream 에서 직접 buffer data 를 얻어 MultipartFileUploadImpl 에 넣은후 RoutingContext 로 전달.
                        Buffer allBuffer = Buffer.buffer();
                        upload.handler(buffer -> {
                            allBuffer.appendBuffer(buffer);
                        });

                        upload.exceptionHandler(t -> {
                            //deleteFileUploads();
                            context.fail(t);
                        });
                        upload.endHandler(v -> {
                            MFileUploadImpl fileUpload = new MFileUploadImpl(null, upload, allBuffer.getBytes());
                            fileUploads.add(fileUpload);

                            int count = uploadCount.decrementAndGet();
                            // only if parsing is done and count is 0 then all files have been processed
                            if (ended && count == 0) {
                                doEnd();
                            }
                        });
                    }
                });
            }


            context.request().exceptionHandler(t -> {
                //deleteFileUploads();
                context.fail(t);
            });
        }


        private void initBodyBuffer(long contentLength) {
            int initialBodyBufferSize;
            if (contentLength < 0) {
                initialBodyBufferSize = DEFAULT_INITIAL_BODY_BUFFER_SIZE;
            } else if (contentLength > MAX_PREALLOCATED_BODY_BUFFER_BYTES) {
                initialBodyBufferSize = MAX_PREALLOCATED_BODY_BUFFER_BYTES;
            } else {
                initialBodyBufferSize = (int) contentLength;
            }
            if (bodyLimit != -1) {
                initialBodyBufferSize = (int) Math.min(initialBodyBufferSize, bodyLimit);
            }
            this.body = Buffer.buffer(initialBodyBufferSize);
        }

        @Override
        public void handle(Buffer buff) {
            if (failed) {
                return;
            }
            uploadSize += buff.length();
            if (bodyLimit != -1 && uploadSize > bodyLimit) {
                failed = true;
                context.fail(413);
                // enqueue a delete for the error uploads
                //context.vertx().runOnContext(v -> deleteFileUploads());
            } else {
                // multipart requests will not end up in the request body
                // url encoded should also not, however jQuery by default
                // post in urlencoded even if the payload is something else
                if (!isMultipart /* && !isUrlEncoded */) {
                    body.appendBuffer(buff);
                }
            }
        }

        void end() {
            // this marks the end of body parsing, calling doEnd should
            // only be possible from this moment onwards
            ended = true;

            // only if parsing is done and count is 0 then all files have been processed
            if (uploadCount.get() == 0) {
                doEnd();
            }
        }

        void doEnd() {

            if (failed) {
                //deleteFileUploads();
                return;
            }

            if (deleteUploadedFilesOnEnd) {
                //context.addBodyEndHandler(x -> deleteFileUploads());
            }

            HttpServerRequest req = context.request();
            if (mergeFormAttributes && req.isExpectMultipart()) {
                req.params().addAll(req.formAttributes());
            }
            context.setBody(body);
            context.next();
        }

        /**
         * No need in, since handler does not save temp files.
         */
        private void deleteFileUploads() {
            if (cleanup.compareAndSet(false, true) && handleFileUploads) {
                for (FileUpload fileUpload : context.fileUploads()) {
                    FileSystem fileSystem = context.vertx().fileSystem();
                    String uploadedFileName = fileUpload.uploadedFileName();
                    fileSystem.exists(uploadedFileName, existResult -> {
                        if (existResult.failed()) {
                            LOG.log(Level.WARNING, "Could not detect if uploaded file exists, not deleting: " + uploadedFileName, existResult.cause());
                        } else if (existResult.result()) {
                            fileSystem.delete(uploadedFileName, deleteResult -> {
                                if (deleteResult.failed()) {
                                    LOG.log(Level.WARNING, "Delete of uploaded file failed: " + uploadedFileName, deleteResult.cause());
                                }
                            });
                        }
                    });
                }
            }
        }
    }
}
