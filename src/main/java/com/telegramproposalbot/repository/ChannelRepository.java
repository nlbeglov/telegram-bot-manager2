package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью Channel
 */
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    /**
     * Находит канал по ID бота и ID канала
     */
    Optional<Channel> findByBotIdAndChannelId(Long botId, Long channelId);

    /**
     * Находит канал по ID бота и имени пользователя канала
     */
    Optional<Channel> findByBotIdAndChannelUsernameIgnoreCase(Long botId, String channelUsername);

    /**
     * Проверяет, существует ли канал с такими параметрами
     */
    boolean existsByBotIdAndChannelId(Long botId, Long channelId);

    /**
     * Проверяет, существует ли канал с таким именем пользователя
     */
    boolean existsByBotIdAndChannelUsernameIgnoreCase(Long botId, String channelUsername);

    /**
     * Находит все каналы бота
     */
    List<Channel> findByBotId(Long botId);

    /**
     * Удаляет канал по ID бота и имени пользователя
     */
    void deleteByBotIdAndChannelUsernameIgnoreCase(Long botId, String channelUsername);

    /**
     * Подсчитывает количество каналов у бота
     */
    long countByBotId(Long botId);
}