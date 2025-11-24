package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final RestTemplate restTemplate;
  private final SoapResponseParser soapResponseParser;

  @Value("${app.zakupki.token:1057e083-4a4a-454c-bbf0-23f3eea52be3}")
  private String token;

  @Value("${app.zakupki.base-url:https://int44.zakupki.gov.ru/eis-integration/services/getDocsIP}")
  private String baseUrl;

  /**
   * Получает ссылки на архивы с извещениями по региону и дате
   */
  public java.util.List<String> getNotificationArchiveUrls(String regionCode, LocalDate date) {
    String soapRequest = createNotificationSoapRequest(regionCode, date);
    String responseBody = sendSoapRequest(soapRequest);
    return soapResponseParser.extractAllArchiveUrlsFromResponse(responseBody);
  }

  /**
   * Создает SOAP запрос для получения извещений по региону
   */
  private String createNotificationSoapRequest(String regionCode, LocalDate date) {
    String requestId = UUID.randomUUID().toString();
    String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    String exactDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

    return String.format("""
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
                              xmlns:ws="http://zakupki.gov.ru/fz44/get-docs-ip/ws">
               <soapenv:Header>
                  <individualPerson_token>%s</individualPerson_token>
               </soapenv:Header>
               <soapenv:Body>
                  <ws:getDocsByOrgRegionRequest> 
                     <index>
                        <id>%s</id>
                        <createDateTime>%s</createDateTime>
                        <mode>PROD</mode>
                     </index>
                     <selectionParams>
                        <orgRegion>%s</orgRegion>
                        <subsystemType>PRIZ</subsystemType>
                        <documentType44>epNotificationEF2020</documentType44>
                        <periodInfo>
                            <exactDate>%s</exactDate>
                        </periodInfo>
                    </selectionParams>
                  </ws:getDocsByOrgRegionRequest>
               </soapenv:Body>
            </soapenv:Envelope>
            """, token, requestId, currentDateTime, regionCode, exactDate);
  }

  private String sendSoapRequest(String soapRequest) {
    try {
      HttpHeaders headers = createSoapHeaders();

      log.info("Отправка SOAP запроса для извещений на URL: {}", baseUrl);
      log.debug("SOAP запрос: {}", soapRequest);

      HttpEntity<String> request = new HttpEntity<>(soapRequest, headers);

      ResponseEntity<String> response = restTemplate.exchange(
          baseUrl,
          HttpMethod.POST,
          request,
          String.class
      );

      log.info("Получен SOAP ответ для извещений: статус {}", response.getStatusCode());

      return response.getBody();

    } catch (Exception e) {
      log.error("Ошибка при отправке SOAP запроса для извещений: {}", e.getMessage(), e);
      throw new RuntimeException("Ошибка при отправке запроса для извещений: " + e.getMessage(), e);
    }
  }

  private HttpHeaders createSoapHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_XML);
    headers.set("individualPerson_token", token);
    headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    headers.set("Accept", "text/xml");
    headers.set("Cache-Control", "no-cache");
    return headers;
  }
}