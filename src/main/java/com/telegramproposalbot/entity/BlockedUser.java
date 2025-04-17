package com.telegramproposalbot.entity;

import jakarta.persistence.*;
import java.util.Date;

/**
 * Сущность для представления заблокированного пользователя в базе данных
 */
@Entity
@Table(name = "blocked_users")
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "blocked_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date blockedAt;

    // Конструкторы
    public BlockedUser() {
    }

    public BlockedUser(Long botId, Long telegramId) {
        this.botId = botId;
        this.telegramId = telegramId;
        this.blockedAt = new Date();
    }

    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBotId() {
        return botId;
    }

    public void setBotId(Long botId) {
        this.botId = botId;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public Date getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(Date blockedAt) {
        this.blockedAt = blockedAt;
    }
}