package com.project8.jobvault.parsing;

public interface ResumeParser {
    ParseResult parse(byte[] pdfBytes);
}
