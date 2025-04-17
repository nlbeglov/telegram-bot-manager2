package com.telegramproposalbot.telegram.handler;

import com.telegramproposalbot.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

@Component
public class CallbackQueryHandler {

    private final BotService botService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final LocalizationService localizationService;
    @Autowired
    private final ChannelService channelService;

    @Autowired
    public CallbackQueryHandler(BotService botService, UserService userService,
                                PaymentService paymentService, LocalizationService localizationService, ChannelService channelService) {
        this.botService = botService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.localizationService = localizationService;
        this.channelService = channelService;
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
            case "back_to_main":
                return handleBackToMain(callbackQuery);
            case "toggle_bot":
                return handleToggleBot(callbackQuery, data);
            case "bot_settings":
                return handleBotSettings(callbackQuery, data);
            case "bot_admins":
                return handleBotAdmins(callbackQuery, data);
            case "bot_channels":
                return handleBotChannels(callbackQuery, data);
            case "bot_stats":
                return handleBotStats(callbackQuery, data);
            case "delete_bot":
                return handleDeleteBot(callbackQuery, data);
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

        // Используем локализованные тексты
        String title = localizationService.getMessage("bot.create.title", chatId);
        String instructions = localizationService.getMessage("bot.create.instructions", chatId);

        message.setText(title + "\n\n" + instructions);

        // Добавляем кнопку "Назад"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row.add(backButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handleMyBots(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        // Получаем список ботов пользователя
        var user = userService.getUserByTelegramId(chatId);
        var userBots = botService.getUserBots(user.getId());

        StringBuilder botsListText = new StringBuilder();
        botsListText.append(localizationService.getMessage("bots.list.title", chatId)).append("\n\n");

        if (userBots.isEmpty()) {
            botsListText.append(localizationService.getMessage("bots.list.empty", chatId));
        } else {
            for (int i = 0; i < userBots.size(); i++) {
                var bot = userBots.get(i);
                botsListText.append(i + 1).append(". *").append(bot.getName()).append("*\n");

                String statusKey = bot.isActive() ? "bots.list.status_active" : "bots.list.status_inactive";
                botsListText.append(localizationService.getMessage("common.status", chatId))
                        .append(": ")
                        .append(localizationService.getMessage(statusKey, chatId))
                        .append("\n\n");
            }
        }

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());
        message.setText(botsListText.toString());
        message.enableMarkdown(true);

        // Добавляем inline-клавиатуру с кнопками управления ботами и кнопкой "Назад"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (!userBots.isEmpty()) {
            for (var bot : userBots) {
                List<InlineKeyboardButton> botRow = new ArrayList<>();
                InlineKeyboardButton manageButton = new InlineKeyboardButton();
                manageButton.setText("⚙️ " + bot.getName());
                manageButton.setCallbackData("manage_bot:" + bot.getId());
                botRow.add(manageButton);
                keyboard.add(botRow);
            }
        }

        // Кнопка "Создать нового бота"
        List<InlineKeyboardButton> createRow = new ArrayList<>();
        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("➕ " + localizationService.getMessage("common.create_bot_button", chatId));
        createButton.setCallbackData("create_bot");
        createRow.add(createButton);
        keyboard.add(createRow);

        // Кнопка "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        backRow.add(backButton);
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handleManageBot(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        // Получаем информацию о боте
        try {
            var bot = botService.getBotById(botId);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setText("⚙️ *Управление ботом " + bot.getName() + "*\n\n" +
                    "Выберите действие для управления ботом:");
            message.enableMarkdown(true);

            // Создаем клавиатуру с опциями управления
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Активация/деактивация
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton toggleButton = new InlineKeyboardButton();
            toggleButton.setText(bot.isActive() ? "❌ Деактивировать" : "✅ Активировать");
            toggleButton.setCallbackData("toggle_bot:" + botId + ":" + !bot.isActive());
            row1.add(toggleButton);
            keyboard.add(row1);

            // Настройки
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton settingsButton = new InlineKeyboardButton();
            settingsButton.setText("⚙️ Настройки");
            settingsButton.setCallbackData("bot_settings:" + botId);
            row2.add(settingsButton);
            keyboard.add(row2);

            // Администраторы
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            InlineKeyboardButton adminsButton = new InlineKeyboardButton();
            adminsButton.setText("👥 Администраторы");
            adminsButton.setCallbackData("bot_admins:" + botId);
            row3.add(adminsButton);
            keyboard.add(row3);

            // Каналы
            List<InlineKeyboardButton> row4 = new ArrayList<>();
            InlineKeyboardButton channelsButton = new InlineKeyboardButton();
            channelsButton.setText("📢 Каналы");
            channelsButton.setCallbackData("bot_channels:" + botId);
            row4.add(channelsButton);
            keyboard.add(row4);

            // Статистика
            List<InlineKeyboardButton> row5 = new ArrayList<>();
            InlineKeyboardButton statsButton = new InlineKeyboardButton();
            statsButton.setText("📊 Статистика");
            statsButton.setCallbackData("bot_stats:" + botId);
            row5.add(statsButton);
            keyboard.add(row5);

            // Веб-панель
            List<InlineKeyboardButton> row6 = new ArrayList<>();
            InlineKeyboardButton webAppButton = new InlineKeyboardButton();
            webAppButton.setText("🌐 Открыть веб-панель");

            // Создаем ссылку на веб-приложение с передачей ID бота
            var user = userService.getUserByTelegramId(chatId);
            if (user != null) {
                webAppButton.setWebApp(new org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo(
                        "https://telegrambot-manager.example.com/webapp?user_id=" + user.getId() + "&bot_id=" + botId
                ));
                row6.add(webAppButton);
                keyboard.add(row6);
            }

            // Удаление
            List<InlineKeyboardButton> row7 = new ArrayList<>();
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("🗑️ Удалить бота");
            deleteButton.setCallbackData("delete_bot:" + botId);
            row7.add(deleteButton);
            keyboard.add(row7);

            // Кнопка "Назад к списку ботов"
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад к списку ботов");
            backButton.setCallbackData("my_bots");
            backRow.add(backButton);
            keyboard.add(backRow);

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            return message;

        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("my_bots");
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleBackToMain(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());

        String welcome = localizationService.getMessage("common.welcome", chatId);
        String createBot = localizationService.getMessage("common.create_bot", chatId);

        message.setText(welcome + "\n\n" + createBot);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("🤖 " + localizationService.getMessage("common.create_bot_button", chatId));
        createButton.setCallbackData("create_bot");
        row1.add(createButton);
        keyboard.add(row1);

        var user = userService.getUserByTelegramId(chatId);
        if (botService.userHasBots(user)) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton myBotsButton = new InlineKeyboardButton();
            myBotsButton.setText("📋 " + localizationService.getMessage("common.my_bots", chatId));
            myBotsButton.setCallbackData("my_bots");
            row2.add(myBotsButton);
            keyboard.add(row2);
        }

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ " + localizationService.getMessage("common.help", chatId));
        helpButton.setCallbackData("help");
        row3.add(helpButton);

        InlineKeyboardButton premiumButton = new InlineKeyboardButton();
        premiumButton.setText("💎 Premium");
        premiumButton.setCallbackData("premium_info");
        row3.add(premiumButton);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handleBotHelp(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());

        String title = localizationService.getMessage("bot.help.title", chatId);
        String steps = localizationService.getMessage("bot.help.steps", chatId);
        String commands = localizationService.getMessage("bot.help.commands", chatId);

        message.setText(title + "\n\n" + steps + "\n\n" + commands);
        message.enableMarkdown(true);

        // Добавляем кнопку "Назад"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row.add(backButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handleHelp(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());

        String title = localizationService.getMessage("help.title", chatId);
        String description = localizationService.getMessage("help.description", chatId);
        String whatIs = localizationService.getMessage("help.what_is", chatId);
        String howTo = localizationService.getMessage("help.how_to", chatId);
        String premium = localizationService.getMessage("help.premium", chatId);

        message.setText(title + "\n\n" + description + "\n\n" + whatIs + "\n\n" + howTo + "\n\n" + premium);
        message.enableMarkdown(true);

        // Добавляем кнопку "Назад"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row.add(backButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handlePremiumInfo(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(callbackQuery.getMessage().getMessageId());

        String title = localizationService.getMessage("premium.info.title", chatId);
        String description = localizationService.getMessage("premium.info.description", chatId);
        String features = localizationService.getMessage("premium.info.features", chatId);
        String prices = localizationService.getMessage("premium.info.prices", chatId);
        String buy = localizationService.getMessage("premium.info.buy", chatId);

        message.setText(title + "\n\n" + description + "\n\n" + features + "\n\n" + prices + "\n\n" + buy);
        message.enableMarkdown(true);

        // Добавляем кнопки покупки и "Назад"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопки подписок
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton monthlyButton = new InlineKeyboardButton();
        monthlyButton.setText("💳 " + localizationService.getMessage("premium.monthly_subscription", chatId));
        monthlyButton.setCallbackData("buy_premium:monthly");
        row1.add(monthlyButton);
        keyboard.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton yearlyButton = new InlineKeyboardButton();
        yearlyButton.setText("💳 " + localizationService.getMessage("premium.yearly_subscription", chatId));
        yearlyButton.setCallbackData("buy_premium:yearly");
        row2.add(yearlyButton);
        keyboard.add(row2);

        // Кнопка "Назад"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row3.add(backButton);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private BotApiMethod<?> handleBuyPremium(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String planType = "monthly";

        // Проверяем, указан ли тип подписки в callbackData
        String[] data = callbackQuery.getData().split(":");
        if (data.length > 1) {
            planType = data[1];
        }

        // Создаем и возвращаем инвойс для оплаты
        return paymentService.createSubscriptionInvoice(chatId, planType);
    }

    private BotApiMethod<?> handleToggleBot(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 3) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        boolean active = Boolean.parseBoolean(data[2]);
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Активируем/деактивируем бота
            botService.toggleBotActive(botId, active);

            // Возвращаемся в меню управления ботом
            return handleManageBot(callbackQuery, new String[]{"manage_bot", botId.toString()});
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при изменении статуса бота: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleBotSettings(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Получаем данные о боте
            var bot = botService.getBotById(botId);
            Map<String, String> settings = botService.getBotSettingsMap(botId);

            StringBuilder settingsText = new StringBuilder();
            settingsText.append("⚙️ *Настройки бота ").append(bot.getName()).append("*\n\n");
            settingsText.append("Здесь вы можете изменить основные настройки вашего бота.\n\n");

            settingsText.append("🤖 *Имя бота:* ").append(bot.getName()).append("\n");
            settingsText.append("💬 *Приветственное сообщение:*\n").append(bot.getWelcomeMessage()).append("\n\n");
            settingsText.append("✅ *Сообщение о получении:*\n").append(bot.getConfirmationMessage()).append("\n\n");

            if (settings.containsKey("publication_footer")) {
                settingsText.append("📝 *Подпись к публикациям:*\n").append(settings.get("publication_footer")).append("\n\n");
            }

            settingsText.append("Чтобы изменить настройки, используйте соответствующие команды:");

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setText(settingsText.toString());
            message.enableMarkdown(true);

            // Создаем клавиатуру
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Кнопки изменения настроек
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton nameButton = new InlineKeyboardButton();
            nameButton.setText("✏️ Изменить имя");
            nameButton.setCallbackData("edit_bot_name:" + botId);
            row1.add(nameButton);
            keyboard.add(row1);

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton welcomeButton = new InlineKeyboardButton();
            welcomeButton.setText("✏️ Изменить приветствие");
            welcomeButton.setCallbackData("edit_bot_welcome:" + botId);
            row2.add(welcomeButton);
            keyboard.add(row2);

            List<InlineKeyboardButton> row3 = new ArrayList<>();
            InlineKeyboardButton confirmButton = new InlineKeyboardButton();
            confirmButton.setText("✏️ Изменить сообщение о получении");
            confirmButton.setCallbackData("edit_bot_confirmation:" + botId);
            row3.add(confirmButton);
            keyboard.add(row3);

            // Кнопка "Назад"
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад к управлению ботом");
            backButton.setCallbackData("manage_bot:" + botId);
            backRow.add(backButton);
            keyboard.add(backRow);

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            return message;
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при загрузке настроек: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleBotAdmins(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Получаем список администраторов
            var adminsList = botService.getAdminIds(botId);

            StringBuilder adminsText = new StringBuilder();
            adminsText.append("👥 *Администраторы бота*\n\n");

            if (adminsList.isEmpty()) {
                adminsText.append("У бота пока нет администраторов кроме вас.\n");
            } else {
                adminsText.append("Список администраторов бота:\n");
                for (int i = 0; i < adminsList.size(); i++) {
                    Long adminId = adminsList.get(i);
                    adminsText.append(i + 1).append(". ID: ").append(adminId);

                    // Проверяем, главный ли это администратор
                    if (botService.isAdmin(botId, adminId)) {
                        // Можно добавить комментарий "Администратор"
                        adminsText.append(" (Администратор)");
                    }

                    adminsText.append("\n");
                }
            }

            adminsText.append("\nЧтобы добавить администратора, используйте кнопку ниже.");

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setText(adminsText.toString());
            message.enableMarkdown(true);

            // Создаем клавиатуру
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Кнопка добавления администратора
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton addButton = new InlineKeyboardButton();
            addButton.setText("➕ Добавить администратора");
            addButton.setCallbackData("add_admin:" + botId);
            row1.add(addButton);
            keyboard.add(row1);

            // Кнопка "Назад"
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад к управлению ботом");
            backButton.setCallbackData("manage_bot:" + botId);
            backRow.add(backButton);
            keyboard.add(backRow);

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            return message;
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при загрузке администраторов: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleBotChannels(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Получаем список каналов
            var channelsInfo = channelService.getChannelsInfo(botId);

            StringBuilder channelsText = new StringBuilder();
            channelsText.append("📢 *Каналы бота для публикаций*\n\n");

            if (channelsInfo.isEmpty()) {
                channelsText.append("У бота пока нет настроенных каналов.\n");
            } else {
                channelsText.append("Список каналов для публикаций:\n");
                for (int i = 0; i < channelsInfo.size(); i++) {
                    var channel = channelsInfo.get(i);
                    channelsText.append(i + 1).append(". @").append(channel.get("username"));
                    if (channel.containsKey("title") && !channel.get("title").isEmpty()) {
                        channelsText.append(" (").append(channel.get("title")).append(")");
                    }
                    channelsText.append("\n");
                }
            }

            channelsText.append("\nЧтобы добавить канал, используйте кнопку ниже.\n");
            channelsText.append("⚠️ Не забудьте добавить бота администратором канала с правом публикации сообщений!");

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setText(channelsText.toString());
            message.enableMarkdown(true);

            // Создаем клавиатуру
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Кнопка добавления канала
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton addButton = new InlineKeyboardButton();
            addButton.setText("➕ Добавить канал");
            addButton.setCallbackData("add_channel:" + botId);
            row1.add(addButton);
            keyboard.add(row1);

            // Кнопка "Назад"
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад к управлению ботом");
            backButton.setCallbackData("manage_bot:" + botId);
            backRow.add(backButton);
            keyboard.add(backRow);

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            return message;
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при загрузке каналов: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleBotStats(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Получаем статистику бота
            Map<String, Integer> stats = botService.getBotStats(botId);

            StringBuilder statsText = new StringBuilder();
            statsText.append("📊 *Статистика бота*\n\n");

            statsText.append("📨 Сообщений получено: ").append(stats.getOrDefault("messages_received", 0)).append("\n");
            statsText.append("📢 Сообщений опубликовано: ").append(stats.getOrDefault("messages_published", 0)).append("\n");
            statsText.append("❌ Сообщений отклонено: ").append(stats.getOrDefault("messages_rejected", 0)).append("\n");
            statsText.append("⛔ Заблокировано пользователей: ").append(stats.getOrDefault("users_blocked", 0)).append("\n");

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setText(statsText.toString());
            message.enableMarkdown(true);

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад к управлению ботом");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            return message;
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при загрузке статистики: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }

    private BotApiMethod<?> handleDeleteBot(CallbackQuery callbackQuery, String[] data) {
        if (data.length < 2) {
            return new SendMessage(callbackQuery.getMessage().getChatId().toString(),
                    "Ошибка: неверный формат данных");
        }

        Long botId = Long.parseLong(data[1]);
        Long chatId = callbackQuery.getMessage().getChatId();

        // Сначала показываем подтверждение
        if (data.length < 3 || !data[2].equals("confirm")) {
            EditMessageText confirmMessage = new EditMessageText();
            confirmMessage.setChatId(chatId.toString());
            confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            confirmMessage.setText("⚠️ *Вы уверены, что хотите удалить этого бота?*\n\n" +
                    "Это действие необратимо. Все настройки, администраторы и каналы будут удалены.");
            confirmMessage.enableMarkdown(true);

            // Кнопки подтверждения
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton confirmButton = new InlineKeyboardButton();
            confirmButton.setText("✅ Да, удалить бота");
            confirmButton.setCallbackData("delete_bot:" + botId + ":confirm");
            row1.add(confirmButton);
            keyboard.add(row1);

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton cancelButton = new InlineKeyboardButton();
            cancelButton.setText("❌ Нет, отменить");
            cancelButton.setCallbackData("manage_bot:" + botId);
            row2.add(cancelButton);
            keyboard.add(row2);

            markup.setKeyboard(keyboard);
            confirmMessage.setReplyMarkup(markup);

            return confirmMessage;
        }

        try {
            // Выполняем удаление бота
            botService.deleteBot(botId);

            EditMessageText successMessage = new EditMessageText();
            successMessage.setChatId(chatId.toString());
            successMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            successMessage.setText("✅ *Бот успешно удален*");
            successMessage.enableMarkdown(true);

            // Добавляем кнопку возврата к списку ботов
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Вернуться к списку ботов");
            backButton.setCallbackData("my_bots");
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            successMessage.setReplyMarkup(markup);

            return successMessage;
        } catch (Exception e) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при удалении бота: " + e.getMessage());

            // Добавляем кнопку "Назад"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("🔙 Назад");
            backButton.setCallbackData("manage_bot:" + botId);
            row.add(backButton);

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            return errorMessage;
        }
    }
}