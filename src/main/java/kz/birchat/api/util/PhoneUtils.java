package kz.birchat.api.util;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Телефон обязателен");
        }

        String digits = input.replaceAll("\\D", "");

        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }

        if (digits.length() == 10) {
            digits = "7" + digits;
        }

        if (digits.length() != 11 || !digits.startsWith("7")) {
            throw new IllegalArgumentException("Некорректный номер телефона");
        }

        return "+" + digits;
    }
}