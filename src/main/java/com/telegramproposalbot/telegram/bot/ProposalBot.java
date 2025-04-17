package com.telegramproposalbot.telegram.bot;

import com.telegramproposalbot.entity.Bot;
import com.telegramproposalbot.entity.BlockedUser;
import com.telegramproposalbot.service.BlockedUserService;
import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProposalBot extends TelegramLongPollingBot {

    private Bot botConfig;
    private final BotService botService;
    private final BlockedUserService blockedUserService;
    private final ChannelService channelService;

    // Кэш сообщений пользователей
    private final Map<Long, Map<Integer, Message>> userMessages = new ConcurrentHashMap<>();

    // Кэш для связи сообщений между пользователями и админами
    private final Map<Integer, MessageLink> messageLinks = new ConcurrentHashMap<>();

    @Autowired
    public ProposalBot(BotService botService,
                       BlockedUserService blockedUserService,
                       ChannelService channelService) {
        super(new DefaultBotOptions(), "placeholder-token"); // Будет заменен при инициализации
        this.botService = botService;
        this.blockedUserService = blockedUserService;
        this.channelService = channelService;
    }

    /**
     * Инициализирует бота с токеном и конфигурацией
     */
    public void initialize(String token, Bot botConfig) {
        this.botConfig = botConfig;

        // Устанавливаем токен через рефлексию
        try {
            Field tokenField = this.getClass().getSuperclass().getDeclaredField("botToken");
            tokenField.setAccessible(true);
            tokenField.set(this, token);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить токен бота", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig != null ? botConfig.getName() : "uninitialized-bot";
    }

    // Метод getBotToken() устарел, но нам все еще нужно его переопределить
    @Deprecated
    @Override
    public String getBotToken() {
        try {
            Field tokenField = this.getClass().getSuperclass().getDeclaredField("botToken");
            tokenField.setAccessible(true);
            return (String) tokenField.get(this);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось получить токен бота", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Проверяем, что бот инициализирован
        if (botConfig == null) {
            System.err.println("Бот не инициализирован!");
            return;
        }

        try {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();

        // Проверяем, является ли отправитель администратором
        boolean isAdmin = botService.isAdmin(botConfig.getId(), message.getFrom().getId());

        if (isAdmin) {
            // Если это ответ на пересланное сообщение от пользователя
            if (message.isReply() && message.getReplyToMessage() != null) {
                handleAdminReply(message);
            } else {
                // Другие команды администратора
                if (message.hasText() && message.getText().startsWith("/")) {
                    handleAdminCommand(message);
                } else {
                    sendAdminHelp(chatId);
                }
            }
        } else {
            // Проверяем, не заблокирован ли пользователь
            if (blockedUserService.isBlocked(botConfig.getId(), message.getFrom().getId())) {
                SendMessage blockedMessage = new SendMessage();
                blockedMessage.setChatId(chatId.toString());
                blockedMessage.setText("⛔ Вы заблокированы администратором и не можете отправлять сообщения.");
                execute(blockedMessage);
                return;
            }

            // Обработка сообщения от обычного пользователя
            handleUserMessage(message);
        }
    }

    private void handleUserMessage(Message message) throws TelegramApiException {
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();

        // Если это первое сообщение пользователя, отправляем приветствие
        if (!userMessages.containsKey(userId)) {
            userMessages.put(userId, new HashMap<>());

            SendMessage welcomeMessage = new SendMessage();
            welcomeMessage.setChatId(chatId.toString());
            welcomeMessage.setText(botConfig.getWelcomeMessage());
            execute(welcomeMessage);
        }

        // Сохраняем сообщение в кэше
        userMessages.get(userId).put(message.getMessageId(), message);

        // Отправляем подтверждение пользователю
        SendMessage confirmationMessage = new SendMessage();
        confirmationMessage.setChatId(chatId.toString());
        confirmationMessage.setText(botConfig.getConfirmationMessage() != null
                ? botConfig.getConfirmationMessage()
                : "✅ Ваше сообщение получено и будет рассмотрено администраторами.");
        execute(confirmationMessage);

        // Пересылаем сообщение всем администраторам
        forwardMessageToAdmins(message);
    }

    private void forwardMessageToAdmins(Message message) throws TelegramApiException {
        List<Long> adminIds = botService.getAdminIds(botConfig.getId());

        for (Long adminId : adminIds) {
            // Пересылаем оригинальное сообщение
            ForwardMessage forwardMessage = new ForwardMessage();
            forwardMessage.setChatId(adminId.toString());
            forwardMessage.setFromChatId(message.getChatId().toString());
            forwardMessage.setMessageId(message.getMessageId());
            Message forwardedMessage = execute(forwardMessage);

            // Отправляем админу кнопки управления
            SendMessage controlMessage = new SendMessage();
            controlMessage.setChatId(adminId.toString());
            controlMessage.setText("👆 Выберите действие для этого сообщения:");

            // Создаем клавиатуру с кнопками
            InlineKeyboardMarkup markup = createAdminKeyboard(message.getFrom().getId(), message.getMessageId());
            controlMessage.setReplyMarkup(markup);

            Message controlMessageSent = execute(controlMessage);

            // Сохраняем связь между сообщениями
            messageLinks.put(controlMessageSent.getMessageId(),
                    new MessageLink(message.getFrom().getId(), message.getMessageId(),
                            forwardedMessage.getMessageId(), controlMessageSent.getMessageId()));
        }
    }

    private InlineKeyboardMarkup createAdminKeyboard(Long userId, Integer messageId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Первый ряд: Одобрить и Редактировать
        List<InlineKeyboardButton> row1 = new ArrayList<>();

        InlineKeyboardButton approveButton = new InlineKeyboardButton();
        approveButton.setText("✅ Опубликовать");
        approveButton.setCallbackData("approve:" + userId + ":" + messageId);
        row1.add(approveButton);

        InlineKeyboardButton editButton = new InlineKeyboardButton();
        editButton.setText("✏️ Редактировать");
        editButton.setCallbackData("edit:" + userId + ":" + messageId);
        row1.add(editButton);

        keyboard.add(row1);

        // Второй ряд: Отклонить и Заблокировать
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        InlineKeyboardButton rejectButton = new InlineKeyboardButton();
        rejectButton.setText("❌ Отклонить");
        rejectButton.setCallbackData("reject:" + userId + ":" + messageId);
        row2.add(rejectButton);

        InlineKeyboardButton blockButton = new InlineKeyboardButton();
        blockButton.setText("⛔ Заблокировать");
        blockButton.setCallbackData("block:" + userId + ":" + messageId);
        row2.add(blockButton);

        keyboard.add(row2);

        // Третий ряд: Дополнительные настройки публикации
        List<InlineKeyboardButton> row3 = new ArrayList<>();

        InlineKeyboardButton scheduleButton = new InlineKeyboardButton();
        scheduleButton.setText("🕒 Отложить");
        scheduleButton.setCallbackData("schedule:" + userId + ":" + messageId);
        row3.add(scheduleButton);

        InlineKeyboardButton silentButton = new InlineKeyboardButton();
        silentButton.setText("🔕 Без звука");
        silentButton.setCallbackData("silent:" + userId + ":" + messageId);
        row3.add(silentButton);

        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) throws TelegramApiException {
        String[] data = callbackQuery.getData().split(":");
        String action = data[0];
        Long userId = Long.parseLong(data[1]);
        Integer messageId = Integer.parseInt(data[2]);

        switch (action) {
            case "approve":
                handleApprove(callbackQuery, userId, messageId);
                break;
            case "edit":
                handleEdit(callbackQuery, userId, messageId);
                break;
            case "reject":
                handleReject(callbackQuery, userId, messageId);
                break;
            case "block":
                handleBlock(callbackQuery, userId, messageId);
                break;
            case "schedule":
                handleSchedule(callbackQuery, userId, messageId);
                break;
            case "silent":
                handleSilent(callbackQuery, userId, messageId);
                break;
            case "publish":
                // Для публикации в выбранный канал
                Long channelId = Long.parseLong(data[3]);
                publishToChannel(callbackQuery, userId, messageId, channelId, false, null);
                break;
            case "publish_silent":
                // Для публикации без звука в выбранный канал
                Long silentChannelId = Long.parseLong(data[3]);
                publishToChannel(callbackQuery, userId, messageId, silentChannelId, true, null);
                break;
            case "schedule_time":
                // Для отложенной публикации
                Long scheduledChannelId = Long.parseLong(data[3]);
                Date scheduledDate = new Date(Long.parseLong(data[4]));
                publishToChannel(callbackQuery, userId, messageId, scheduledChannelId, false, scheduledDate);
                break;
            case "schedule_cancel":
            case "silent_cancel":
                // Возврат к основным действиям
                EditMessageText resetMessage = new EditMessageText();
                resetMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
                resetMessage.setMessageId(callbackQuery.getMessage().getMessageId());
                resetMessage.setText("👆 Выберите действие для этого сообщения:");
                resetMessage.setReplyMarkup(createAdminKeyboard(userId, messageId));
                execute(resetMessage);
                break;
            case "undo_reject":
                // Восстановление отклоненного сообщения
                // Просто обновляем UI, т.к. сообщение может быть удалено из кэша
                EditMessageText undoMessage = new EditMessageText();
                undoMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
                undoMessage.setMessageId(callbackQuery.getMessage().getMessageId());
                undoMessage.setText("👆 Сообщение восстановлено. Выберите действие:");
                undoMessage.setReplyMarkup(createAdminKeyboard(userId, messageId));
                execute(undoMessage);
                break;
            case "unblock":
                // Разблокировка пользователя
                handleUnblock(callbackQuery, userId);
                break;
            default:
                System.out.println("Unknown callback action: " + action);
        }
    }

    private void handleApprove(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        // Получаем список каналов для публикации
        List<Long> channelIds = channelService.getChannelIds(botConfig.getId());

        if (channelIds.isEmpty()) {
            // Если каналы не настроены
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            editMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            editMessage.setText("⚠️ Нет настроенных каналов для публикации. " +
                    "Пожалуйста, настройте каналы в настройках бота.");
            execute(editMessage);
            return;
        }

        // Проверяем, есть ли сообщение в кэше
        Map<Integer, Message> userMsgs = userMessages.getOrDefault(userId, new HashMap<>());
        Message originalMessage = userMsgs.get(messageId);

        if (originalMessage == null) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Сообщение не найдено. Возможно, оно было удалено или слишком старое.");
            execute(errorMessage);
            return;
        }

        // Если каналов несколько, показываем выбор
        if (channelIds.size() > 1) {
            showChannelSelection(callbackQuery, userId, messageId);
            return;
        }

        // Если канал один, публикуем сразу
        Long channelId = channelIds.get(0);
        publishToChannel(callbackQuery, userId, messageId, channelId, false, null);
    }

    private void showChannelSelection(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        List<Map<String, String>> channels = channelService.getChannelsInfo(botConfig.getId());

        EditMessageText selectionMessage = new EditMessageText();
        selectionMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        selectionMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        selectionMessage.setText("📢 Выберите канал для публикации:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Map<String, String> channel : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.get("title"));
            channelButton.setCallbackData("publish:" + userId + ":" + messageId + ":" + channel.get("id"));
            row.add(channelButton);
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);
        selectionMessage.setReplyMarkup(markup);

        execute(selectionMessage);
    }

    private void publishToChannel(CallbackQuery callbackQuery, Long userId, Integer messageId,
                                  Long channelId, boolean silent, Date scheduledDate) throws TelegramApiException {
        Map<Integer, Message> userMsgs = userMessages.getOrDefault(userId, new HashMap<>());
        Message originalMessage = userMsgs.get(messageId);

        if (originalMessage == null) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Сообщение не найдено. Возможно, оно было удалено или слишком старое.");
            execute(errorMessage);
            return;
        }

        try {
            // Публикация сообщения в канал
            // В зависимости от типа сообщения используем разные методы API
            String channelUsername = channelService.getChannelUsername(botConfig.getId(), channelId);

            // Получаем форматирование публикации
            String footer = botService.getBotSetting(botConfig.getId(), "publication_footer", "");

            if (originalMessage.hasText()) {
                SendMessage publishMessage = new SendMessage();
                publishMessage.setChatId(channelUsername);

                // Если есть подпись к публикации, добавляем её
                String text = originalMessage.getText();
                if (!footer.isEmpty()) {
                    text += "\n\n" + footer;
                }

                publishMessage.setText(text);
                publishMessage.setDisableNotification(silent);

                if (scheduledDate != null) {
                    // Для отложенной публикации используем другой метод API
                    // В этом примере просто имитируем
                    botService.schedulePublication(botConfig.getId(), channelId, text, scheduledDate);

                    EditMessageText confirmMessage = new EditMessageText();
                    confirmMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
                    confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
                    confirmMessage.setText("🕒 Публикация запланирована на " + scheduledDate);
                    execute(confirmMessage);
                    return;
                }

                execute(publishMessage);
            } else if (originalMessage.hasPhoto()) {
                // Здесь код для публикации фото
                // ...
            } else if (originalMessage.hasVideo()) {
                // Здесь код для публикации видео
                // ...
            } else if (originalMessage.hasDocument()) {
                // Здесь код для публикации документа
                // ...
            }

            // Уведомляем админа об успехе
            EditMessageText confirmMessage = new EditMessageText();
            confirmMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            confirmMessage.setText("✅ Сообщение успешно опубликовано в канале!");
            execute(confirmMessage);

            // Уведомляем отправителя об одобрении
            SendMessage notifyUser = new SendMessage();
            notifyUser.setChatId(userId.toString());
            notifyUser.setText("🎉 Ваше сообщение было одобрено и опубликовано в канале!");
            execute(notifyUser);

            // Удаляем сообщение из кэша
            userMsgs.remove(messageId);
        } catch (Exception e) {
            e.printStackTrace();

            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Ошибка при публикации: " + e.getMessage());
            execute(errorMessage);
        }
    }

    private void handleEdit(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        Map<Integer, Message> userMsgs = userMessages.getOrDefault(userId, new HashMap<>());
        Message originalMessage = userMsgs.get(messageId);

        if (originalMessage == null) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("❌ Сообщение не найдено. Возможно, оно было удалено или слишком старое.");
            execute(errorMessage);
            return;
        }

        // Переводим админа в режим редактирования
        botService.setAdminState(botConfig.getId(), callbackQuery.getFrom().getId(),
                "EDITING:" + userId + ":" + messageId);

        EditMessageText promptMessage = new EditMessageText();
        promptMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        promptMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        promptMessage.setText("✏️ Пожалуйста, отправьте новый текст для редактирования сообщения.\n\n" +
                "Оригинальное сообщение:\n" + getMessageContent(originalMessage));
        execute(promptMessage);
    }

    private void handleReject(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        Map<Integer, Message> userMsgs = userMessages.getOrDefault(userId, new HashMap<>());

        // Уведомляем админа
        EditMessageText confirmMessage = new EditMessageText();
        confirmMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        confirmMessage.setText("❌ Сообщение отклонено");

        // Добавляем кнопку для возможности вернуть сообщение
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton undoButton = new InlineKeyboardButton();
        undoButton.setText("↩️ Вернуть");
        undoButton.setCallbackData("undo_reject:" + userId + ":" + messageId);
        row.add(undoButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        confirmMessage.setReplyMarkup(markup);

        execute(confirmMessage);

        // Уведомляем пользователя
        SendMessage notifyUser = new SendMessage();
        notifyUser.setChatId(userId.toString());
        notifyUser.setText("❌ К сожалению, ваше сообщение было отклонено администраторами.");
        execute(notifyUser);

        // Удаляем сообщение из кэша
        userMsgs.remove(messageId);
    }

    private void handleBlock(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        // Блокируем пользователя
        blockedUserService.blockUser(botConfig.getId(), userId);

        // Уведомляем админа
        EditMessageText confirmMessage = new EditMessageText();
        confirmMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        confirmMessage.setText("⛔ Пользователь заблокирован и больше не сможет отправлять сообщения.");

        // Добавляем кнопку для разблокировки
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton unblockButton = new InlineKeyboardButton();
        unblockButton.setText("🔓 Разблокировать");
        unblockButton.setCallbackData("unblock:" + userId);
        row.add(unblockButton);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        confirmMessage.setReplyMarkup(markup);

        execute(confirmMessage);

        // Уведомляем пользователя
        SendMessage notifyUser = new SendMessage();
        notifyUser.setChatId(userId.toString());
        notifyUser.setText("⛔ Вы были заблокированы администратором и больше не сможете отправлять сообщения.");
        execute(notifyUser);
    }

    private void handleSchedule(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        // Проверяем подписку пользователя - доступно только для Premium
        if (!botService.hasPremiumFeature(botConfig, "scheduled_posts")) {
            EditMessageText errorMessage = new EditMessageText();
            errorMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
            errorMessage.setMessageId(callbackQuery.getMessage().getMessageId());
            errorMessage.setText("⭐ Отложенная публикация доступна только в Premium подписке");
            execute(errorMessage);
            return;
        }

        // Показываем календарь для выбора даты
        showScheduleCalendar(callbackQuery, userId, messageId);
    }

    private void showScheduleCalendar(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        // Здесь реализация интерфейса выбора даты и времени
        // В реальном приложении это будет более сложная логика

        EditMessageText calendarMessage = new EditMessageText();
        calendarMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        calendarMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        calendarMessage.setText("🗓 Выберите дату публикации:");

        // Создаем простую клавиатуру с несколькими вариантами времени
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Текущая дата для примера
        Calendar cal = Calendar.getInstance();

        // Добавляем кнопки для выбора времени
        for (int i = 1; i <= 3; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            cal.add(Calendar.HOUR, i);
            Date date = cal.getTime();
            String formattedDate = new java.text.SimpleDateFormat("HH:mm").format(date);

            InlineKeyboardButton timeButton = new InlineKeyboardButton();
            timeButton.setText("Через " + i + " ч. (" + formattedDate + ")");
            timeButton.setCallbackData("schedule_time:" + userId + ":" + messageId + ":" + date.getTime());
            row.add(timeButton);

            keyboard.add(row);
        }

        // Добавляем кнопку отмены
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 Назад");
        backButton.setCallbackData("schedule_cancel:" + userId + ":" + messageId);
        backRow.add(backButton);
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        calendarMessage.setReplyMarkup(markup);

        execute(calendarMessage);
    }

    private void handleSilent(CallbackQuery callbackQuery, Long userId, Integer messageId) throws TelegramApiException {
        // Показываем список каналов для публикации без звука
        List<Map<String, String>> channels = channelService.getChannelsInfo(botConfig.getId());

        EditMessageText selectionMessage = new EditMessageText();
        selectionMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        selectionMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        selectionMessage.setText("🔕 Выберите канал для публикации без звука:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Map<String, String> channel : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.get("title"));
            // Используем специальный формат для отправки без звука
            channelButton.setCallbackData("publish_silent:" + userId + ":" + messageId + ":" + channel.get("id"));
            row.add(channelButton);
            keyboard.add(row);
        }

        // Добавляем кнопку отмены
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 Назад");
        backButton.setCallbackData("silent_cancel:" + userId + ":" + messageId);
        backRow.add(backButton);
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        selectionMessage.setReplyMarkup(markup);

        execute(selectionMessage);
    }

    private void handleUnblock(CallbackQuery callbackQuery, Long userId) throws TelegramApiException {
        // Разблокируем пользователя
        blockedUserService.unblockUser(botConfig.getId(), userId);

        // Уведомляем админа
        EditMessageText confirmMessage = new EditMessageText();
        confirmMessage.setChatId(callbackQuery.getMessage().getChatId().toString());
        confirmMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        confirmMessage.setText("✅ Пользователь разблокирован и снова может отправлять сообщения.");
        execute(confirmMessage);

        // Уведомляем пользователя
        SendMessage notifyUser = new SendMessage();
        notifyUser.setChatId(userId.toString());
        notifyUser.setText("✅ Вы были разблокированы администратором и снова можете отправлять сообщения.");
        execute(notifyUser);
    }

    private void handleAdminCommand(Message message) throws TelegramApiException {
        String command = message.getText();
        Long chatId = message.getChatId();

        if ("/help".equals(command)) {
            sendAdminHelp(chatId);
        } else if ("/stats".equals(command)) {
            sendBotStats(chatId);
        } else if ("/settings".equals(command)) {
            sendBotSettings(chatId);
        } else if (command.startsWith("/channel_add")) {
            handleAddChannel(chatId, command);
        } else if (command.startsWith("/channel_remove")) {
            handleRemoveChannel(chatId, command);
        } else if ("/channels".equals(command)) {
            sendChannelsList(chatId);
        } else if ("/blocklist".equals(command)) {
            sendBlockedUsersList(chatId);
        } else {
            SendMessage unknownCommand = new SendMessage();
            unknownCommand.setChatId(chatId.toString());
            unknownCommand.setText("❓ Неизвестная команда. Отправьте /help для просмотра доступных команд.");
            execute(unknownCommand);
        }
    }

    private void sendAdminHelp(Long chatId) throws TelegramApiException {
        SendMessage helpMessage = new SendMessage();
        helpMessage.setChatId(chatId.toString());
        helpMessage.setText("📚 *Справка по командам администратора*\n\n" +
                "/help - показать эту справку\n" +
                "/stats - статистика бота\n" +
                "/settings - настройки бота\n" +
                "/channels - список подключенных каналов\n" +
                "/channel_add @username - добавить канал\n" +
                "/channel_remove @username - удалить канал\n" +
                "/blocklist - список заблокированных пользователей\n\n" +
                "Для публикации, редактирования или удаления сообщений используйте кнопки под пересланными сообщениями.");
        helpMessage.enableMarkdown(true);
        execute(helpMessage);
    }

    private void sendBotStats(Long chatId) throws TelegramApiException {
        // Получаем статистику из сервиса
        Map<String, Integer> stats = botService.getBotStats(botConfig.getId());

        SendMessage statsMessage = new SendMessage();
        statsMessage.setChatId(chatId.toString());
        statsMessage.setText("📊 *Статистика бота*\n\n" +
                "📨 Сообщений получено: " + stats.getOrDefault("messages_received", 0) + "\n" +
                "📢 Сообщений опубликовано: " + stats.getOrDefault("messages_published", 0) + "\n" +
                "❌ Сообщений отклонено: " + stats.getOrDefault("messages_rejected", 0) + "\n" +
                "⛔ Заблокировано пользователей: " + stats.getOrDefault("users_blocked", 0) + "\n" +
                "📆 Бот создан: " + botConfig.getCreatedAt().toString());
        statsMessage.enableMarkdown(true);
        execute(statsMessage);
    }

    private void sendBotSettings(Long chatId) throws TelegramApiException {
        // Получаем настройки бота
        Map<String, String> settings = botService.getBotSettingsMap(botConfig.getId());
        boolean isPremium = botService.hasPremiumSubscription(botConfig.getOwnerId());

        StringBuilder settingsText = new StringBuilder();
        settingsText.append("⚙️ *Настройки бота*\n\n");
        settingsText.append("🤖 Имя бота: ").append(botConfig.getName()).append("\n");
        settingsText.append("💬 Приветственное сообщение: ").append(botConfig.getWelcomeMessage()).append("\n");
        settingsText.append("✅ Сообщение о получении: ").append(botConfig.getConfirmationMessage()).append("\n");

        // Добавляем информацию о Premium функциях
        settingsText.append("\n💎 *Premium функции*\n");
        settingsText.append(isPremium ? "✅ Активирована" : "❌ Не активирована").append("\n");

        if (isPremium) {
            settingsText.append("⏰ Отложенные публикации: ✅\n");
            settingsText.append("🖼️ Кастомная аватарка: ✅\n");
            settingsText.append("🏷️ Без ссылки на основного бота: ✅\n");
        } else {
            settingsText.append("⏰ Отложенные публикации: ❌\n");
            settingsText.append("🖼️ Кастомная аватарка: ❌\n");
            settingsText.append("🏷️ Без ссылки на основного бота: ❌\n");
        }

        SendMessage settingsMessage = new SendMessage();
        settingsMessage.setChatId(chatId.toString());
        settingsMessage.setText(settingsText.toString());
        settingsMessage.enableMarkdown(true);

        // Добавляем кнопки для изменения настроек
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton welcomeButton = new InlineKeyboardButton();
        welcomeButton.setText("Изменить приветствие");
        welcomeButton.setCallbackData("edit_welcome");
        row1.add(welcomeButton);
        keyboard.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("Изменить сообщение о получении");
        confirmButton.setCallbackData("edit_confirm");
        row2.add(confirmButton);
        keyboard.add(row2);

        if (!isPremium) {
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            InlineKeyboardButton premiumButton = new InlineKeyboardButton();
            premiumButton.setText("💎 Получить Premium");
            premiumButton.setCallbackData("upgrade_premium");
            row3.add(premiumButton);
            keyboard.add(row3);
        }

        markup.setKeyboard(keyboard);
        settingsMessage.setReplyMarkup(markup);

        execute(settingsMessage);
    }

    private void handleAddChannel(Long chatId, String command) throws TelegramApiException {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Пожалуйста, укажите username канала: /channel_add @username");
            execute(errorMessage);
            return;
        }

        String channelUsername = parts[1].trim();
        // Убираем @ из имени, если есть
        if (channelUsername.startsWith("@")) {
            channelUsername = channelUsername.substring(1);
        }

        try {
            boolean added = channelService.addChannel(botConfig.getId(), channelUsername);

            SendMessage resultMessage = new SendMessage();
            resultMessage.setChatId(chatId.toString());

            if (added) {
                resultMessage.setText("✅ Канал @" + channelUsername + " успешно добавлен!\n\n" +
                        "Убедитесь, что этот бот добавлен в канал как администратор " +
                        "с правами публикации сообщений.");
            } else {
                resultMessage.setText("❌ Не удалось добавить канал. Возможно, он уже добавлен " +
                        "или указано неверное имя пользователя.");
            }

            execute(resultMessage);
        } catch (Exception e) {
            e.printStackTrace();
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка при добавлении канала: " + e.getMessage());
            execute(errorMessage);
        }
    }

    private void handleRemoveChannel(Long chatId, String command) throws TelegramApiException {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Пожалуйста, укажите username канала: /channel_remove @username");
            execute(errorMessage);
            return;
        }

        String channelUsername = parts[1].trim();
        // Убираем @ из имени, если есть
        if (channelUsername.startsWith("@")) {
            channelUsername = channelUsername.substring(1);
        }

        try {
            boolean removed = channelService.removeChannel(botConfig.getId(), channelUsername);

            SendMessage resultMessage = new SendMessage();
            resultMessage.setChatId(chatId.toString());

            if (removed) {
                resultMessage.setText("✅ Канал @" + channelUsername + " успешно удален из списка!");
            } else {
                resultMessage.setText("❌ Не удалось удалить канал. Возможно, он не добавлен " +
                        "или указано неверное имя пользователя.");
            }

            execute(resultMessage);
        } catch (Exception e) {
            e.printStackTrace();
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка при удалении канала: " + e.getMessage());
            execute(errorMessage);
        }
    }

    private void sendChannelsList(Long chatId) throws TelegramApiException {
        List<Map<String, String>> channels = channelService.getChannelsInfo(botConfig.getId());

        if (channels.isEmpty()) {
            SendMessage noChannelsMessage = new SendMessage();
            noChannelsMessage.setChatId(chatId.toString());
            noChannelsMessage.setText("❌ У бота нет добавленных каналов.\n\n" +
                    "Используйте команду /channel_add @username чтобы добавить канал.\n" +
                    "Не забудьте добавить бота администратором канала!");
            execute(noChannelsMessage);
            return;
        }

        StringBuilder channelsText = new StringBuilder();
        channelsText.append("📢 *Список добавленных каналов*\n\n");

        for (int i = 0; i < channels.size(); i++) {
            Map<String, String> channel = channels.get(i);
            channelsText.append(i + 1).append(". @").append(channel.get("username"))
                    .append(" (").append(channel.get("title")).append(")\n");
        }

        channelsText.append("\nДля добавления канала используйте команду /channel_add @username\n");
        channelsText.append("Для удаления канала используйте команду /channel_remove @username");

        SendMessage channelsMessage = new SendMessage();
        channelsMessage.setChatId(chatId.toString());
        channelsMessage.setText(channelsText.toString());
        channelsMessage.enableMarkdown(true);
        execute(channelsMessage);
    }

    private void sendBlockedUsersList(Long chatId) throws TelegramApiException {
        List<BlockedUser> blockedUsers = blockedUserService.getBlockedUsers(botConfig.getId());

        if (blockedUsers.isEmpty()) {
            SendMessage noBlockedMessage = new SendMessage();
            noBlockedMessage.setChatId(chatId.toString());
            noBlockedMessage.setText("✅ Список заблокированных пользователей пуст.");
            execute(noBlockedMessage);
            return;
        }

        StringBuilder blockedText = new StringBuilder();
        blockedText.append("⛔ *Список заблокированных пользователей*\n\n");

        for (int i = 0; i < blockedUsers.size(); i++) {
            BlockedUser user = blockedUsers.get(i);
            blockedText.append(i + 1).append(". ID: ").append(user.getTelegramId())
                    .append(" (заблокирован: ").append(user.getBlockedAt().toString()).append(")\n");
        }

        blockedText.append("\nДля разблокировки используйте кнопки ниже:");

        SendMessage blockedMessage = new SendMessage();
        blockedMessage.setChatId(chatId.toString());
        blockedMessage.setText(blockedText.toString());
        blockedMessage.enableMarkdown(true);

        // Добавляем кнопки для разблокировки
        // Если список слишком большой, добавляем только первые 5 пользователей
        if (!blockedUsers.isEmpty()) {
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            int limit = Math.min(blockedUsers.size(), 5);
            for (int i = 0; i < limit; i++) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton unblockButton = new InlineKeyboardButton();
                unblockButton.setText("🔓 Разблокировать ID: " + blockedUsers.get(i).getTelegramId());
                unblockButton.setCallbackData("unblock:" + blockedUsers.get(i).getTelegramId());
                row.add(unblockButton);
                keyboard.add(row);
            }

            markup.setKeyboard(keyboard);
            blockedMessage.setReplyMarkup(markup);
        }

        execute(blockedMessage);
    }

    private void handleAdminReply(Message message) throws TelegramApiException {
        // Получаем ID сообщения, на которое отвечает админ
        Integer repliedToMessageId = message.getReplyToMessage().getMessageId();

        // Ищем связь в messageLinks
        MessageLink link = null;
        for (MessageLink ml : messageLinks.values()) {
            if (ml.getAdminForwardedMessageId().equals(repliedToMessageId)) {
                link = ml;
                break;
            }
        }

        if (link == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(message.getChatId().toString());
            errorMessage.setText("❌ Не удалось найти информацию о сообщении пользователя.");
            execute(errorMessage);
            return;
        }

        // Отправляем ответ пользователю
        SendMessage replyToUser = new SendMessage();
        replyToUser.setChatId(link.getUserId().toString());
        replyToUser.setText("👨‍💼 *Ответ от администратора*:\n\n" + message.getText());
        replyToUser.enableMarkdown(true);

        try {
            execute(replyToUser);

            // Уведомляем админа, что ответ отправлен
            SendMessage confirmationToAdmin = new SendMessage();
            confirmationToAdmin.setChatId(message.getChatId().toString());
            confirmationToAdmin.setText("✅ Ваш ответ отправлен пользователю.");
            execute(confirmationToAdmin);
        } catch (Exception e) {
            e.printStackTrace();
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(message.getChatId().toString());
            errorMessage.setText("❌ Ошибка при отправке ответа пользователю: " + e.getMessage());
            execute(errorMessage);
        }
    }

    // Вспомогательные методы
    private String getMessageContent(Message message) {
        if (message.hasText()) {
            return message.getText();
        } else if (message.hasPhoto()) {
            return "[Фотография]" + (message.getCaption() != null ? ": " + message.getCaption() : "");
        } else if (message.hasVideo()) {
            return "[Видео]" + (message.getCaption() != null ? ": " + message.getCaption() : "");
        } else if (message.hasDocument()) {
            return "[Документ: " + message.getDocument().getFileName() + "]" +
                    (message.getCaption() != null ? ": " + message.getCaption() : "");
        } else if (message.hasSticker()) {
            return "[Стикер]";
        } else if (message.hasVoice()) {
            return "[Голосовое сообщение]";
        } else if (message.hasVideoNote()) {
            return "[Видеосообщение]";
        }
        return "[Неизвестный тип сообщения]";
    }

    // Вложенный класс для хранения связей между сообщениями
    private static class MessageLink {
        private final Long userId;
        private final Integer userMessageId;
        private final Integer adminForwardedMessageId;
        private final Integer adminControlMessageId;

        public MessageLink(Long userId, Integer userMessageId,
                           Integer adminForwardedMessageId, Integer adminControlMessageId) {
            this.userId = userId;
            this.userMessageId = userMessageId;
            this.adminForwardedMessageId = adminForwardedMessageId;
            this.adminControlMessageId = adminControlMessageId;
        }

        public Long getUserId() {
            return userId;
        }

        public Integer getUserMessageId() {
            return userMessageId;
        }

        public Integer getAdminForwardedMessageId() {
            return adminForwardedMessageId;
        }

        public Integer getAdminControlMessageId() {
            return adminControlMessageId;
        }
    }
}