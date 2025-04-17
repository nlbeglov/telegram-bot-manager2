package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью Bot
 */
@Repository
public interface BotRepository extends JpaRepository<Bot, Long> {

    /**
     * Находит бота по токену
     */
    Optional<Bot> findByToken(String token);

    /**
     * Проверяет, существует ли бот с таким токеном
     */
    boolean existsByToken(String token);

    /**
     * Находит всех ботов пользователя
     */
    List<Bot> findByOwnerId(Long ownerId);

    /**
     * Подсчитывает количество ботов пользователя
     */
    long countByOwnerId(Long ownerId);

    /**
     * Находит последнего созданного бота пользователя
     */
    Optional<Bot> findTopByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Находит активных ботов
     */
    List<Bot> findByActiveTrue();
}