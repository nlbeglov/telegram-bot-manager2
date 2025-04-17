package com.telegramproposalbot.dto;

/**
 * DTO для обновления настроек бота
 */
public class BotSettingsDTO {
    private String name;
    private String welcomeMessage;
    private String confirmationMessage;
    private String publicationFooter;
    private Boolean autoFormat;
    private Boolean showStatistics;

    // Геттеры и сеттеры
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getConfirmationMessage() {
        return confirmationMessage;
    }

    public void setConfirmationMessage(String confirmationMessage) {
        this.confirmationMessage = confirmationMessage;
    }

    public String getPublicationFooter() {
        return publicationFooter;
    }

    public void setPublicationFooter(String publicationFooter) {
        this.publicationFooter = publicationFooter;
    }

    public Boolean getAutoFormat() {
        return autoFormat;
    }

    public void setAutoFormat(Boolean autoFormat) {
        this.autoFormat = autoFormat;
    }

    public Boolean getShowStatistics() {
        return showStatistics;
    }

    public void setShowStatistics(Boolean showStatistics) {
        this.showStatistics = showStatistics;
    }
}