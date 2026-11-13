package kz.birchat.api.service.media;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Чтение PDF для AI: текст документа, а для сканов (текста нет) — первые страницы картинками,
 * чтобы их прочитала модель с «зрением».
 *
 * PDF читается с диска с временными файлами вместо памяти — сервер работает в 512 МБ.
 */
public final class PdfReader {

    /** Сколько страниц читать — договор или выписка дальше редко нужны для чата. */
    public static final int MAX_TEXT_PAGES = 20;
    public static final int MAX_TEXT_CHARS = 8000;
    /** Меньше стольких букв и цифр — считаем, что это скан без текстового слоя. */
    static final int MIN_TEXT_CHARS = 40;
    private static final float RENDER_DPI = 150f;

    private PdfReader() {
    }

    public record PdfText(int pages, String text) {

        /** Скан (картинки страниц без текста) — текст надо читать моделью с «зрением». */
        public boolean scanned() {
            return text.codePoints().filter(Character::isLetterOrDigit).count() < MIN_TEXT_CHARS;
        }
    }

    public static PdfText readText(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            int pages = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pages, MAX_TEXT_PAGES));

            return new PdfText(pages, normalize(stripper.getText(document)));
        }
    }

    /** Первые страницы картинками JPEG — для сканов. */
    public static List<byte[]> renderPages(Path file, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int count = Math.min(document.getNumberOfPages(), maxPages);
            List<byte[]> images = new ArrayList<>(count);

            for (int page = 0; page < count; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                if (!ImageIO.write(image, "jpg", out)) {
                    throw new IOException("JPEG writer is not available");
                }

                images.add(out.toByteArray());
            }

            return images;
        }
    }

    /** Пустые строки и пробелы схлопываются, текст обрезается до MAX_TEXT_CHARS. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String value = text
                .replace("\r", "")
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return value.length() <= MAX_TEXT_CHARS ? value : value.substring(0, MAX_TEXT_CHARS) + "…";
    }
}
