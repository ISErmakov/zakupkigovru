package ru.ermakovdev.torgigov.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class YandexApiConfig {

  @Value("${yandex.geocoder.api.key:}")
  private String yandexApiKey;

  @Bean
  public RestTemplate yandexRestTemplate() {
    return new RestTemplate();
  }

  public String getYandexApiKey() {
    return yandexApiKey;
  }
}