package org.dummy.upload;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Multipart Body Handler.
 */
public interface MBodyHandler extends Handler<RoutingContext> {
    long DEFAULT_BODY_LIMIT = -1;

    String DEFAULT_UPLOADS_DIRECTORY = "file-uploads";

    boolean DEFAULT_MERGE_FORM_ATTRIBUTES = true;

    boolean DEFAULT_DELETE_UPLOADED_FILES_ON_END = false;

    boolean DEFAULT_PREALLOCATE_BODY_BUFFER = false;

    static MBodyHandler create() {
        return new MBodyHandlerImpl();
    }

    static MBodyHandler create(boolean handleFileUploads) {
        return new MBodyHandlerImpl(handleFileUploads);
    }

    static MBodyHandler create(String uploadDirectory) {
        return new MBodyHandlerImpl(uploadDirectory);
    }

    MBodyHandler setHandleFileUploads(boolean handleFileUploads);

    MBodyHandler setBodyLimit(long bodyLimit);

    MBodyHandler setUploadsDirectory(String uploadsDirectory);

    MBodyHandler setMergeFormAttributes(boolean mergeFormAttributes);

    MBodyHandler setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd);

    MBodyHandler setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer);
}
