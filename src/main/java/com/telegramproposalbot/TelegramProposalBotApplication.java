package com.telegramproposalbot;

import com.telegramproposalbot.telegram.bot.MainBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Главный класс приложения Spring Boot
 */
@SpringBootApplication
@EnableScheduling
public class TelegramProposalBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramProposalBotApplication.class, args);
    }
}
@Component
class BotInitializer {

    @Autowired
    private MainBot mainBot;

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        try {
            // Создаем API без автоматического удаления вебхука
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Регистрируем бота с опцией отключения проверки вебхука
            telegramBotsApi.registerBot(mainBot);

            System.out.println("MainBot успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            System.err.println("Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
    }
}