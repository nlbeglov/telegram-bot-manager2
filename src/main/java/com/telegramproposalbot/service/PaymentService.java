package com.telegramproposalbot.service;

import com.telegramproposalbot.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для обработки платежей через Telegram
 */
@Service
public class PaymentService {

    private final UserService userService;

    @Value("${subscription.premium.monthly.price}")
    private int premiumMonthlyPrice;

    @Value("${subscription.premium.yearly.price}")
    private int premiumYearlyPrice;

    // Тестовый платежный токен для Telegram
    @Value("${telegram.payment.token:TEST_PAYMENT_TOKEN}")
    private String paymentProviderToken;

    // Карта для отслеживания платежей
    private final Map<String, PaymentInfo> paymentInfoMap = new ConcurrentHashMap<>();

    @Autowired
    public PaymentService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Создает инвойс для оплаты подписки через Telegram
     */
    public SendInvoice createSubscriptionInvoice(Long chatId, String planType) {
        // Генерируем уникальный payload
        String payload = "premium_" + planType + "_" + UUID.randomUUID().toString();

        // Создаем инвойс
        SendInvoice invoice = new SendInvoice();
        invoice.setChatId(chatId.toString());
        invoice.setTitle("Premium подписка");

        int priceAmount;
        String description;
        if ("monthly".equals(planType)) {
            priceAmount = premiumMonthlyPrice;
            description = "Месячная Premium подписка для управления ботами-предложками";
        } else if ("yearly".equals(planType)) {
            priceAmount = premiumYearlyPrice;
            description = "Годовая Premium подписка для управления ботами-предложками";
        } else {
            throw new IllegalArgumentException("Неизвестный тип подписки: " + planType);
        }

        invoice.setDescription(description);
        invoice.setPayload(payload);
        invoice.setProviderToken(paymentProviderToken);
        invoice.setCurrency("RUB");

        // Добавляем цену
        List<LabeledPrice> prices = new ArrayList<>();
        prices.add(new LabeledPrice("Premium подписка", priceAmount * 100)); // Цена в копейках
        invoice.setPrices(prices);

        // Сохраняем информацию о платеже
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setChatId(chatId);
        paymentInfo.setPlanType(planType);
        paymentInfo.setAmount(priceAmount);
        paymentInfo.setCreatedAt(new Date());

        paymentInfoMap.put(payload, paymentInfo);

        return invoice;
    }

    /**
     * Обрабатывает успешный платеж
     */
    public void processSuccessfulPayment(String payload) {
        PaymentInfo paymentInfo = paymentInfoMap.get(payload);
        if (paymentInfo == null) {
            throw new IllegalArgumentException("Платеж не найден: " + payload);
        }

        // Получаем пользователя
        User user = userService.getUserByTelegramId(paymentInfo.getChatId());
        if (user == null) {
            throw new IllegalArgumentException("Пользователь не найден: " + paymentInfo.getChatId());
        }

        // Активируем подписку
        int months = "monthly".equals(paymentInfo.getPlanType()) ? 1 : 12;
        userService.activatePremium(paymentInfo.getChatId(), months);

        // Удаляем информацию о платеже
        paymentInfoMap.remove(payload);
    }

    /**
     * Класс для хранения информации о платеже
     */
    private static class PaymentInfo {
        private Long chatId;
        private String planType;
        private int amount;
        private Date createdAt;

        public Long getChatId() {
            return chatId;
        }

        public void setChatId(Long chatId) {
            this.chatId = chatId;
        }

        public String getPlanType() {
            return planType;
        }

        public void setPlanType(String planType) {
            this.planType = planType;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
        }
    }
}