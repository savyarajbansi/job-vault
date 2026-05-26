package com.project8.jobvault.parsing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfResumeParserTest {
    private static final int MAX_PAGES = 20;
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void corruptedPdfReturnsParseFailed() {
        PdfResumeParser parser = buildParser();

        ParseErrorException ex = assertThrows(ParseErrorException.class, () -> parser.parse(new byte[] { 0x1, 0x2 }));

        assertEquals(ParseErrorCodes.PARSE_FAILED, ex.getCode());
    }

    @Test
    void simplePdfExtractsTextAndSkills() throws Exception {
        PdfResumeParser parser = buildParser();
        byte[] pdf = createPdfWithText("Java Spring developer");

        ParseResult result = parser.parse(pdf);

        assertFalse(result.extractedText().isBlank());
        assertTrue(result.inferredSkills().contains("java"));
    }

    @Test
    void emptyPdfReturnsEmptyTextError() throws Exception {
        PdfResumeParser parser = buildParser();
        byte[] pdf = createEmptyPdf();

        ParseErrorException ex = assertThrows(ParseErrorException.class, () -> parser.parse(pdf));

        assertEquals(ParseErrorCodes.EMPTY_TEXT, ex.getCode());
    }

    @Test
    void boundedStringWriterDoesNotSplitSurrogatePairsOrExceedByteBudget() throws Exception {
        Writer writer = createBoundedWriter(5);

        writer.write("A\uD83D\uDE00B");

        String output = writer.toString();
        assertEquals("A\uD83D\uDE00", output);
        assertEquals(5, output.getBytes(StandardCharsets.UTF_8).length);
    }

    private PdfResumeParser buildParser() {
        SkillCatalog catalog = new SkillCatalog("classpath:skills/skill-dictionary.txt");
        return new PdfResumeParser(MAX_PAGES, MAX_TEXT_BYTES, TIMEOUT, catalog);
    }

    private Writer createBoundedWriter(int maxBytes) throws Exception {
        Class<?> writerClass = Class.forName("com.project8.jobvault.parsing.PdfResumeParser$BoundedStringWriter");
        Constructor<?> ctor = writerClass.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        return (Writer) ctor.newInstance(maxBytes);
    }

    private byte[] createPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createEmptyPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
