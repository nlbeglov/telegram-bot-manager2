package com.telegramproposalbot.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        return new YamlMessageSource("messages/messages");
    }

    @Bean
    public LocalValidatorFactoryBean getValidator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource());
        return bean;
    }

    public static class YamlMessageSource extends AbstractMessageSource {

        private final Map<Locale, Map<String, String>> messages = new HashMap<>();

        public YamlMessageSource(String baseName) {
            loadYaml(baseName, Locale.ENGLISH);
            loadYaml(baseName, new Locale("ru"));
        }

        private void loadYaml(String baseName, Locale locale) {
            String localeSuffix = locale.equals(Locale.ENGLISH) ? "en" : "ru";
            String filename = baseName + "_" + localeSuffix + ".yml";
            try {
                ClassPathResource resource = new ClassPathResource(filename);
                if (resource.exists()) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(resource.getInputStream());
                    Map<String, String> flatMap = new HashMap<>();
                    flatten(data, "", flatMap);
                    messages.put(locale, flatMap);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load YAML file: " + filename, e);
            }
        }

        private void flatten(Map<String, Object> source, String prefix, Map<String, String> target) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    flatten((Map<String, Object>) value, key, target);
                } else {
                    target.put(key, String.valueOf(value));
                }
            }
        }

        @Override
        protected MessageFormat resolveCode(String code, Locale locale) {
            Map<String, String> localeMessages = messages.getOrDefault(locale, messages.get(new Locale("ru")));
            String msg = localeMessages.get(code);
            if (msg == null) return null;
            return new MessageFormat(msg, locale);
        }
    }
}
