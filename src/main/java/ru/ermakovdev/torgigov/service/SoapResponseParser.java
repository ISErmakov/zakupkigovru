package ru.ermakovdev.torgigov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SoapResponseParser {

  public List<String> extractAllArchiveUrlsFromResponse(String responseBody) {
    if (responseBody == null || responseBody.isEmpty()) {
      throw new RuntimeException("Пустой ответ от сервера");
    }

    log.debug("Парсим ответ для извлечения всех archiveUrl");
    List<String> archiveUrls = new ArrayList<>();

    int startIndex = 0;
    while (true) {
      int start = responseBody.indexOf("<archiveUrl>", startIndex);
      if (start == -1) break;
      start += "<archiveUrl>".length();

      int end = responseBody.indexOf("</archiveUrl>", start);
      if (end == -1) break;

      String archiveUrl = responseBody.substring(start, end);
      archiveUrls.add(archiveUrl);

      startIndex = end + "</archiveUrl>".length();
      log.debug("Извлечен archiveUrl: {}", archiveUrl);
    }

    if (archiveUrls.isEmpty()) {
      log.error("Ни одного archiveUrl не найдено в ответе. Полный ответ: {}", responseBody);
      throw new RuntimeException("Ни одного archiveUrl не найдено в ответе");
    }

    log.info("Извлечено {} archiveUrl", archiveUrls.size());
    return archiveUrls;
  }
}