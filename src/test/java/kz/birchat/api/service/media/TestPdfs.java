package kz.birchat.api.service.media;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;

/** PDF для тестов: с текстовым слоем и «скан» (страница без текста). */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static void textPdf(Path target, String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);

                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }

                content.endText();
            }

            document.save(target.toFile());
        }
    }

    public static void blankPdf(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(target.toFile());
        }
    }
}
