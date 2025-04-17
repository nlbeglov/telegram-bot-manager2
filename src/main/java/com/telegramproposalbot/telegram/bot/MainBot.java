package com.telegramproposalbot.telegram.bot;

import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.LocalizationService;
import com.telegramproposalbot.service.UserService;
import com.telegramproposalbot.telegram.handler.CallbackQueryHandler;
import com.telegramproposalbot.telegram.handler.CommandHandler;
import com.telegramproposalbot.telegram.miniapp.MiniAppHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MainBot extends TelegramLongPollingBot {

    private final UserService userService;
    private final BotService botService;
    private final CommandHandler commandHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final MiniAppHandler miniAppHandler;
    private final LocalizationService localizationService;

    // Хранение состояний бесед с пользователями
    private final Map<Long, UserState> userStates = new HashMap<>();

    @Value("${telegram.bot.main.username}")
    private String botUsername;

    @Value("${telegram.bot.main.token}")
    private String botToken;

    public MainBot(UserService userService, BotService botService,
                   CommandHandler commandHandler, CallbackQueryHandler callbackQueryHandler,
                   MiniAppHandler miniAppHandler, LocalizationService localizationService) {
        super(new DefaultBotOptions(), "placeholder-token"); // Будет обновлен после инициализации
        this.userService = userService;
        this.botService = botService;
        this.commandHandler = commandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.miniAppHandler = miniAppHandler;
        this.localizationService = localizationService;
    }

    @PostConstruct
    public void init() {
        // Обновляем токен после инициализации бина
        try {
            Field tokenField = DefaultAbsSender.class.getDeclaredField("botToken");
            tokenField.setAccessible(true);
            tokenField.set(this, botToken);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить токен бота", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // Метод getBotToken() устарел, но нам все еще нужно его переопределить
    @Deprecated
    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Обработка сообщений
            if (update.hasMessage()) {
                // Проверяем, не запрашивает ли пользователь изменение языка
                if (update.getMessage().hasText() && update.getMessage().getText().equals("/language")) {
                    sendLanguageSelection(update.getMessage().getChatId());
                    return;
                }

                // Обрабатываем команду смены языка
                if (update.getMessage().hasText() &&
                        (update.getMessage().getText().equals("/ru") ||
                                update.getMessage().getText().equals("/en"))) {

                    String langCode = update.getMessage().getText().substring(1);
                    handleLanguageChange(update.getMessage().getChatId(), langCode);
                    return;
                }

                // Проверяем наличие WebApp данных в сообщении
                if (update.getMessage().getWebAppData() != null) {
                    miniAppHandler.handleWebAppData(update.getMessage().getWebAppData());
                } else {
                    handleMessage(update.getMessage());
                }
            }
            // Обработка нажатий на кнопки
            else if (update.hasCallbackQuery()) {
                // Проверяем, не выбор ли это языка
                if (update.getCallbackQuery().getData().startsWith("language:")) {
                    String langCode = update.getCallbackQuery().getData().split(":")[1];
                    handleLanguageChange(update.getCallbackQuery().getMessage().getChatId(), langCode);
                    return;
                }

                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Отправляет меню выбора языка
     */
    private void sendLanguageSelection(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🌐 Выберите язык / Select language:");

        // Создаем клавиатуру с выбором языков
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton russianButton = new InlineKeyboardButton();
        russianButton.setText("🇷🇺 Русский");
        russianButton.setCallbackData("language:ru");
        row.add(russianButton);

        InlineKeyboardButton englishButton = new InlineKeyboardButton();
        englishButton.setText("🇬🇧 English");
        englishButton.setCallbackData("language:en");
        row.add(englishButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        execute(message);
    }

    /**
     * Обрабатывает изменение языка
     */
    private void handleLanguageChange(Long chatId, String langCode) throws TelegramApiException {
        // Устанавливаем локаль для пользователя
        localizationService.setUserLocale(chatId, langCode);

        // Отправляем сообщение о смене языка
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if ("ru".equals(langCode)) {
            message.setText("✅ Язык успешно изменен на русский");
        } else {
            message.setText("✅ Language has been changed to English");
        }

        execute(message);

        // Отправляем главное меню на выбранном языке
        sendMainMenu(chatId);
    }

    private void handleMessage(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();

        // Регистрируем пользователя, если он новый
        User user = userService.getOrCreateUser(message.getFrom());

        // Проверяем текущее состояние пользователя
        UserState state = userStates.getOrDefault(chatId, UserState.NONE);

        // Обработка команд - имеет приоритет над состояниями
        if (message.hasText() && message.getText().startsWith("/")) {
            BotApiMethod<?> responseMethod = commandHandler.handleCommand(message);
            execute(responseMethod);
            return;
        }

        // Обработка в зависимости от состояния
        switch (state) {
            case AWAITING_BOT_TOKEN:
                handleBotTokenInput(chatId, message.getText());
                break;
            case AWAITING_BOT_NAME:
                handleBotNameInput(chatId, message.getText());
                break;
            case AWAITING_WELCOME_MESSAGE:
                handleWelcomeMessageInput(chatId, message.getText());
                break;
            // Другие состояния...
            default:
                sendMainMenu(chatId);
                break;
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) throws TelegramApiException {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        // Обработка специальных действий навигации
        if ("back_to_main".equals(callbackData)) {
            sendMainMenu(chatId);
            return;
        } else if ("create_bot".equals(callbackData)) {
            // Устанавливаем состояние ожидания токена
            userStates.put(chatId, UserState.AWAITING_BOT_TOKEN);
        }

        // Передаем обработку основному обработчику
        BotApiMethod<?> responseMethod = callbackQueryHandler.handleCallbackQuery(callbackQuery);
        if (responseMethod != null) {
            execute(responseMethod);
        }
    }

    private void handleBotTokenInput(Long chatId, String tokenText) throws TelegramApiException {
        if (tokenText == null || !tokenText.trim().matches("[0-9]{9,10}:[a-zA-Z0-9_-]{35}")) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(localizationService.getMessage("bot.create.token_invalid", chatId));

            // Добавляем кнопку возврата в главное меню
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText(localizationService.getMessage("common.back", chatId));
            backButton.setCallbackData("back_to_main");
            row.add(backButton);
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            execute(errorMessage);
            return;
        }

        // Сохраняем токен во временное хранилище
        userStates.put(chatId, UserState.AWAITING_BOT_NAME);
        userService.saveTemporaryBotToken(chatId, tokenText);

        SendMessage nameRequest = new SendMessage();
        nameRequest.setChatId(chatId.toString());
        nameRequest.setText(localizationService.getMessage("bot.create.token_accepted", chatId));

        // Добавляем кнопку возврата
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row.add(backButton);
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        nameRequest.setReplyMarkup(markup);

        execute(nameRequest);
    }

    private void handleBotNameInput(Long chatId, String botName) throws TelegramApiException {
        if (botName == null || botName.length() < 3 || botName.length() > 64) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(localizationService.getMessage("bot.create.name_invalid", chatId));

            // Добавляем кнопку возврата
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText(localizationService.getMessage("common.back", chatId));
            backButton.setCallbackData("back_to_main");
            row.add(backButton);
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            execute(errorMessage);
            return;
        }

        // Сохраняем имя во временное хранилище
        userStates.put(chatId, UserState.AWAITING_WELCOME_MESSAGE);
        userService.saveTemporaryBotName(chatId, botName);

        SendMessage welcomeRequest = new SendMessage();
        welcomeRequest.setChatId(chatId.toString());
        welcomeRequest.setText(localizationService.getMessage("bot.create.name_accepted", chatId));

        // Добавляем кнопку возврата
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(localizationService.getMessage("common.back", chatId));
        backButton.setCallbackData("back_to_main");
        row.add(backButton);
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        welcomeRequest.setReplyMarkup(markup);

        execute(welcomeRequest);
    }

    private void handleWelcomeMessageInput(Long chatId, String welcomeMessage) throws TelegramApiException {
        if (welcomeMessage == null || welcomeMessage.length() > 4096) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(localizationService.getMessage("bot.create.welcome_invalid", chatId));

            // Добавляем кнопку возврата
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText(localizationService.getMessage("common.back", chatId));
            backButton.setCallbackData("back_to_main");
            row.add(backButton);
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            execute(errorMessage);
            return;
        }

        // Создаем нового бота
        User user = userService.getUserByTelegramId(chatId);

        try {
            // Проверяем, может ли пользователь создать еще одного бота
            if (!userService.canCreateBot(user)) {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId.toString());
                errorMessage.setText(localizationService.getMessage("bot.create.limit_reached", chatId));

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();

                InlineKeyboardButton premiumButton = new InlineKeyboardButton();
                premiumButton.setText(localizationService.getMessage("premium.buy", chatId));
                premiumButton.setCallbackData("buy_premium");
                row.add(premiumButton);

                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText(localizationService.getMessage("common.back", chatId));
                backButton.setCallbackData("back_to_main");
                row.add(backButton);

                keyboard.add(row);
                markup.setKeyboard(keyboard);
                errorMessage.setReplyMarkup(markup);

                execute(errorMessage);
                userStates.put(chatId, UserState.NONE);
                return;
            }

            // Получаем временные данные и создаем бота
            String token = userService.getTemporaryBotToken(chatId);
            String name = userService.getTemporaryBotName(chatId);

            // Создание бота
            botService.createBot(user, token, name, welcomeMessage);

            // Сброс состояния
            userStates.put(chatId, UserState.NONE);

            // Отправка сообщения об успешном создании
            SendMessage successMessage = new SendMessage();
            successMessage.setChatId(chatId.toString());
            successMessage.setText(localizationService.getMessage("bot.create.success", new Object[]{name}, chatId));

            // Добавляем кнопки для управления ботом
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton manageButton = new InlineKeyboardButton();
            manageButton.setText("⚙️ " + localizationService.getMessage("bot.manage.button", chatId));
            manageButton.setCallbackData("manage_bot:" + botService.getLatestBotId(user));
            row1.add(manageButton);
            keyboard.add(row1);

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton helpButton = new InlineKeyboardButton();
            helpButton.setText("❓ " + localizationService.getMessage("bot.help.button", chatId));
            helpButton.setCallbackData("bot_help");
            row2.add(helpButton);

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText(localizationService.getMessage("common.back", chatId));
            backButton.setCallbackData("back_to_main");
            row2.add(backButton);

            keyboard.add(row2);

            markup.setKeyboard(keyboard);
            successMessage.setReplyMarkup(markup);

            execute(successMessage);
        } catch (Exception e) {
            e.printStackTrace();
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(localizationService.getMessage("bot.create.error", chatId));

            // Добавляем кнопку возврата
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText(localizationService.getMessage("common.back", chatId));
            backButton.setCallbackData("back_to_main");
            row.add(backButton);
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            errorMessage.setReplyMarkup(markup);

            execute(errorMessage);
            userStates.put(chatId, UserState.NONE);
        }
    }

    private void sendMainMenu(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        String welcome = localizationService.getMessage("common.welcome", chatId);
        String createBot = localizationService.getMessage("common.create_bot", chatId);

        message.setText(welcome + "\n\n" + createBot);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопка создания бота
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("🤖 " + localizationService.getMessage("common.create_bot_button", chatId));
        createButton.setCallbackData("create_bot");
        row1.add(createButton);
        keyboard.add(row1);

        // Если у пользователя есть боты, добавляем кнопку "Мои боты"
        User user = userService.getUserByTelegramId(chatId);
        if (botService.userHasBots(user)) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton myBotsButton = new InlineKeyboardButton();
            myBotsButton.setText("📋 " + localizationService.getMessage("common.my_bots", chatId));
            myBotsButton.setCallbackData("my_bots");
            row2.add(myBotsButton);
            keyboard.add(row2);
        }

        // Добавляем кнопки помощи и Premium
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

        // Добавляем кнопку выбора языка
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton langButton = new InlineKeyboardButton();
        langButton.setText("🌐 " + localizationService.getMessage("common.language", chatId));
        langButton.setCallbackData("language_menu");
        row4.add(langButton);
        keyboard.add(row4);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        execute(message);
    }

    // Состояния для FSM (Finite State Machine)
    public enum UserState {
        NONE,
        AWAITING_BOT_TOKEN,
        AWAITING_BOT_NAME,
        AWAITING_WELCOME_MESSAGE,
        AWAITING_CONFIRMATION_MESSAGE,
        // другие состояния...
    }
}