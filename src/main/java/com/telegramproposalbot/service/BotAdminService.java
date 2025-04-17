package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.BotAdmin;
import com.telegramproposalbot.exception.UnauthorizedException;
import com.telegramproposalbot.repository.BotAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для управления администраторами ботов
 */
@Service
public class BotAdminService {

    private final BotAdminRepository botAdminRepository;
    private final SubscriptionService subscriptionService;

    @Autowired
    public BotAdminService(BotAdminRepository botAdminRepository, SubscriptionService subscriptionService) {
        this.botAdminRepository = botAdminRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Добавляет администратора к боту
     */
    @Transactional
    public BotAdmin addAdmin(Long botId, Long telegramId, String username, boolean isMainAdmin) throws UnauthorizedException {
        // Проверяем, существует ли уже такой администратор
        if (botAdminRepository.existsByBotIdAndTelegramId(botId, telegramId)) {
            throw new UnauthorizedException("Пользователь уже является администратором этого бота");
        }

        // Если это главный администратор, проверяем, что нет другого главного администратора
        if (isMainAdmin && botAdminRepository.findByBotIdAndIsMainAdminTrue(botId).isPresent()) {
            throw new UnauthorizedException("У бота уже есть главный администратор");
        }

        // Проверяем лимит администраторов (только если не главный админ)
        if (!isMainAdmin) {
            long adminsCount = botAdminRepository.countByBotId(botId);
            // TODO: Заменить hardcoded значение на получение лимита из подписки
            int maxAdmins = 5;
            if (adminsCount >= maxAdmins) {
                throw new UnauthorizedException("Достигнут лимит администраторов для данного бота");
            }
        }

        // Создаем и сохраняем нового администратора
        BotAdmin admin = new BotAdmin();
        admin.setBotId(botId);
        admin.setTelegramId(telegramId);
        admin.setUsername(username);
        admin.setMainAdmin(isMainAdmin);
        admin.setCreatedAt(new Date());

        return botAdminRepository.save(admin);
    }

    /**
     * Удаляет администратора бота
     */
    @Transactional
    public void removeAdmin(Long botId, Long telegramId, Long requesterId) throws UnauthorizedException {
        // Проверяем, что запрашивающий - главный администратор
        BotAdmin requester = botAdminRepository.findByBotIdAndTelegramId(botId, requesterId)
                .orElseThrow(() -> new UnauthorizedException("Вы не являетесь администратором этого бота"));

        if (!requester.isMainAdmin()) {
            throw new UnauthorizedException("Только главный администратор может удалять других администраторов");
        }

        // Проверяем, не пытается ли главный админ удалить сам себя
        if (requesterId.equals(telegramId)) {
            throw new UnauthorizedException("Главный администратор не может удалить сам себя");
        }

        // Удаляем администратора
        botAdminRepository.deleteByBotIdAndTelegramId(botId, telegramId);
    }

    /**
     * Проверяет, является ли пользователь администратором бота
     */
    public boolean isAdmin(Long botId, Long telegramId) {
        return botAdminRepository.existsByBotIdAndTelegramId(botId, telegramId);
    }

    /**
     * Проверяет, является ли пользователь главным администратором бота
     */
    public boolean isMainAdmin(Long botId, Long telegramId) {
        return botAdminRepository.findByBotIdAndTelegramId(botId, telegramId)
                .map(BotAdmin::isMainAdmin)
                .orElse(false);
    }

    /**
     * Получает список всех администраторов бота
     */
    public List<BotAdmin> getBotAdmins(Long botId) {
        return botAdminRepository.findByBotId(botId);
    }

    /**
     * Получает список ID всех администраторов бота
     */
    public List<Long> getAdminIds(Long botId) {
        return botAdminRepository.findByBotId(botId).stream()
                .map(BotAdmin::getTelegramId)
                .collect(Collectors.toList());
    }

    /**
     * Получает главного администратора бота
     */
    public BotAdmin getMainAdmin(Long botId) {
        return botAdminRepository.findByBotIdAndIsMainAdminTrue(botId).orElse(null);
    }

    /**
     * Удаляет всех администраторов бота
     */
    @Transactional
    public void deleteAdminsByBotId(Long botId) {
        List<BotAdmin> admins = botAdminRepository.findByBotId(botId);
        botAdminRepository.deleteAll(admins);
    }
}