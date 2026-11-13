package kz.birchat.api.service.media;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfReaderTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void readsWholeReceiptText(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("receipt.pdf");
        TestPdfs.textPdf(pdf,
                "Magnum Cash&Carry, BIN 123456789012",
                "Milk 2 x 450 = 900",
                "Bread 1 x 250 = 250",
                "TOTAL 1150 KZT, card");

        PdfReader.PdfText text = PdfReader.readText(pdf);

        assertEquals(1, text.pages());
        assertFalse(text.scanned());
        assertTrue(text.text().contains("Milk 2 x 450 = 900"), text.text());
        assertTrue(text.text().contains("Bread 1 x 250 = 250"), text.text());
        assertTrue(text.text().contains("TOTAL 1150 KZT, card"), text.text());
    }

    @Test
    void pageWithoutTextIsTreatedAsScanAndRendered(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("scan.pdf");
        TestPdfs.blankPdf(pdf);

        assertTrue(PdfReader.readText(pdf).scanned());

        List<byte[]> pages = PdfReader.renderPages(pdf, 2);

        assertEquals(1, pages.size());
        // JPEG начинается с FF D8
        assertEquals((byte) 0xFF, pages.get(0)[0]);
        assertEquals((byte) 0xD8, pages.get(0)[1]);
    }

    @Test
    void longTextIsCut() {
        String text = PdfReader.normalize("a".repeat(PdfReader.MAX_TEXT_CHARS + 100));

        assertEquals(PdfReader.MAX_TEXT_CHARS + 1, text.length());
        assertTrue(text.endsWith("…"));
    }

    @Test
    void blankLinesAndSpacesAreCollapsed() {
        assertEquals("Итого: 1 150\n\nОплата картой",
                PdfReader.normalize("  Итого:   1 150  \r\n\n\n\n Оплата\tкартой  "));
    }
}
