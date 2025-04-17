package com.telegramproposalbot.telegram.handler;

import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.PaymentService;
import com.telegramproposalbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
public class CallbackQueryHandler {

    private final BotService botService;
    private final UserService userService;
    private final PaymentService paymentService;

    @Autowired
    public CallbackQueryHandler(BotService botService, UserService userService, PaymentService paymentService) {
        this.botService = botService;
        this.userService = userService;
        this.paymentService = paymentService;
    }

    public BotApiMethod<?> handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        String[] data = callbackData.split(":");
        String action = data[0];

        switch (action) {
            case "create_bot":
                return handleCreateBot(callbackQuery);
            case "my_bots":
                return handleMyBots(callbackQuery);
            case "manage_bot":
                return handleManageBot(callbackQuery, data);
            case "bot_help":
                return handleBotHelp(callbackQuery);
            case "help":
                return handleHelp(callbackQuery);
            case "premium_info":
                return handlePremiumInfo(callbackQuery);
            case "buy_premium":
                return handleBuyPremium(callbackQuery);
            // Другие обработчики...
            default:
                return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                        "Неизвестное действие, пожалуйста, попробуйте снова.");
        }
    }

    private BotApiMethod<?> handleCreateBot(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        // Переводим пользователя в режим создания бота
        userService.clearTemporaryData(chatId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText("🤖 Для создания нового бота вам потребуется токен от @BotFather.\n\n" +
                "1. Напишите @BotFather и создайте нового бота командой /newbot\n" +
                "2. Скопируйте полученный токен и отправьте его мне\n\n" +
                "Пожалуйста, отправьте токен вашего бота:");

        return message;
    }

    private BotApiMethod<?> handleMyBots(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

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

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText(botsListText.toString());
        message.enableMarkdown(true);

        // Здесь также добавляем inline-клавиатуру с кнопками управления ботами

        return message;
    }

    private BotApiMethod<?> handleManageBot(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        // Здесь добавляем логику управления конкретным ботом

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText("Управление ботом. Выберите действие:");

        // Добавляем inline-клавиатуру с опциями управления ботом

        return message;
    }

    // Другие методы обработки...

    private BotApiMethod<?> handleBotHelp(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText("📚 *Как пользоваться ботом-предложкой*\n\n" +
                "1. Добавьте бота в администраторы вашего канала\n" +
                "2. Настройте бота через меню 'Управление ботом'\n" +
                "3. Когда пользователи пишут боту, вы получаете их сообщения\n" +
                "4. Для каждого сообщения у вас есть кнопки: опубликовать, редактировать, отклонить\n" +
                "5. Опубликованные сообщения появляются в канале\n\n" +
                "Дополнительные команды бота:\n" +
                "/help - эта справка\n" +
                "/stats - статистика бота\n" +
                "/settings - настройки бота");
        message.enableMarkdown(true);

        return message;
    }

    private BotApiMethod<?> handleHelp(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText("📚 *Справка*\n\n" +
                "Этот бот позволяет создавать и управлять ботами-предложками для ваших Telegram-каналов.\n\n" +
                "🤖 *Что такое бот-предложка?*\n" +
                "Это бот, который собирает предложения от подписчиков и пересылает их администраторам канала для модерации.\n\n" +
                "🚀 *Как начать?*\n" +
                "1. Создайте бота через @BotFather\n" +
                "2. Нажмите 'Создать нового бота' в меню\n" +
                "3. Введите токен, полученный от @BotFather\n" +
                "4. Настройте и запустите своего бота\n\n" +
                "💎 *Premium подписка*\n" +
                "Получите доступ к расширенным функциям:\n" +
                "• Несколько ботов-предложек\n" +
                "• Отложенная публикация\n" +
                "• Кастомная аватарка бота\n" +
                "• И многое другое");
        message.enableMarkdown(true);

        return message;
    }

    private BotApiMethod<?> handlePremiumInfo(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText("💎 *Premium подписка*\n\n" +
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
        message.enableMarkdown(true);

        // Добавляем кнопку покупки

        return message;
    }

    private BotApiMethod<?> handleBuyPremium(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        // Здесь можно реализовать отправку инвойса Telegram Payments
        // или перенаправление на страницу оплаты

        return paymentService.createSubscriptionInvoice(chatId, "monthly");
    }
}