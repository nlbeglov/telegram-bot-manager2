package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.BlockedUser;
import com.telegramproposalbot.repository.BlockedUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для управления заблокированными пользователями
 */
@Service
public class BlockedUserService {

    private final BlockedUserRepository blockedUserRepository;

    @Autowired
    public BlockedUserService(BlockedUserRepository blockedUserRepository) {
        this.blockedUserRepository = blockedUserRepository;
    }

    /**
     * Блокирует пользователя для конкретного бота
     */
    @Transactional
    public BlockedUser blockUser(Long botId, Long telegramId) {
        // Проверяем, не заблокирован ли уже пользователь
        if (blockedUserRepository.existsByBotIdAndTelegramId(botId, telegramId)) {
            return blockedUserRepository.findByBotIdAndTelegramId(botId, telegramId).orElse(null);
        }

        // Создаем новую запись о блокировке
        BlockedUser blockedUser = new BlockedUser(botId, telegramId);
        return blockedUserRepository.save(blockedUser);
    }

    /**
     * Разблокирует пользователя для конкретного бота
     */
    @Transactional
    public void unblockUser(Long botId, Long telegramId) {
        blockedUserRepository.deleteByBotIdAndTelegramId(botId, telegramId);
    }

    /**
     * Проверяет, заблокирован ли пользователь
     */
    public boolean isBlocked(Long botId, Long telegramId) {
        return blockedUserRepository.existsByBotIdAndTelegramId(botId, telegramId);
    }

    /**
     * Получает список всех заблокированных пользователей
     */
    public List<BlockedUser> getBlockedUsers(Long botId) {
        return blockedUserRepository.findByBotId(botId);
    }

    /**
     * Получает информацию о заблокированном пользователе
     */
    public BlockedUser getBlockedUser(Long botId, Long telegramId) {
        return blockedUserRepository.findByBotIdAndTelegramId(botId, telegramId).orElse(null);
    }

    /**
     * Удаляет все блокировки пользователей для бота
     */
    @Transactional
    public void clearBlockedUsers(Long botId) {
        List<BlockedUser> blockedUsers = blockedUserRepository.findByBotId(botId);
        blockedUserRepository.deleteAll(blockedUsers);
    }
}