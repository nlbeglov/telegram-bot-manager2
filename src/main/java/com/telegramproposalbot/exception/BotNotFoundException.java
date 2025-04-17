package com.telegramproposalbot.exception;

/**
 * Исключение, возникающее когда бот не найден
 */
public class BotNotFoundException extends Exception {

    public BotNotFoundException(String message) {
        super(message);
    }

    public BotNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}