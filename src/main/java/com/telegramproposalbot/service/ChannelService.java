package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.Bot;
import com.telegramproposalbot.entity.Channel;
import com.telegramproposalbot.exception.BotNotFoundException;
import com.telegramproposalbot.repository.BotRepository;
import com.telegramproposalbot.repository.ChannelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для управления каналами Telegram
 */
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final BotRepository botRepository;
    private final SubscriptionService subscriptionService;

    @Autowired
    public ChannelService(ChannelRepository channelRepository,
                          BotRepository botRepository,
                          SubscriptionService subscriptionService) {
        this.channelRepository = channelRepository;
        this.botRepository = botRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Добавляет канал к боту
     */
    @Transactional
    public boolean addChannel(Long botId, String channelUsername) throws BotNotFoundException {
        // Проверяем, существует ли бот
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException("Бот не найден"));

        // Проверяем, не добавлен ли уже канал
        if (channelRepository.existsByBotIdAndChannelUsernameIgnoreCase(botId, channelUsername)) {
            return false;
        }

        // Проверяем лимит каналов
        long channelsCount = channelRepository.countByBotId(botId);
        // Получаем максимальное количество каналов из настроек подписки
        int maxChannels = subscriptionService.getLimit(null, "max_channels_per_bot"); // TODO: заменить null на owner

        if (channelsCount >= maxChannels) {
            return false;
        }

        // В реальном приложении здесь нужно проверить, является ли бот админом канала
        // Для этого нужно использовать Telegram API метод getChatAdministrators

        // Создаем и сохраняем новый канал
        Channel channel = new Channel();
        channel.setBotId(botId);
        // Здесь должен быть запрос к Telegram API для получения channelId
        // Для примера используем -1 как ID канала
        channel.setChannelId(-1L);
        channel.setChannelUsername(channelUsername);
        channel.setChannelTitle("@" + channelUsername); // В реальном приложении получаем из Telegram API

        channelRepository.save(channel);
        return true;
    }

    /**
     * Удаляет канал
     */
    @Transactional
    public boolean removeChannel(Long botId, String channelUsername) {
        // Проверяем, существует ли канал
        if (!channelRepository.existsByBotIdAndChannelUsernameIgnoreCase(botId, channelUsername)) {
            return false;
        }

        channelRepository.deleteByBotIdAndChannelUsernameIgnoreCase(botId, channelUsername);
        return true;
    }

    /**
     * Получает список всех каналов бота
     */
    public List<Channel> getChannels(Long botId) {
        return channelRepository.findByBotId(botId);
    }

    /**
     * Получает список ID всех каналов бота
     */
    public List<Long> getChannelIds(Long botId) {
        return channelRepository.findByBotId(botId).stream()
                .map(Channel::getChannelId)
                .collect(Collectors.toList());
    }

    /**
     * Получает имя пользователя канала по ID
     */
    public String getChannelUsername(Long botId, Long channelId) {
        return channelRepository.findByBotIdAndChannelId(botId, channelId)
                .map(Channel::getChannelUsername)
                .orElse(null);
    }

    /**
     * Получает информацию о каналах в формате для отображения
     */
    public List<Map<String, String>> getChannelsInfo(Long botId) {
        List<Channel> channels = channelRepository.findByBotId(botId);
        List<Map<String, String>> channelsInfo = new ArrayList<>();

        for (Channel channel : channels) {
            Map<String, String> info = new HashMap<>();
            info.put("id", channel.getChannelId().toString());
            info.put("username", channel.getChannelUsername());
            info.put("title", channel.getChannelTitle());
            channelsInfo.add(info);
        }

        return channelsInfo;
    }

    /**
     * Проверяет права бота в канале
     * В реальном приложении должен делать запрос к Telegram API
     */
    public boolean checkBotPermissions(String botToken, String channelUsername) {
        // Заглушка, в реальном приложении здесь будет проверка прав через Telegram API
        return true;
    }
    /**
     * Удаляет все каналы, связанные с ботом
     */
    @Transactional
    public void deleteChannelsByBotId(Long botId) {
        List<Channel> channels = channelRepository.findByBotId(botId);
        channelRepository.deleteAll(channels);
    }
}