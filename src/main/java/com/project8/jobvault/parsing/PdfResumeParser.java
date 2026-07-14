package com.project8.jobvault.parsing;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PdfResumeParser implements ResumeParser {
    private final int maxPages;
    private final int maxTextBytes;
    private final Duration timeout;
    private final SkillCatalog skillCatalog;
    private final ExecutorService parserExecutor;

    @Autowired
    public PdfResumeParser(
            @Value("${jobvault.parsing.max-pages}") int maxPages,
            @Value("${jobvault.parsing.max-text-bytes}") int maxTextBytes,
            @Value("${jobvault.parsing.timeout-seconds}") long timeoutSeconds,
            SkillCatalog skillCatalog,
            @Value("${jobvault.parsing.max-queue-depth:20}") int maxQueueDepth) {
        this(maxPages, maxTextBytes, Duration.ofSeconds(timeoutSeconds), skillCatalog, maxQueueDepth);
    }

    public PdfResumeParser(int maxPages, int maxTextBytes, Duration timeout, SkillCatalog skillCatalog) {
        this(maxPages, maxTextBytes, timeout, skillCatalog, 20);
    }

    public PdfResumeParser(
            int maxPages, int maxTextBytes, Duration timeout, SkillCatalog skillCatalog, int maxQueueDepth) {
        this.maxPages = requirePositive(maxPages, "maxPages");
        this.maxTextBytes = requirePositive(maxTextBytes, "maxTextBytes");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxQueueDepth <= 0) {
            throw new IllegalArgumentException("maxQueueDepth must be positive");
        }
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog");
        this.parserExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueDepth),
                new ParserThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public ParseResult parse(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ParseErrorException(
                    ParseErrorCodes.PARSE_FAILED,
                    ParseErrorCodes.MESSAGE_PARSE_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("reason", "empty_input"));
        }
        String extractedText = extractTextWithTimeout(pdfBytes);
        String trimmed = normalizeWhitespace(extractedText);
        if (trimmed.isBlank()) {
            throw new ParseErrorException(
                    ParseErrorCodes.EMPTY_TEXT,
                    ParseErrorCodes.MESSAGE_EMPTY_TEXT,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("reason", "empty_text"));
        }
        List<String> skills = skillCatalog.extractSkills(trimmed);
        return new ParseResult(trimmed, skills);
    }

    private String extractTextWithTimeout(byte[] pdfBytes) {
        Future<String> future = null;
        try {
            future = parserExecutor.submit(() -> extractText(pdfBytes));
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            throw new ParseErrorException(
                    ParseErrorCodes.PARSE_QUEUE_FULL,
                    ParseErrorCodes.MESSAGE_PARSE_QUEUE_FULL,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    Map.of("reason", "queue_full"));
        } catch (TimeoutException ex) {
            if (future != null) {
                future.cancel(true);
            }
            throw new ParseErrorException(
                    ParseErrorCodes.PARSE_FAILED,
                    ParseErrorCodes.MESSAGE_PARSE_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("reason", "timeout"));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ParseErrorException parseError) {
                throw parseError;
            }
            String reason = "invalid_pdf";
            if (cause instanceof IOException) {
                reason = "io_error";
            } else if (cause instanceof IllegalArgumentException) {
                reason = "invalid_pdf";
            }
            throw new ParseErrorException(
                    ParseErrorCodes.PARSE_FAILED,
                    ParseErrorCodes.MESSAGE_PARSE_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("reason", reason));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ParseErrorException(
                    ParseErrorCodes.PARSE_FAILED,
                    ParseErrorCodes.MESSAGE_PARSE_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("reason", "interrupted"));
        }
    }

    @PreDestroy
    void shutdownParserExecutor() {
        parserExecutor.shutdownNow();
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(maxPages, pages));
            BoundedStringWriter writer = new BoundedStringWriter(maxTextBytes);
            stripper.writeText(document, writer);
            return writer.toString();
        }
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean lastSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean isSpace = Character.isWhitespace(c);
            if (isSpace) {
                if (!lastSpace) {
                    normalized.append(' ');
                }
                lastSpace = true;
            } else {
                normalized.append(c);
                lastSpace = false;
            }
        }
        return normalized.toString();
    }

    private static final class ParserThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("pdf-resume-parser");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class BoundedStringWriter extends Writer {
        private final StringBuilder builder = new StringBuilder();
        private final int maxBytes;
        private int currentBytes;

        private BoundedStringWriter(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len <= 0 || currentBytes >= maxBytes) {
                return;
            }
            String chunk = new String(cbuf, off, len);
            appendChunk(chunk);
        }

        @Override
        public void write(String str, int off, int len) {
            if (str == null || len <= 0 || currentBytes >= maxBytes) {
                return;
            }
            appendChunk(str.substring(off, off + len));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return builder.toString();
        }

        private void appendChunk(String chunk) {
            if (chunk.isEmpty() || currentBytes >= maxBytes) {
                return;
            }
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            int remaining = maxBytes - currentBytes;
            if (bytes.length <= remaining) {
                builder.append(chunk);
                currentBytes += bytes.length;
                return;
            }
            int allowedChars = allowedChars(chunk, remaining);
            if (allowedChars > 0) {
                builder.append(chunk, 0, allowedChars);
                currentBytes = maxBytes;
            } else {
                currentBytes = maxBytes;
            }
        }

        private int allowedChars(String chunk, int remainingBytes) {
            int usedBytes = 0;
            for (int i = 0; i < chunk.length();) {
                int charCount = Character.charCount(chunk.codePointAt(i));
                int charBytes = chunk.substring(i, i + charCount).getBytes(StandardCharsets.UTF_8).length;
                if (usedBytes + charBytes > remainingBytes) {
                    return i;
                }
                usedBytes += charBytes;
                i += charCount;
            }
            return chunk.length();
        }
    }
}
