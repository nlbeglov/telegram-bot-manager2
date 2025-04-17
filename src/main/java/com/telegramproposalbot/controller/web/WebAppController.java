package com.telegramproposalbot.controller.web;

import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.ChannelService;
import com.telegramproposalbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для обработки запросов к WebApp
 */
@Controller
@RequestMapping("/webapp")
public class WebAppController {

    private final UserService userService;
    private final BotService botService;
    @Autowired
    private final ChannelService channelService;

    @Autowired
    public WebAppController(UserService userService, BotService botService, ChannelService channelService) {
        this.userService = userService;
        this.botService = botService;
        this.channelService = channelService;
    }

    /**
     * Возвращает страницу WebApp
     */
    @GetMapping
    public String getWebApp() {
        return "webapp/index";
    }

    /**
     * Проверяет авторизацию пользователя и возвращает данные для WebApp
     */
    @GetMapping("/auth")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> authenticateUser(@RequestParam("user_id") Long userId,
                                                                @RequestParam("auth") String authToken) {
        User user = userService.getUserByTelegramId(userId);

        Map<String, Object> response = new HashMap<>();

        if (user == null) {
            response.put("success", false);
            response.put("error", "User not found");
            return ResponseEntity.badRequest().body(response);
        }

        // В реальном приложении здесь нужно проверить authToken
        // Например, по временному токену, сохраненному в сессии

        response.put("success", true);
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    /**
     * Возвращает список ботов пользователя
     */
    @GetMapping("/bots")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserBots(@RequestParam("user_id") Long userId) {
        User user = userService.getUserByTelegramId(userId);

        Map<String, Object> response = new HashMap<>();

        if (user == null) {
            response.put("success", false);
            response.put("error", "User not found");
            return ResponseEntity.badRequest().body(response);
        }

        var bots = botService.getUserBots(user.getId());
        response.put("success", true);
        response.put("bots", bots);
        return ResponseEntity.ok(response);
    }

    /**
     * Возвращает подробную информацию о конкретном боте
     */
    @GetMapping("/bots/{botId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBotDetails(@RequestParam("user_id") Long userId,
                                                             @PathVariable Long botId) {
        User user = userService.getUserByTelegramId(userId);

        Map<String, Object> response = new HashMap<>();

        if (user == null) {
            response.put("success", false);
            response.put("error", "User not found");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            var bot = botService.getBotById(botId);

            // Проверяем права доступа
            if (!bot.getOwnerId().equals(user.getId()) && !botService.isAdmin(botId, userId)) {
                response.put("success", false);
                response.put("error", "Access denied");
                return ResponseEntity.status(403).body(response);
            }

            response.put("success", true);
            response.put("bot", bot);

            // Получаем дополнительную информацию
            response.put("settings", botService.getBotSettingsMap(botId));
            response.put("stats", botService.getBotStats(botId));
            response.put("channels", channelService.getChannelsInfo(botId));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}