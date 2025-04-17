package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.User;
import com.telegramproposalbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Contact;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    // Кэш для временного хранения данных при создании бота
    private final Map<Long, Map<String, String>> tempUserData = new ConcurrentHashMap<>();

    @Autowired
    public UserService(UserRepository userRepository, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Получает существующего пользователя или создаёт нового
     */
    @Transactional
    public User getOrCreateUser(org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        Optional<User> existingUser = userRepository.findByTelegramId(telegramUser.getId());

        if (existingUser.isPresent()) {
            // Обновляем информацию о пользователе, если она изменилась
            User user = existingUser.get();
            boolean updated = false;

            if (!Objects.equals(user.getUsername(), telegramUser.getUserName())) {
                user.setUsername(telegramUser.getUserName());
                updated = true;
            }

            if (!Objects.equals(user.getFirstName(), telegramUser.getFirstName())) {
                user.setFirstName(telegramUser.getFirstName());
                updated = true;
            }

            if (!Objects.equals(user.getLastName(), telegramUser.getLastName())) {
                user.setLastName(telegramUser.getLastName());
                updated = true;
            }

            if (updated) {
                user.setUpdatedAt(new Date());
                userRepository.save(user);
            }

            return user;
        } else {
            // Создаем нового пользователя
            User newUser = new User();
            newUser.setTelegramId(telegramUser.getId());
            newUser.setUsername(telegramUser.getUserName());
            newUser.setFirstName(telegramUser.getFirstName());
            newUser.setLastName(telegramUser.getLastName());
            newUser.setSubscriptionType("FREE");
            newUser.setCreatedAt(new Date());
            newUser.setUpdatedAt(new Date());

            return userRepository.save(newUser);
        }
    }

    /**
     * Получает пользователя по Telegram ID
     */
    public User getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElse(null);
    }

    /**
     * Обновляет контактную информацию пользователя
     */
    @Transactional
    public User updateUserContact(Long telegramId, Contact contact) {
        User user = getUserByTelegramId(telegramId);
        if (user != null) {
            user.setPhone(contact.getPhoneNumber());
            user.setUpdatedAt(new Date());
            return userRepository.save(user);
        }
        return null;
    }

    /**
     * Проверяет, может ли пользователь создать еще одного бота
     */
    public boolean canCreateBot(User user) {
        return subscriptionService.canCreateBot(user);
    }

    /**
     * Временно сохраняет токен бота при создании
     */
    public void saveTemporaryBotToken(Long userId, String token) {
        tempUserData.computeIfAbsent(userId, k -> new HashMap<>()).put("bot_token", token);
    }

    /**
     * Временно сохраняет имя бота при создании
     */
    public void saveTemporaryBotName(Long userId, String name) {
        tempUserData.computeIfAbsent(userId, k -> new HashMap<>()).put("bot_name", name);
    }

    /**
     * Получает временно сохраненный токен бота
     */
    public String getTemporaryBotToken(Long userId) {
        Map<String, String> userData = tempUserData.get(userId);
        return userData != null ? userData.get("bot_token") : null;
    }

    /**
     * Получает временно сохраненное имя бота
     */
    public String getTemporaryBotName(Long userId) {
        Map<String, String> userData = tempUserData.get(userId);
        return userData != null ? userData.get("bot_name") : null;
    }

    /**
     * Очищает временные данные пользователя
     */
    public void clearTemporaryData(Long userId) {
        tempUserData.remove(userId);
    }

    /**
     * Активирует Premium подписку для пользователя
     */
    @Transactional
    public User activatePremium(Long telegramId, int monthsDuration) {
        User user = getUserByTelegramId(telegramId);
        if (user != null) {
            // Рассчитываем дату окончания подписки
            Date expiryDate = subscriptionService.calculateExpiryDate(user, monthsDuration);

            user.setSubscriptionType("PREMIUM");
            user.setSubscriptionExpiry(expiryDate);
            user.setUpdatedAt(new Date());

            return userRepository.save(user);
        }
        return null;
    }

    /**
     * Деактивирует Premium подписку
     */
    @Transactional
    public User deactivatePremium(Long telegramId) {
        User user = getUserByTelegramId(telegramId);
        if (user != null) {
            user.setSubscriptionType("FREE");
            user.setSubscriptionExpiry(null);
            user.setUpdatedAt(new Date());

            return userRepository.save(user);
        }
        return null;
    }

    /**
     * Проверяет, истекла ли подписка
     */
    public boolean isSubscriptionExpired(User user) {
        return subscriptionService.isSubscriptionExpired(user);
    }

    /**
     * Обрабатывает истекшие подписки
     * Метод может вызываться по расписанию
     */
    @Transactional
    public void processExpiredSubscriptions() {
        List<User> expiredUsers = userRepository.findExpiredPremiumUsers(new Date());

        for (User user : expiredUsers) {
            user.setSubscriptionType("FREE");
            user.setUpdatedAt(new Date());
            userRepository.save(user);

            // Уведомляем пользователя об истечении подписки
            notifySubscriptionExpired(user);
        }
    }

    /**
     * Уведомляет пользователя об истечении подписки
     */
    private void notifySubscriptionExpired(User user) {
        // В реальном приложении здесь будет логика отправки уведомления
        System.out.println("Отправка уведомления пользователю " + user.getTelegramId() +
                " об истечении Premium подписки");
    }

    /**
     * Получает всех пользователей
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Получает пользователей с Premium подпиской
     */
    public List<User> getPremiumUsers() {
        return userRepository.findBySubscriptionType("PREMIUM");
    }

    /**
     * Сохраняет пользователя
     */
    @Transactional
    public User saveUser(User user) {
        user.setUpdatedAt(new Date());
        return userRepository.save(user);
    }

    /**
     * Удаляет пользователя
     */
    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    /**
     * Проверяет, существует ли пользователь с таким Telegram ID
     */
    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).isPresent();
    }

    /**
     * Получает количество пользователей
     */
    public long getUserCount() {
        return userRepository.count();
    }

    /**
     * Получает количество пользователей с Premium подпиской
     */
    public long getPremiumUserCount() {
        return userRepository.countBySubscriptionType("PREMIUM");
    }

    /**
     * Получает пользователя по имени пользователя Telegram
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Находит пользователей с истекающей подпиской
     */
    public List<User> findUsersWithExpiringSubscription(int daysLeft) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysLeft);
        Date expiryThreshold = calendar.getTime();

        return userRepository.findUsersWithExpiringSubscription(new Date(), expiryThreshold);
    }

    /**
     * Отправляет напоминание об истечении подписки
     */
    @Transactional
    public void sendSubscriptionExpiryReminders() {
        // Находим пользователей, у которых подписка истекает через 3 дня
        List<User> usersWithExpiringSubscription = findUsersWithExpiringSubscription(3);

        for (User user : usersWithExpiringSubscription) {
            // Отправляем напоминание
            sendSubscriptionExpiryReminder(user);
        }
    }

    /**
     * Отправляет напоминание пользователю
     */
    private void sendSubscriptionExpiryReminder(User user) {
        // В реальном приложении здесь будет логика отправки напоминания
        System.out.println("Отправка напоминания пользователю " + user.getTelegramId() +
                " о скором истечении Premium подписки");
    }
}