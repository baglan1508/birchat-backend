package kz.birchat.api.push;

/** Отправка одного уведомления на одно устройство. */
public interface PushSender {

    enum Result {
        SENT,
        /** Токен больше не действует (приложение удалено и т.п.) — его нужно удалить. */
        INVALID_TOKEN,
        FAILED
    }

    /** Итог отправки с подробностями для диагностики: HTTP-статус и причина от Apple/Google. */
    record Delivery(Result result, int status, String reason) {
    }

    boolean isConfigured();

    /**
     * @param environment production или sandbox — от него зависит сервер Apple
     * @param payloadJson тело уведомления (JSON)
     */
    Result send(String deviceToken, String environment, String payloadJson);

    /**
     * Тихое обновление (только бейдж): низкий приоритет, Apple доставляет его, когда удобно телефону.
     */
    default Result sendQuiet(String deviceToken, String environment, String payloadJson) {
        return send(deviceToken, environment, payloadJson);
    }

    /** Как send, но с подробностями (для тестового пуша). */
    default Delivery deliver(String deviceToken, String environment, String payloadJson) {
        return new Delivery(send(deviceToken, environment, payloadJson), 0, null);
    }
}
