package com.telegramproposalbot.entity;

import jakarta.persistence.*;
import java.util.Date;

/**
 * Сущность для представления администратора бота в базе данных
 */
@Entity
@Table(name = "bot_admins")
public class BotAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column
    private String username;

    @Column(name = "is_main_admin", nullable = false)
    private boolean isMainAdmin;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    // Конструкторы
    public BotAdmin() {
    }

    public BotAdmin(Long botId, Long telegramId, String username, boolean isMainAdmin) {
        this.botId = botId;
        this.telegramId = telegramId;
        this.username = username;
        this.isMainAdmin = isMainAdmin;
        this.createdAt = new Date();
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isMainAdmin() {
        return isMainAdmin;
    }

    public void setMainAdmin(boolean mainAdmin) {
        isMainAdmin = mainAdmin;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}