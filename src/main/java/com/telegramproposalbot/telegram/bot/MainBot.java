package com.telegramproposalbot.telegram.bot;

import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.service.BotService;
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

    // Хранение состояний бесед с пользователями
    private final Map<Long, UserState> userStates = new HashMap<>();

    @Value("${telegram.bot.main.username}")
    private String botUsername;

    @Value("${telegram.bot.main.token}")
    private String botToken;

    public MainBot(UserService userService, BotService botService,
                   CommandHandler commandHandler, CallbackQueryHandler callbackQueryHandler,
                   MiniAppHandler miniAppHandler) {
        super(new DefaultBotOptions(), "placeholder-token"); // Будет обновлен после инициализации
        this.userService = userService;
        this.botService = botService;
        this.commandHandler = commandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.miniAppHandler = miniAppHandler;
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
                // Проверяем наличие WebApp данных в сообщении
                if (update.getMessage().getWebAppData() != null) {
                    miniAppHandler.handleWebAppData(update.getMessage().getWebAppData());
                } else {
                    handleMessage(update.getMessage());
                }
            }
            // Обработка нажатий на кнопки
            else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();

        // Регистрируем пользователя, если он новый
        User user = userService.getOrCreateUser(message.getFrom());

        // Проверяем текущее состояние пользователя
        UserState state = userStates.getOrDefault(chatId, UserState.NONE);

        // Обработка команд
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
        BotApiMethod<?> responseMethod = callbackQueryHandler.handleCallbackQuery(callbackQuery);
        if (responseMethod != null) {
            execute(responseMethod);
        }
    }

    private void handleBotTokenInput(Long chatId, String tokenText) throws TelegramApiException {
        if (tokenText == null || !tokenText.trim().matches("[0-9]{9}:[a-zA-Z0-9_-]{35}")) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Введенный токен не соответствует формату токена бота Telegram.\n" +
                    "Пожалуйста, введите действительный токен, полученный от @BotFather.");
            execute(errorMessage);
            return;
        }

        // Сохраняем токен во временное хранилище
        userStates.put(chatId, UserState.AWAITING_BOT_NAME);
        userService.saveTemporaryBotToken(chatId, tokenText);

        SendMessage nameRequest = new SendMessage();
        nameRequest.setChatId(chatId.toString());
        nameRequest.setText("✅ Токен принят!\n\nТеперь введите название для вашего бота-предложки:");
        execute(nameRequest);
    }

    private void handleBotNameInput(Long chatId, String botName) throws TelegramApiException {
        if (botName == null || botName.length() < 3 || botName.length() > 64) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Название бота должно содержать от 3 до 64 символов.\n" +
                    "Пожалуйста, введите другое название.");
            execute(errorMessage);
            return;
        }

        // Сохраняем имя во временное хранилище
        userStates.put(chatId, UserState.AWAITING_WELCOME_MESSAGE);
        userService.saveTemporaryBotName(chatId, botName);

        SendMessage welcomeRequest = new SendMessage();
        welcomeRequest.setChatId(chatId.toString());
        welcomeRequest.setText("✅ Название принято!\n\nТеперь введите приветственное сообщение, " +
                "которое будет показываться пользователям при первом обращении к боту:");
        execute(welcomeRequest);
    }

    private void handleWelcomeMessageInput(Long chatId, String welcomeMessage) throws TelegramApiException {
        if (welcomeMessage == null || welcomeMessage.length() > 4096) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Приветственное сообщение слишком длинное (максимум 4096 символов).\n" +
                    "Пожалуйста, сократите сообщение.");
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
                errorMessage.setText("❌ На бесплатном тарифе вы можете создать только одного бота.\n" +
                        "Приобретите Premium подписку для создания дополнительных ботов.");

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();

                InlineKeyboardButton premiumButton = new InlineKeyboardButton();
                premiumButton.setText("💎 Купить Premium");
                premiumButton.setCallbackData("buy_premium");
                row.add(premiumButton);

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
            successMessage.setText("🎉 Поздравляем! Ваш бот-предложка успешно создан!\n\n" +
                    "Имя бота: " + name + "\n" +
                    "Теперь вы можете настроить его через меню управления.");

            // Добавляем кнопки для управления ботом
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton manageButton = new InlineKeyboardButton();
            manageButton.setText("⚙️ Управление ботом");
            manageButton.setCallbackData("manage_bot:" + botService.getLatestBotId(user));
            row1.add(manageButton);
            keyboard.add(row1);

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton helpButton = new InlineKeyboardButton();
            helpButton.setText("❓ Как пользоваться");
            helpButton.setCallbackData("bot_help");
            row2.add(helpButton);
            keyboard.add(row2);

            markup.setKeyboard(keyboard);
            successMessage.setReplyMarkup(markup);

            execute(successMessage);
        } catch (Exception e) {
            e.printStackTrace();
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Произошла ошибка при создании бота. Пожалуйста, попробуйте позже или " +
                    "убедитесь, что введенный токен действителен и бот не зарегистрирован в системе.");
            execute(errorMessage);
            userStates.put(chatId, UserState.NONE);
        }
    }

    private void sendMainMenu(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👋 Добро пожаловать в менеджер ботов-предложек!\n\n" +
                "Создайте своего бота для приема предложений в ваш канал. " +
                "Что вы хотите сделать?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("🤖 Создать нового бота");
        createButton.setCallbackData("create_bot");
        row1.add(createButton);
        keyboard.add(row1);

        User user = userService.getUserByTelegramId(chatId);
        if (botService.userHasBots(user)) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton myBotsButton = new InlineKeyboardButton();
            myBotsButton.setText("📋 Мои боты");
            myBotsButton.setCallbackData("my_bots");
            row2.add(myBotsButton);
            keyboard.add(row2);
        }

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Помощь");
        helpButton.setCallbackData("help");
        row3.add(helpButton);

        InlineKeyboardButton premiumButton = new InlineKeyboardButton();
        premiumButton.setText("💎 Premium");
        premiumButton.setCallbackData("premium_info");
        row3.add(premiumButton);
        keyboard.add(row3);

        // Если доступен Telegram Mini Apps, добавляем соответствующую кнопку
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton webAppButton = new InlineKeyboardButton();
        webAppButton.setText("🌐 Открыть панель управления");
        webAppButton.setWebApp(new org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo(
                "https://yourdomain.com/webapp"
        ));
        row4.add(webAppButton);
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