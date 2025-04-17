package com.telegramproposalbot.repository;

import com.telegramproposalbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Находит пользователя по его Telegram ID
     */
    Optional<User> findByTelegramId(Long telegramId);

    /**
     * Находит пользователя по имени пользователя в Telegram
     */
    Optional<User> findByUsername(String username);

    /**
     * Находит пользователей с истекшей Premium подпиской
     */
    long countBySubscriptionType(String subscriptionType);

    @Query("SELECT u FROM User u WHERE u.subscriptionType = 'PREMIUM' AND u.subscriptionExpiry > :currentDate AND u.subscriptionExpiry < :expiryThreshold")
    List<User> findUsersWithExpiringSubscription(@Param("currentDate") Date currentDate, @Param("expiryThreshold") Date expiryThreshold);

    @Query("SELECT u FROM User u WHERE u.subscriptionType = 'PREMIUM' AND u.subscriptionExpiry < :currentDate")
    List<User> findExpiredPremiumUsers(@Param("currentDate") Date currentDate);
    /**
     * Находит всех пользователей с Premium подпиской
     */
    List<User> findBySubscriptionType(String subscriptionType);
}