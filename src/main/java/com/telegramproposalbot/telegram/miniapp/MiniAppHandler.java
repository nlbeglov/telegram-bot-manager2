package com.telegramproposalbot.telegram.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppData;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

/**
 * Обработчик данных из Telegram Mini Apps
 */
@Component
public class MiniAppHandler {

    private final BotService botService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    private static final String WEBAPP_URL = "https://yourdomain.com/webapp";

    @Autowired
    public MiniAppHandler(BotService botService, UserService userService, ObjectMapper objectMapper) {
        this.botService = botService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * Создает объект WebAppInfo для использования в кнопках
     */
    public WebAppInfo createWebAppInfo() {
        return new WebAppInfo(WEBAPP_URL);
    }

    /**
     * Создает URL для Mini App с параметрами авторизации
     */
    public String createWebAppUrl(Long userId, Long botId) {
        // В реальном приложении здесь должна быть генерация
        // безопасных параметров аутентификации
        return WEBAPP_URL + "?user_id=" + userId +
                (botId != null ? "&bot_id=" + botId : "");
    }

    /**
     * Обрабатывает данные от Telegram WebApp
     */
    public void handleWebAppData(WebAppData webAppData) {
        try {
            String data = webAppData.getData();
            JsonNode jsonData = objectMapper.readTree(data);

            // Определяем тип действия
            String action = jsonData.get("action").asText();

            switch (action) {
                case "create_bot":
                    handleCreateBot(jsonData);
                    break;
                case "update_bot_settings":
                    handleUpdateBotSettings(jsonData);
                    break;
                case "toggle_bot_active":
                    handleToggleBotActive(jsonData);
                    break;
                case "add_admin":
                    handleAddAdmin(jsonData);
                    break;
                case "add_channel":
                    handleAddChannel(jsonData);
                    break;
                default:
                    System.out.println("Неизвестное действие: " + action);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает создание бота из WebApp
     */
    private void handleCreateBot(JsonNode jsonData) {
        try {
            Long userId = jsonData.get("user_id").asLong();
            String token = jsonData.get("token").asText();
            String name = jsonData.get("name").asText();
            String welcomeMessage = jsonData.get("welcome_message").asText();

            // Получаем пользователя
            var user = userService.getUserByTelegramId(userId);
            if (user == null) {
                System.out.println("Пользователь не найден: " + userId);
                return;
            }

            // Создаем бота
            botService.createBot(user, token, name, welcomeMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает обновление настроек бота
     */
    private void handleUpdateBotSettings(JsonNode jsonData) {
        try {
            Long botId = jsonData.get("bot_id").asLong();
            Long userId = jsonData.get("user_id").asLong();

            // Проверяем права доступа
            if (!botService.isOwnerOrAdmin(botId, userId)) {
                System.out.println("Нет прав доступа к боту: " + botId);
                return;
            }

            // Обновляем настройки
            if (jsonData.has("name")) {
                String name = jsonData.get("name").asText();
                botService.updateBotName(botId, name);
            }

            if (jsonData.has("welcome_message")) {
                String welcomeMessage = jsonData.get("welcome_message").asText();
                botService.updateBotWelcomeMessage(botId, welcomeMessage);
            }

            if (jsonData.has("confirmation_message")) {
                String confirmationMessage = jsonData.get("confirmation_message").asText();
                botService.updateBotConfirmationMessage(botId, confirmationMessage);
            }

            if (jsonData.has("publication_footer")) {
                String publicationFooter = jsonData.get("publication_footer").asText();
                botService.saveBotSetting(botId, "publication_footer", publicationFooter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает включение/выключение бота
     */
    private void handleToggleBotActive(JsonNode jsonData) {
        try {
            Long botId = jsonData.get("bot_id").asLong();
            Long userId = jsonData.get("user_id").asLong();
            boolean active = jsonData.get("active").asBoolean();

            // Проверяем права доступа
            if (!botService.isOwner(botId, userId)) {
                System.out.println("Нет прав доступа к боту: " + botId);
                return;
            }

            // Включаем/выключаем бота
            botService.toggleBotActive(botId, active);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает добавление администратора
     */
    private void handleAddAdmin(JsonNode jsonData) {
        try {
            Long botId = jsonData.get("bot_id").asLong();
            Long userId = jsonData.get("user_id").asLong();
            Long adminId = jsonData.get("admin_id").asLong();
            String username = jsonData.get("username").asText();

            // Проверяем права доступа (только владелец может добавлять админов)
            if (!botService.isOwner(botId, userId)) {
                System.out.println("Нет прав доступа к боту: " + botId);
                return;
            }

            // Добавляем администратора
            botService.addBotAdmin(botId, adminId, username, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает добавление канала
     */
    private void handleAddChannel(JsonNode jsonData) {
        try {
            Long botId = jsonData.get("bot_id").asLong();
            Long userId = jsonData.get("user_id").asLong();
            String channelUsername = jsonData.get("channel_username").asText();

            // Проверяем права доступа
            if (!botService.isOwnerOrAdmin(botId, userId)) {
                System.out.println("Нет прав доступа к боту: " + botId);
                return;
            }

            // Добавляем канал
            botService.addChannel(botId, channelUsername);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}