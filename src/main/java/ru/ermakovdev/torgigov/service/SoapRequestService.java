package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoapRequestService {

  private final RestTemplate restTemplate;
  private final SoapResponseParser soapResponseParser;

  @Value("${app.zakupki.token:1057e083-4a4a-454c-bbf0-23f3eea52be3}")
  private String token;

  @Value("${app.zakupki.base-url:https://int44.zakupki.gov.ru/eis-integration/services/getDocsIP}")
  private String baseUrl;

  public List<String> getArchiveUrls(String nsiCode) {
    String soapRequest = createSoapRequest(nsiCode);
    String responseBody = sendSoapRequest(soapRequest);
    return soapResponseParser.extractAllArchiveUrlsFromResponse(responseBody);
  }

  private String createSoapRequest(String nsiCode) {
    String requestId = UUID.randomUUID().toString();
    String currentDateTime = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    return String.format("""
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
                              xmlns:ws="http://zakupki.gov.ru/fz44/get-docs-ip/ws">
               <soapenv:Header>
                  <individualPerson_token>%s</individualPerson_token>
               </soapenv:Header>
               <soapenv:Body>
                  <ws:getNsiRequest>
                     <index>
                        <id>%s</id>
                        <createDateTime>%s</createDateTime>
                        <mode>PROD</mode>
                     </index>
                     <selectionParams>
                        <nsiCode44>%s</nsiCode44>
                        <nsiKind>all</nsiKind>
                     </selectionParams>
                  </ws:getNsiRequest>
               </soapenv:Body>
            </soapenv:Envelope>
            """, token, requestId, currentDateTime, nsiCode);
  }

  private String sendSoapRequest(String soapRequest) {
    try {
      HttpHeaders headers = createSoapHeaders();

      log.info("Отправка SOAP запроса на URL: {}", baseUrl);
      log.debug("SOAP запрос: {}", soapRequest);

      HttpEntity<String> request = new HttpEntity<>(soapRequest, headers);

      ResponseEntity<String> response = restTemplate.exchange(
          baseUrl,
          HttpMethod.POST,
          request,
          String.class
      );

      log.info("Получен SOAP ответ: статус {}", response.getStatusCode());
      log.debug("Полный SOAP ответ: {}", response.getBody());

      return response.getBody();

    } catch (Exception e) {
      log.error("Ошибка при отправке SOAP запроса: {}", e.getMessage(), e);
      throw new RuntimeException("Ошибка при отправке запроса: " + e.getMessage(), e);
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