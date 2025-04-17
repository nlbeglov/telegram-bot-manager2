package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.User;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SubscriptionService {

    // Карта доступных функций для каждого типа подписки
    private static final Map<String, Set<String>> SUBSCRIPTION_FEATURES = new HashMap<>();

    // Ограничения для разных типов подписок
    private static final Map<String, Map<String, Integer>> SUBSCRIPTION_LIMITS = new HashMap<>();

    static {
        // Инициализация доступных функций
        Set<String> freeFeatures = new HashSet<>(Arrays.asList(
                "basic_bot",
                "basic_settings",
                "manual_posting"
        ));

        Set<String> premiumFeatures = new HashSet<>(Arrays.asList(
                "basic_bot",
                "basic_settings",
                "manual_posting",
                "multiple_bots",
                "custom_avatar",
                "scheduled_posts",
                "silent_posts",
                "no_branded_footer",
                "mini_apps",
                "analytics"
        ));

        SUBSCRIPTION_FEATURES.put("FREE", freeFeatures);
        SUBSCRIPTION_FEATURES.put("PREMIUM", premiumFeatures);

        // Инициализация ограничений
        Map<String, Integer> freeLimits = new HashMap<>();
        freeLimits.put("max_bots", 1);
        freeLimits.put("max_admins_per_bot", 3);
        freeLimits.put("max_channels_per_bot", 1);

        Map<String, Integer> premiumLimits = new HashMap<>();
        premiumLimits.put("max_bots", 10);
        premiumLimits.put("max_admins_per_bot", 10);
        premiumLimits.put("max_channels_per_bot", 5);

        SUBSCRIPTION_LIMITS.put("FREE", freeLimits);
        SUBSCRIPTION_LIMITS.put("PREMIUM", premiumLimits);
    }

    /**
     * Проверяет, имеет ли пользователь доступ к определенной функции
     */
    public boolean hasFeature(User user, String featureName) {
        String subscriptionType = user.getSubscriptionType();

        // Проверяем, не истекла ли подписка
        if ("PREMIUM".equals(subscriptionType) && isSubscriptionExpired(user)) {
            subscriptionType = "FREE";
        }

        Set<String> features = SUBSCRIPTION_FEATURES.getOrDefault(subscriptionType, Collections.emptySet());
        return features.contains(featureName);
    }

    /**
     * Проверяет, имеет ли владелец бота доступ к функции по его ID
     */
    public boolean hasPremiumFeature(Long ownerId, String featureName) {
        // В реальном приложении здесь будет запрос к БД
        // Для примера возвращаем true для некоторых функций
        return Arrays.asList("scheduled_posts", "silent_posts").contains(featureName);
    }

    /**
     * Получает ограничение для пользователя по типу ограничения
     */
    public int getLimit(User user, String limitName) {
        String subscriptionType = user.getSubscriptionType();

        // Проверяем, не истекла ли подписка
        if ("PREMIUM".equals(subscriptionType) && isSubscriptionExpired(user)) {
            subscriptionType = "FREE";
        }

        Map<String, Integer> limits = SUBSCRIPTION_LIMITS.getOrDefault(subscriptionType, Collections.emptyMap());
        return limits.getOrDefault(limitName, 0);
    }

    /**
     * Проверяет, может ли пользователь создать еще одного бота
     */
    public boolean canCreateBot(User user) {
        int maxBots = getLimit(user, "max_bots");
        // В реальном приложении здесь будет запрос к репозиторию
        int currentBots = 0; // Заглушка

        return currentBots < maxBots;
    }

    /**
     * Проверяет, истекла ли Premium подписка пользователя
     */
    public boolean isSubscriptionExpired(User user) {
        if (!"PREMIUM".equals(user.getSubscriptionType())) {
            return false; // Не Premium подписка
        }

        Date expiryDate = user.getSubscriptionExpiry();
        return expiryDate == null || expiryDate.before(new Date());
    }

    /**
     * Рассчитывает дату истечения подписки
     */
    public Date calculateExpiryDate(User user, int monthsDuration) {
        Calendar calendar = Calendar.getInstance();

        // Если у пользователя уже есть активная подписка, используем её дату
        if ("PREMIUM".equals(user.getSubscriptionType()) &&
                user.getSubscriptionExpiry() != null &&
                user.getSubscriptionExpiry().after(new Date())) {
            calendar.setTime(user.getSubscriptionExpiry());
        }

        // Добавляем указанное количество месяцев
        calendar.add(Calendar.MONTH, monthsDuration);

        return calendar.getTime();
    }

    /**
     * Проверяет, есть ли у пользователя активная Premium подписка
     */
    public boolean hasPremiumSubscription(Long userId) {
        // В реальном приложении здесь будет запрос к БД
        // Для примера возвращаем true
        return true;
    }
}