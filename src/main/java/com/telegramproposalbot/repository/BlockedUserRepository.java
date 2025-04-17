package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью BlockedUser
 */
@Repository
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    /**
     * Находит заблокированного пользователя по ID бота и Telegram ID
     */
    Optional<BlockedUser> findByBotIdAndTelegramId(Long botId, Long telegramId);

    /**
     * Проверяет, заблокирован ли пользователь
     */
    boolean existsByBotIdAndTelegramId(Long botId, Long telegramId);

    /**
     * Находит всех заблокированных пользователей бота
     */
    List<BlockedUser> findByBotId(Long botId);

    /**
     * Удаляет запись о блокировке пользователя
     */
    void deleteByBotIdAndTelegramId(Long botId, Long telegramId);
}