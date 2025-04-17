package com.telegramproposalbot.entity;

import jakarta.persistence.*;
import java.util.Date;

/**
 * Сущность для представления канала Telegram в базе данных
 */
@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "channel_username")
    private String channelUsername;

    @Column(name = "channel_title")
    private String channelTitle;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    // Конструкторы
    public Channel() {
    }

    public Channel(Long botId, Long channelId, String channelUsername, String channelTitle) {
        this.botId = botId;
        this.channelId = channelId;
        this.channelUsername = channelUsername;
        this.channelTitle = channelTitle;
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

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getChannelUsername() {
        return channelUsername;
    }

    public void setChannelUsername(String channelUsername) {
        this.channelUsername = channelUsername;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}