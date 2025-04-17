package com.telegramproposalbot.service;

import com.telegramproposalbot.dto.BotSettingsDTO;
import com.telegramproposalbot.entity.Bot;
import com.telegramproposalbot.entity.BotSettings;
import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.exception.BotCreationException;
import com.telegramproposalbot.exception.BotNotFoundException;
import com.telegramproposalbot.exception.UnauthorizedException;
import com.telegramproposalbot.repository.BotRepository;
import com.telegramproposalbot.repository.BotSettingsRepository;
import com.telegramproposalbot.telegram.bot.ProposalBot;
import jakarta.activation.DataHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;


@Service
public class BotService {

    private final BotRepository botRepository;
    private final BotSettingsRepository botSettingsRepository;
    private final BotAdminService botAdminService;
    private final TelegramBotsApi telegramBotsApi;
    private final SubscriptionService subscriptionService;
    private final ChannelService channelService;
    private final BlockedUserService blockedUserService;

    @Autowired
    private ApplicationContext applicationContext;


    // Кэш запущенных ботов
    private final Map<Long, ProposalBot> runningBots = new ConcurrentHashMap<>();

    // Кэш состояний администраторов
    private final Map<String, String> adminStates = new ConcurrentHashMap<>();

    @Autowired
    public BotService(BotRepository botRepository,
                      BotSettingsRepository botSettingsRepository,
                      BotAdminService botAdminService,
                      TelegramBotsApi telegramBotsApi,
                      SubscriptionService subscriptionService,
                      ChannelService channelService,
                      BlockedUserService blockedUserService,
                      ApplicationContext applicationContext) {
        this.botRepository = botRepository;
        this.botSettingsRepository = botSettingsRepository;
        this.botAdminService = botAdminService;
        this.telegramBotsApi = telegramBotsApi;
        this.subscriptionService = subscriptionService;
        this.channelService = channelService;
        this.blockedUserService = blockedUserService;
        this.applicationContext = applicationContext;
    }

    /**
     * Создает нового бота и регистрирует его в Telegram
     */
    @Transactional
    public Bot createBot(User owner, String token, String name, String welcomeMessage) throws BotCreationException {
        // Проверяем, может ли пользователь создать бота
        if (!subscriptionService.canCreateBot(owner)) {
            throw new BotCreationException("Достигнут лимит ботов для текущей подписки");
        }

        // Проверяем, что токен еще не зарегистрирован
        if (botRepository.existsByToken(token)) {
            throw new BotCreationException("Бот с таким токеном уже зарегистрирован");
        }

        try {
            // Создаем запись о боте в БД
            Bot bot = new Bot();
            bot.setToken(token);
            bot.setName(name);
            bot.setOwnerId(owner.getId());
            bot.setWelcomeMessage(welcomeMessage);
            bot.setConfirmationMessage("✅ Ваше сообщение получено и будет рассмотрено администраторами.");
            bot.setActive(true);
            bot.setCreatedAt(new Date());

            // Сохраняем бота
            Bot savedBot = botRepository.save(bot);

            // Добавляем владельца как главного администратора
            botAdminService.addAdmin(savedBot.getId(), owner.getTelegramId(), owner.getUsername(), true);

            // Добавляем базовые настройки
            createDefaultSettings(savedBot.getId());

            // Создаем и запускаем экземпляр бота
            startBot(savedBot);

            return savedBot;
        } catch (Exception e) {
            throw new BotCreationException("Ошибка при создании бота: " + e.getMessage(), e);
        }
    }

    /**
     * Запускает бота в Telegram
     */
    public void startBot(Bot bot) throws TelegramApiException {
        // Проверяем, запущен ли уже этот бот
        if (runningBots.containsKey(bot.getId())) {
            return;
        }

        // Создаем экземпляр бота
        // Используем конструктор с зависимостями через Spring
        ProposalBot proposalBot = applicationContext.getBean(ProposalBot.class);

        // Инициализируем бота с токеном и конфигурацией
        proposalBot.initialize(bot.getToken(), bot);

        // Регистрируем бота в Telegram API
        telegramBotsApi.registerBot(proposalBot);

        // Добавляем в кэш запущенных ботов
        runningBots.put(bot.getId(), proposalBot);
    }

    /**
     * Останавливает работу бота
     */
    public void stopBot(Long botId) {
        ProposalBot bot = runningBots.remove(botId);
        if (bot != null) {
            // TelegramBotsApi не предоставляет метод unregisterBot,
            // но мы можем остановить обработку обновлений
            // В реальном приложении нужно корректно обработать остановку
        }
    }

    /**
     * Перезапускает бота после изменения настроек
     */
    @Transactional
    public void restartBot(Long botId) throws BotNotFoundException, TelegramApiException {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        stopBot(botId);
        startBot(bot);
    }

    /**
     * Создает базовые настройки для бота
     */
    private void createDefaultSettings(Long botId) {
        List<BotSettings> defaultSettings = new ArrayList<>();

        // Подпись в публикуемых сообщениях
        defaultSettings.add(new BotSettings(botId, "publication_footer",
                "🤖 Отправлено через бота предложки @ProposalManagerBot"));

        // Настройка для отображения статистики
        defaultSettings.add(new BotSettings(botId, "show_statistics", "true"));

        // Автоматическое форматирование публикуемых сообщений
        defaultSettings.add(new BotSettings(botId, "auto_format", "true"));

        botSettingsRepository.saveAll(defaultSettings);
    }

    /**
     * Получает настройку бота по ключу
     */
    public String getBotSetting(Long botId, String key, String defaultValue) {
        return botSettingsRepository.findByBotIdAndKey(botId, key)
                .map(BotSettings::getValue)
                .orElse(defaultValue);
    }

    /**
     * Проверяет, может ли пользователь создать бота
     */
    public boolean canCreateBot(User user) {
        return subscriptionService.canCreateBot(user);
    }

    /**
     * Проверяет, владеет ли пользователь хотя бы одним ботом
     */
    public boolean userHasBots(User user) {
        return botRepository.countByOwnerId(user.getId()) > 0;
    }

    /**
     * Возвращает ID последнего созданного бота пользователя
     */
    public Long getLatestBotId(User user) {
        return botRepository.findTopByOwnerIdOrderByCreatedAtDesc(user.getId())
                .map(Bot::getId)
                .orElse(null);
    }

    /**
     * Проверяет, является ли пользователь администратором бота
     */
    public boolean isAdmin(Long botId, Long telegramId) {
        return botAdminService.isAdmin(botId, telegramId);
    }

    /**
     * Получает список ID всех администраторов бота
     */
    public List<Long> getAdminIds(Long botId) {
        return botAdminService.getAdminIds(botId);
    }

    /**
     * Устанавливает состояние администратора (для FSM)
     */
    public void setAdminState(Long botId, Long adminId, String state) {
        adminStates.put(botId + ":" + adminId, state);
    }

    /**
     * Получает состояние администратора
     */
    public String getAdminState(Long botId, Long adminId) {
        return adminStates.getOrDefault(botId + ":" + adminId, "NONE");
    }

    /**
     * Очищает состояние администратора
     */
    public void clearAdminState(Long botId, Long adminId) {
        adminStates.remove(botId + ":" + adminId);
    }

    /**
     * Получает список ботов пользователя
     */
    public List<Bot> getUserBots(Long userId) {
        return botRepository.findByOwnerId(userId);
    }

    /**
     * Получает статистику бота
     */
    public Map<String, Integer> getBotStats(Long botId) {
        // В реальном приложении здесь будет запрос к БД для получения статистики
        Map<String, Integer> stats = new HashMap<>();
        stats.put("messages_received", 150); // Пример данных
        stats.put("messages_published", 85);
        stats.put("messages_rejected", 65);
        stats.put("users_blocked", 5);
        return stats;
    }

    /**
     * Получает все настройки бота
     */
    public Map<String, String> getBotSettingsMap(Long botId) {
        List<BotSettings> settings = botSettingsRepository.findByBotId(botId);
        Map<String, String> settingsMap = new HashMap<>();

        for (BotSettings setting : settings) {
            settingsMap.put(setting.getKey(), setting.getValue());
        }

        return settingsMap;
    }

    /**
     * Проверяет, доступна ли Premium функция
     */
    public boolean hasPremiumFeature(Bot bot, String feature) {
        return subscriptionService.hasPremiumFeature(bot.getOwnerId(), feature);
    }

    /**
     * Проверяет, есть ли у владельца бота Premium подписка
     */
    public boolean hasPremiumSubscription(Long ownerId) {
        return subscriptionService.hasPremiumSubscription(ownerId);
    }

    /**
     * Планирует отложенную публикацию
     */
    public void schedulePublication(Long botId, Long channelId, String text, Date scheduledDate) {
        // В реальном приложении здесь будет логика для планирования публикации
        // Можно использовать Spring Scheduler или другие механизмы
        System.out.println("Запланирована публикация для бота " + botId +
                " в канал " + channelId + " на " + scheduledDate);
    }

    /**
     * Проверяет, является ли пользователь владельцем или администратором бота
     */
    public boolean isOwnerOrAdmin(Long botId, Long telegramId) {
        Bot bot = botRepository.findById(botId).orElse(null);
        if (bot == null) {
            return false;
        }

        // Проверяем, является ли пользователь владельцем
        if (bot.getOwnerId().equals(telegramId)) {
            return true;
        }

        // Проверяем, является ли пользователь администратором
        return botAdminService.isAdmin(botId, telegramId);
    }

    /**
     * Проверяет, является ли пользователь владельцем бота
     */
    public boolean isOwner(Long botId, Long telegramId) {
        Bot bot = botRepository.findById(botId).orElse(null);
        if (bot == null) {
            return false;
        }

        return bot.getOwnerId().equals(telegramId);
    }

    /**
     * Обновляет имя бота
     */
    @Transactional
    public Bot updateBotName(Long botId, String name) throws BotNotFoundException {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        bot.setName(name);
        bot.setUpdatedAt(new Date());
        return botRepository.save(bot);
    }

    /**
     * Обновляет приветственное сообщение бота
     */
    @Transactional
    public Bot updateBotWelcomeMessage(Long botId, String welcomeMessage) throws BotNotFoundException {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        bot.setWelcomeMessage(welcomeMessage);
        bot.setUpdatedAt(new Date());
        return botRepository.save(bot);
    }

    /**
     * Обновляет сообщение о получении предложения
     */
    @Transactional
    public Bot updateBotConfirmationMessage(Long botId, String confirmationMessage) throws BotNotFoundException {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        bot.setConfirmationMessage(confirmationMessage);
        bot.setUpdatedAt(new Date());
        return botRepository.save(bot);
    }

    /**
     * Сохраняет настройку бота
     */
    @Transactional
    public BotSettings saveBotSetting(Long botId, String key, String value) {
        Optional<BotSettings> existingSetting = botSettingsRepository.findByBotIdAndKey(botId, key);

        if (existingSetting.isPresent()) {
            BotSettings setting = existingSetting.get();
            setting.setValue(value);
            setting.setUpdatedAt(new Date());
            return botSettingsRepository.save(setting);
        } else {
            BotSettings newSetting = new BotSettings(botId, key, value);
            return botSettingsRepository.save(newSetting);
        }
    }

    /**
     * Включает/выключает бота
     */
    @Transactional
    public Bot toggleBotActive(Long botId, boolean active) throws BotNotFoundException {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        bot.setActive(active);
        bot.setUpdatedAt(new Date());

        // Если бот включается, запускаем его, иначе останавливаем
        if (active) {
            try {
                startBot(bot);
            } catch (TelegramApiException e) {
                throw new RuntimeException("Ошибка при запуске бота", e);
            }
        } else {
            stopBot(botId);
        }

        return botRepository.save(bot);
    }

    /**
     * Добавляет администратора бота
     */
    @Transactional
    public void addBotAdmin(Long botId, Long adminId, String username, boolean isMainAdmin) throws BotNotFoundException, UnauthorizedException {
        // Проверяем существование бота
        if (!botRepository.existsById(botId)) {
            throw new BotNotFoundException("Бот не найден");
        }

        // Добавляем администратора
        botAdminService.addAdmin(botId, adminId, username, isMainAdmin);
    }

    /**
     * Добавляет канал к боту
     */
    @Transactional
    public boolean addChannel(Long botId, String channelUsername) throws BotNotFoundException {
        return channelService.addChannel(botId, channelUsername);
    }

    /**
     * Получает бота по ID
     */
    public Bot getBotById(Long botId) throws BotNotFoundException {
        return botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот с ID " + botId + " не найден"));
    }

    /**
     * Обновляет настройки бота из DTO
     */
    @Transactional
    public Bot updateBotSettings(Long botId, BotSettingsDTO settings) throws BotNotFoundException {
        Bot bot = getBotById(botId);

        // Обновляем основные поля, если они указаны
        if (settings.getName() != null) {
            bot.setName(settings.getName());
        }

        if (settings.getWelcomeMessage() != null) {
            bot.setWelcomeMessage(settings.getWelcomeMessage());
        }

        if (settings.getConfirmationMessage() != null) {
            bot.setConfirmationMessage(settings.getConfirmationMessage());
        }

        // Обновляем дополнительные настройки
        if (settings.getPublicationFooter() != null) {
            saveBotSetting(botId, "publication_footer", settings.getPublicationFooter());
        }

        if (settings.getAutoFormat() != null) {
            saveBotSetting(botId, "auto_format", settings.getAutoFormat().toString());
        }

        if (settings.getShowStatistics() != null) {
            saveBotSetting(botId, "show_statistics", settings.getShowStatistics().toString());
        }

        bot.setUpdatedAt(new Date());
        return botRepository.save(bot);
    }

    /**
     * Удаляет бота
     */
    @Transactional
    public void deleteBot(Long botId) throws BotNotFoundException {
        Bot bot = getBotById(botId);

        // Останавливаем бота, если он запущен
        stopBot(botId);

        // Удаляем связанные записи
        botAdminService.deleteAdminsByBotId(botId);
        blockedUserService.clearBlockedUsers(botId);
        channelService.deleteChannelsByBotId(botId);
        botSettingsRepository.deleteByBotId(botId);

        // Удаляем самого бота
        botRepository.delete(bot);
    }
}