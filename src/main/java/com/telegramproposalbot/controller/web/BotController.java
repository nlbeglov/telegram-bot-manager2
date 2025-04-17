package com.telegramproposalbot.controller.web;

import com.telegramproposalbot.dto.BotCreationRequest;
import com.telegramproposalbot.dto.BotDTO;
import com.telegramproposalbot.dto.BotSettingsDTO;
import com.telegramproposalbot.entity.Bot;
import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.exception.BotCreationException;
import com.telegramproposalbot.exception.BotNotFoundException;
import com.telegramproposalbot.exception.UnauthorizedException;
import com.telegramproposalbot.service.BotService;
import com.telegramproposalbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService botService;
    private final UserService userService;

    @Autowired
    public BotController(BotService botService, UserService userService) {
        this.botService = botService;
        this.userService = userService;
    }

    /**
     * Получение списка ботов пользователя
     */
    @GetMapping
    public ResponseEntity<List<BotDTO>> getUserBots(@RequestHeader("X-Telegram-Auth") String telegramAuth) {
        try {
            // Аутентификация пользователя на основе заголовка
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            List<Bot> bots = botService.getUserBots(user.getId());
            List<BotDTO> botDTOs = bots.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(botDTOs);
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при получении списка ботов");
        }
    }

    /**
     * Получение информации о конкретном боте
     */
    @GetMapping("/{botId}")
    public ResponseEntity<BotDTO> getBotDetails(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @PathVariable Long botId) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            Bot bot = botService.getBotById(botId);

            // Проверяем, владеет ли пользователь этим ботом или является админом
            if (!bot.getOwnerId().equals(user.getId()) && !botService.isAdmin(botId, telegramId)) {
                throw new UnauthorizedException("У вас нет доступа к этому боту");
            }

            return ResponseEntity.ok(convertToDTO(bot));
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (BotNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при получении информации о боте");
        }
    }

    /**
     * Создание нового бота
     */
    @PostMapping
    public ResponseEntity<BotDTO> createBot(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @RequestBody BotCreationRequest request) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            // Создание бота
            Bot bot = botService.createBot(user, request.getToken(),
                    request.getName(), request.getWelcomeMessage());

            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(bot));
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (BotCreationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при создании бота");
        }
    }

    /**
     * Обновление настроек бота
     */
    @PutMapping("/{botId}")
    public ResponseEntity<BotDTO> updateBotSettings(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @PathVariable Long botId,
            @RequestBody BotSettingsDTO settings) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            Bot bot = botService.getBotById(botId);

            // Проверяем, владеет ли пользователь этим ботом
            if (!bot.getOwnerId().equals(user.getId())) {
                throw new UnauthorizedException("Вы не являетесь владельцем этого бота");
            }

            // Обновление настроек
            bot = botService.updateBotSettings(botId, settings);

            return ResponseEntity.ok(convertToDTO(bot));
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (BotNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при обновлении настроек бота");
        }
    }

    /**
     * Активация/деактивация бота
     */
    @PutMapping("/{botId}/active")
    public ResponseEntity<BotDTO> toggleBotActive(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @PathVariable Long botId,
            @RequestParam boolean active) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            Bot bot = botService.getBotById(botId);

            // Проверяем, владеет ли пользователь этим ботом
            if (!bot.getOwnerId().equals(user.getId())) {
                throw new UnauthorizedException("Вы не являетесь владельцем этого бота");
            }

            // Активация/деактивация
            bot = botService.toggleBotActive(botId, active);

            return ResponseEntity.ok(convertToDTO(bot));
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (BotNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при изменении статуса бота");
        }
    }

    /**
     * Получение статистики бота
     */
    @GetMapping("/{botId}/stats")
    public ResponseEntity<Map<String, Integer>> getBotStats(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @PathVariable Long botId) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            // Проверяем, владеет ли пользователь этим ботом или является админом
            if (!botService.isOwnerOrAdmin(botId, telegramId)) {
                throw new UnauthorizedException("У вас нет доступа к статистике этого бота");
            }

            // Получение статистики
            Map<String, Integer> stats = botService.getBotStats(botId);

            return ResponseEntity.ok(stats);
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при получении статистики бота");
        }
    }
    /**
     * Удаление бота
     */
    @DeleteMapping("/{botId}")
    public ResponseEntity<Void> deleteBot(
            @RequestHeader("X-Telegram-Auth") String telegramAuth,
            @PathVariable Long botId) {
        try {
            // Аутентификация пользователя
            Long telegramId = validateAndGetTelegramId(telegramAuth);
            User user = userService.getUserByTelegramId(telegramId);

            if (user == null) {
                throw new UnauthorizedException("Пользователь не найден");
            }

            Bot bot = botService.getBotById(botId);

            // Проверяем, владеет ли пользователь этим ботом
            if (!bot.getOwnerId().equals(user.getId())) {
                throw new UnauthorizedException("Вы не являетесь владельцем этого бота");
            }

            // Удаление бота
            botService.deleteBot(botId);

            return ResponseEntity.noContent().build();
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (BotNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при удалении бота");
        }
    }

    // Вспомогательные методы

    /**
     * Конвертирует сущность Bot в DTO
     */
    private BotDTO convertToDTO(Bot bot) {
        BotDTO dto = new BotDTO();
        dto.setId(bot.getId());
        dto.setName(bot.getName());
        dto.setActive(bot.isActive());
        dto.setWelcomeMessage(bot.getWelcomeMessage());
        dto.setConfirmationMessage(bot.getConfirmationMessage());
        dto.setCreatedAt(bot.getCreatedAt());
        dto.setAvatarUrl(bot.getAvatarUrl());

        // Добавляем настройки
        Map<String, String> settings = botService.getBotSettingsMap(bot.getId());
        dto.setSettings(settings);

        // Добавляем статистику
        Map<String, Integer> stats = botService.getBotStats(bot.getId());
        dto.setStats(stats);

        return dto;
    }

    /**
     * Проверяет заголовок аутентификации и извлекает telegramId
     */
    private Long validateAndGetTelegramId(String telegramAuth) throws UnauthorizedException {
        // В реальном приложении здесь будет проверка данных аутентификации
        // Для примера просто извлекаем ID из заголовка
        try {
            return Long.parseLong(telegramAuth);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Некорректный формат данных аутентификации");
        }
    }
}