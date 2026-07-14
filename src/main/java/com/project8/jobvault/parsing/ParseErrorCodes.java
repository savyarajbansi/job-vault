package com.project8.jobvault.parsing;

public final class ParseErrorCodes {
    public static final String PARSE_FAILED = "ERR_PARSE_001";
    public static final String EMPTY_TEXT = "ERR_PARSE_002";
    public static final String PARSE_QUEUE_FULL = "ERR_PARSE_003";

    public static final String MESSAGE_PARSE_FAILED = "Resume parsing failed.";
    public static final String MESSAGE_EMPTY_TEXT = "Resume text could not be extracted.";
    public static final String MESSAGE_PARSE_QUEUE_FULL = "Resume parsing is busy. Please try again shortly.";

    private ParseErrorCodes() {
    }
}
