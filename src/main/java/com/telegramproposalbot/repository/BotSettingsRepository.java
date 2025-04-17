package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.BotSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью BotSettings
 */
@Repository
public interface BotSettingsRepository extends JpaRepository<BotSettings, Long> {

    /**
     * Находит настройку по ID бота и ключу
     */
    Optional<BotSettings> findByBotIdAndKey(Long botId, String key);

    /**
     * Проверяет существование настройки
     */
    boolean existsByBotIdAndKey(Long botId, String key);

    /**
     * Находит все настройки бота
     */
    List<BotSettings> findByBotId(Long botId);

    /**
     * Удаляет настройку бота
     */
    void deleteByBotIdAndKey(Long botId, String key);

    /**
     * Удаляет все настройки бота
     */
    void deleteByBotId(Long botId);
}