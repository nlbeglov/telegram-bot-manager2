package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.BotAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью BotAdmin
 */
@Repository
public interface BotAdminRepository extends JpaRepository<BotAdmin, Long> {

    /**
     * Находит администратора бота по Telegram ID и ID бота
     */
    Optional<BotAdmin> findByBotIdAndTelegramId(Long botId, Long telegramId);

    /**
     * Проверяет, существует ли администратор с указанными параметрами
     */
    boolean existsByBotIdAndTelegramId(Long botId, Long telegramId);

    /**
     * Находит всех администраторов бота
     */
    List<BotAdmin> findByBotId(Long botId);

    /**
     * Находит главного администратора бота
     */
    Optional<BotAdmin> findByBotIdAndIsMainAdminTrue(Long botId);

    /**
     * Удаляет администратора по ID бота и Telegram ID
     */
    void deleteByBotIdAndTelegramId(Long botId, Long telegramId);

    /**
     * Подсчитывает количество администраторов бота
     */
    long countByBotId(Long botId);
}