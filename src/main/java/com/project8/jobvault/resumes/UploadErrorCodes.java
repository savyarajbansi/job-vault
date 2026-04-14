package com.project8.jobvault.resumes;

public final class UploadErrorCodes {
    public static final String UNSUPPORTED_FILE = "ERR_UPLOAD_001";
    public static final String FILE_TOO_LARGE = "ERR_UPLOAD_002";
    public static final String UPLOAD_FAILED = "ERR_UPLOAD_003";

    public static final String MESSAGE_UNSUPPORTED_FILE = "Unsupported file type. Only PDF is allowed.";
    public static final String MESSAGE_FILE_TOO_LARGE = "File too large.";
    public static final String MESSAGE_UPLOAD_FAILED = "Upload failed.";

    private UploadErrorCodes() {
    }
}
