package ru.ermakovdev.torgigov.configuration;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate restTemplate() throws Exception {
    // Создаем стратегию доверия, которая принимает все сертификаты
    TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;

    // Создаем SSL контекст с нашей стратегией доверия
    SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial(null, acceptingTrustStrategy)
        .build();

    // Создаем фабрику SSL сокетов, которая игнорирует проверку hostname
    SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
        sslContext, (hostname, session) -> true);

    // Создаем менеджер соединений с нашей SSL фабрикой
    PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
        .setSSLSocketFactory(sslSocketFactory)
        .setDefaultSocketConfig(SocketConfig.custom()
            .setSoTimeout(Timeout.of(120, TimeUnit.SECONDS))
            .build())
        .build();

    // Настраиваем таймауты запросов
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(Timeout.of(120, TimeUnit.SECONDS))
        .setResponseTimeout(Timeout.of(120, TimeUnit.SECONDS))
        .build();

    // Создаем HTTP клиент с нашим менеджером соединений
    var httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)
        .build();

    // Создаем фабрику запросов для RestTemplate
    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return new RestTemplate(requestFactory);
  }
}