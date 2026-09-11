package kz.birchat.api.service.sms;

public interface SmsSender {

    SmsSendResult send(String phone, String message);
}