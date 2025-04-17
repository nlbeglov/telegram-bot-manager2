package com.telegramproposalbot.exception;

/**
 * Исключение, возникающее при ошибке создания бота
 */
public class BotCreationException extends Exception {

    public BotCreationException(String message) {
        super(message);
    }

    public BotCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}