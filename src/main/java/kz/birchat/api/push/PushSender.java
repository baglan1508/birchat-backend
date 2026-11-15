package kz.birchat.api.push;

/** Отправка одного уведомления на одно устройство. */
public interface PushSender {

    enum Result {
        SENT,
        /** Токен больше не действует (приложение удалено и т.п.) — его нужно удалить. */
        INVALID_TOKEN,
        FAILED
    }

    boolean isConfigured();

    /**
     * @param environment production или sandbox — от него зависит сервер Apple
     * @param payloadJson тело уведомления (JSON)
     */
    Result send(String deviceToken, String environment, String payloadJson);
}
