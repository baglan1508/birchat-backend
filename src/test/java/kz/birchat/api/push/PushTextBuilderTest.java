package kz.birchat.api.push;

import kz.birchat.api.enums.ChatMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PushTextBuilderTest {

    @Test
    void plainText() {
        assertEquals("Нурлан: По оплате ждём подтверждение до пятницы",
                PushTextBuilder.body("Нурлан", ChatMessageType.TEXT, "По оплате ждём подтверждение до пятницы", null, "ru"));
    }

    @Test
    void longTextIsCutToHundredCharacters() {
        String body = PushTextBuilder.body("Нурлан", ChatMessageType.TEXT, "а".repeat(150), null, "ru");

        assertEquals("Нурлан: " + "а".repeat(100) + "…", body);
    }

    @Test
    void replyQuoteLineIsRemoved() {
        assertEquals("Нурлан: Да, согласен",
                PushTextBuilder.body("Нурлан", ChatMessageType.TEXT, "> Айгерим: завтра в 10?\nДа, согласен", null, "ru"));
    }

    @Test
    void quoteWithoutSecondLineIsOrdinaryText() {
        assertEquals("Нурлан: > 100 штук", PushTextBuilder.body("Нурлан", ChatMessageType.TEXT, "> 100 штук", null, "ru"));
    }

    @Test
    void forwardedTextKeepsOnlyTheText() {
        assertEquals("Нурлан: Счёт оплачен",
                PushTextBuilder.body("Нурлан", ChatMessageType.TEXT, ">> Переслано от Айгерим\nСчёт оплачен", null, "ru"));
    }

    @Test
    void forwardedFileUsesAttachmentLabel() {
        assertEquals("Нурлан: 📎 Накладная.pdf",
                PushTextBuilder.body("Нурлан", ChatMessageType.DOCUMENT, ">> Переслано от Айгерим", "Накладная.pdf", "ru"));
    }

    @Test
    void attachmentLabelsFollowDeviceLanguage() {
        assertEquals("Нурлан: 📷 Фото", PushTextBuilder.body("Нурлан", ChatMessageType.IMAGE, "", null, "ru"));
        assertEquals("Нурлан: 📷 Фотосурет", PushTextBuilder.body("Нурлан", ChatMessageType.IMAGE, null, null, "kk"));
        assertEquals("Нурлан: 🎤 Голосовое сообщение", PushTextBuilder.body("Нурлан", ChatMessageType.VOICE, " ", null, "ru"));
        assertEquals("Нурлан: 🎤 Дауыстық хабарлама", PushTextBuilder.body("Нурлан", ChatMessageType.VOICE, null, null, "kk"));
    }

    @Test
    void photoWithCaptionShowsCaption() {
        assertEquals("Нурлан: Вот накладная", PushTextBuilder.body("Нурлан", ChatMessageType.IMAGE, "Вот накладная", "IMG.jpg", "ru"));
    }
}
