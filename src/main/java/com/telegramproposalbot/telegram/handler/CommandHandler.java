package com.telegramproposalbot.telegram.handler;

import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommandHandler {

    private final BotService botService;
    private final UserService userService;

    @Autowired
    public CommandHandler(BotService botService, UserService userService) {
        this.botService = botService;
        this.userService = userService;
    }

    public BotApiMethod<?> handleCommand(Message message) {
        String command = message.getText();
        Long chatId = message.getChatId();

        switch (command) {
            case "/start":
                return handleStart(message);
            case "/help":
                return handleHelp(message);
            case "/mybots":
                return handleMyBots(message);
            case "/create":
                return handleCreate(message);
            case "/premium":
                return handlePremium(message);
            // Другие команды...
            default:
                return new SendMessage(chatId.toString(),
                        "Неизвестная команда. Отправьте /help для получения списка доступных команд.");
        }
    }

    private SendMessage handleStart(Message message) {
        Long chatId = message.getChatId();
        String username = message.getFrom().getFirstName();

        // Регистрируем пользователя
        userService.getOrCreateUser(message.getFrom());

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());
        response.setText("👋 Привет, " + username + "!\n\n" +
                "Я - менеджер ботов-предложек для Telegram-каналов. " +
                "С моей помощью вы можете создать и настроить ботов, " +
                "которые будут собирать предложения от подписчиков " +
                "и пересылать их вам для модерации.\n\n" +
                "Чтобы начать, выберите действие:");

        // Создаем клавиатуру с кнопками
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопка создания бота
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("🤖 Создать нового бота");
        createButton.setCallbackData("create_bot");
        row1.add(createButton);
        keyboard.add(row1);

        // Кнопка справки
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Как это работает");
        helpButton.setCallbackData("help");
        row2.add(helpButton);
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        response.setReplyMarkup(markup);

        return response;
    }

    private SendMessage handleHelp(Message message) {
        Long chatId = message.getChatId();

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());
        response.setText("📚 *Доступные команды*\n\n" +
                "/start - начать работу с ботом\n" +
                "/help - показать эту справку\n" +
                "/mybots - список ваших ботов\n" +
                "/create - создать нового бота\n" +
                "/premium - информация о Premium подписке\n\n" +
                "Также вы можете использовать кнопки в меню для удобной навигации.");
        response.enableMarkdown(true);

        return response;
    }

    private SendMessage handleMyBots(Message message) {
        Long chatId = message.getChatId();

        // Получаем список ботов пользователя
        var user = userService.getUserByTelegramId(chatId);
        var userBots = botService.getUserBots(user.getId());

        StringBuilder botsListText = new StringBuilder();
        botsListText.append("🤖 *Ваши боты*\n\n");

        if (userBots.isEmpty()) {
            botsListText.append("У вас пока нет ботов-предложек. Создайте первого бота!");
        } else {
            for (int i = 0; i < userBots.size(); i++) {
                var bot = userBots.get(i);
                botsListText.append(i + 1).append(". *").append(bot.getName()).append("*\n");
                botsListText.append("Статус: ").append(bot.isActive() ? "✅ Активен" : "❌ Неактивен").append("\n\n");
            }
        }

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());
        response.setText(botsListText.toString());
        response.enableMarkdown(true);

        // Добавляем inline-клавиатуру с кнопками управления ботами

        return response;
    }

    private SendMessage handleCreate(Message message) {
        Long chatId = message.getChatId();

        // Проверяем, может ли пользователь создать еще одного бота
        var user = userService.getUserByTelegramId(chatId);
        if (!userService.canCreateBot(user)) {
            SendMessage response = new SendMessage();
            response.setChatId(chatId.toString());
            response.setText("⚠️ Вы достигли лимита ботов для бесплатной версии.\n\n" +
                    "Приобретите Premium подписку, чтобы создавать больше ботов.");

            // Добавляем кнопку покупки Premium

            return response;
        }

        // Переводим пользователя в режим создания бота
        userService.clearTemporaryData(chatId);

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());
        response.setText("🤖 Для создания нового бота вам потребуется токен от @BotFather.\n\n" +
                "1. Напишите @BotFather и создайте нового бота командой /newbot\n" +
                "2. Скопируйте полученный токен и отправьте его мне\n\n" +
                "Пожалуйста, отправьте токен вашего бота:");

        return response;
    }

    private SendMessage handlePremium(Message message) {
        Long chatId = message.getChatId();

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());
        response.setText("💎 *Premium подписка*\n\n" +
                "Откройте для себя расширенные возможности:\n\n" +
                "✅ До 10 ботов-предложек\n" +
                "✅ До 10 администраторов на каждого бота\n" +
                "✅ До 5 каналов для публикации\n" +
                "✅ Отложенная публикация\n" +
                "✅ Смена аватарки бота\n" +
                "✅ Убрать ссылку на основного бота\n" +
                "✅ Интеграция Telegram Mini Apps\n\n" +
                "Цены:\n" +
                "• Месячная подписка: 299₽\n" +
                "• Годовая подписка: 2990₽\n\n" +
                "Нажмите 'Купить Premium' для оформления подписки.");
        response.enableMarkdown(true);

        // Добавляем кнопку покупки

        return response;
    }
}