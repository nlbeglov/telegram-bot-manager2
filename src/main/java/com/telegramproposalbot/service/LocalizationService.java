package com.telegramproposalbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для локализации сообщений
 */
@Service
public class LocalizationService {

    // Карта пользовательских локалей
    private final Map<Long, Locale> userLocales = new ConcurrentHashMap<>();

    private final MessageSource messageSource;

    @Autowired
    public LocalizationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Получает локализованное сообщение
     */
    public String getMessage(String code, Object[] args, Long userId) {
        Locale locale = getUserLocale(userId);
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * Получает локализованное сообщение с текущей локалью
     */
    public String getMessage(String code, Object[] args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Получает локализованное сообщение без аргументов
     */
    public String getMessage(String code, Long userId) {
        return getMessage(code, null, userId);
    }

    /**
     * Получает локализованное сообщение без аргументов с текущей локалью
     */
    public String getMessage(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * Устанавливает локаль для пользователя
     */
    public void setUserLocale(Long userId, String languageCode) {
        Locale locale;
        switch (languageCode.toLowerCase()) {
            case "en":
                locale = Locale.ENGLISH;
                break;
            case "ru":
                locale = new Locale("ru");
                break;
            default:
                locale = Locale.ENGLISH;
                break;
        }
        userLocales.put(userId, locale);
    }

    /**
     * Получает локаль пользователя
     */
    public Locale getUserLocale(Long userId) {
        return userLocales.getOrDefault(userId, new Locale("ru")); // По умолчанию русский
    }
}