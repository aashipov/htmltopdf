package org.dummy.upload;

import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.ext.web.FileUpload;

/**
 * {@link FileUpload}.
 */
public class MFileUploadImpl implements FileUpload {

    private final String uploadedFileName;
    private final HttpServerFileUpload upload;
    private final byte[] data;

    public MFileUploadImpl(String ufn, HttpServerFileUpload u, byte[] d) {
        this.uploadedFileName = ufn;
        this.upload = u;
        this.data = d;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String name() {
        return this.upload.name();
    }

    @Override
    public String uploadedFileName() {
        return this.uploadedFileName;
    }

    @Override
    public String fileName() {
        return this.upload.filename();
    }

    @Override
    public long size() {
        return this.upload.size();
    }

    @Override
    public String contentType() {
        return this.upload.contentType();
    }

    @Override
    public String contentTransferEncoding() {
        return this.upload.contentTransferEncoding();
    }

    @Override
    public String charSet() {
        return this.upload.charset();
    }

    @Override
    public boolean cancel() {
        return false;
    }
}
