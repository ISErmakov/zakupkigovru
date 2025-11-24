package ru.ermakovdev.torgigov.service;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveDownloadService {

  private final RestTemplate restTemplate;
  private final FileService fileService;

  @Value("${app.zakupki.token:1057e083-4a4a-454c-bbf0-23f3eea52be3}")
  private String token;

  @Value("${app.zakupki.download-dir:./downloads/nsi}")
  private String downloadDir;

  @Value("${app.zakupki.download-delay-ms:2000}")
  private int downloadDelayMs;

  public List<String> downloadAllArchives(List<String> archiveUrls, String nsiCode) {
    List<String> downloadedFiles = new ArrayList<>();
    int successCount = 0;

    for (int i = 0; i < archiveUrls.size(); i++) {
      try {
        String archiveUrl = archiveUrls.get(i);
        log.info("Скачивание архива {}/{}: {}", i + 1, archiveUrls.size(), archiveUrl);

        String filePath = downloadSingleArchive(archiveUrl, nsiCode, i);
        downloadedFiles.add(filePath);
        successCount++;

        long fileSize = Files.size(Paths.get(filePath));
        log.info("✅ Архив {}/{} успешно скачан: {} ({} байт)",
            i + 1, archiveUrls.size(), filePath, fileSize);

        // Задержка между загрузками (кроме последнего архива)
        applyDownloadDelay(i, archiveUrls.size());

      } catch (Exception e) {
        log.error("❌ Ошибка при скачивании архива {}/{}: {}",
            i + 1, archiveUrls.size(), e.getMessage(), e);

        // Задержка даже при ошибке (кроме последнего архива)
        applyDownloadDelay(i, archiveUrls.size());
      }
    }

    return downloadedFiles;
  }

  private String downloadSingleArchive(String archiveUrl, String nsiCode, int partNumber) throws IOException {
    try {
      log.info("Скачивание архива {}/{} по URL: {}", partNumber + 1, archiveUrl);

      HttpHeaders headers = createDownloadHeaders();
      HttpEntity<Void> request = new HttpEntity<>(headers);

      log.debug("Отправка GET запроса на: {}", archiveUrl);

      // Используем URI вместо String для правильного кодирования
      URI uri = URI.create(archiveUrl);

      ResponseEntity<byte[]> response = restTemplate.exchange(
          uri,  // Используем URI здесь
          HttpMethod.GET,
          request,
          byte[].class
      );

      return processDownloadResponse(response, nsiCode, partNumber);

    } catch (HttpClientErrorException | HttpServerErrorException e) {
      log.error("Ошибка HTTP при скачивании: статус {}", e.getStatusCode());
      log.error("Тело ошибки: {}", e.getResponseBodyAsString());
      throw new IOException("HTTP ошибка " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
    } catch (Exception e) {
      log.error("Неожиданная ошибка при скачивании архива {}: {}", partNumber + 1, e.getMessage(), e);
      throw new IOException("Ошибка при скачивании: " + e.getMessage(), e);
    }
  }
  private String processDownloadResponse(ResponseEntity<byte[]> response, String nsiCode, int partNumber) throws IOException {
    // Логируем headers ответа
    HttpHeaders responseHeaders = response.getHeaders();
    log.info("Headers ответа:");
    responseHeaders.forEach((key, value) -> log.info("  {}: {}", key, value));

    log.info("Получен ответ: статус {}", response.getStatusCode());

    // Проверяем что данные есть
    byte[] content = response.getBody();
    if (content == null || content.length == 0) {
      throw new RuntimeException("Пустой ответ от сервера");
    }

    log.info("Успешно получено {} байт", content.length);

    // Проверяем валидность данных
    validateDownloadedContent(content);

    // Извлекаем оригинальное имя файла из content-disposition
    String originalFileName = extractFileNameFromHeaders(responseHeaders, nsiCode, partNumber);

    // Сохраняем файл
    return fileService.saveArchiveFile(content, originalFileName, nsiCode, partNumber);
  }

  private HttpHeaders createDownloadHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("individualPerson_token", token);
    headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    headers.set("Accept", "*/*");
    headers.set("Cache-Control", "no-cache");
    headers.set("Connection", "keep-alive");
    return headers;
  }

  private String extractFileNameFromHeaders(HttpHeaders headers, String nsiCode, int partNumber) {
    String contentDisposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);

    if (contentDisposition != null && contentDisposition.contains("filename=")) {
      Pattern pattern = Pattern.compile("filename=\"([^\"]+)\"");
      Matcher matcher = pattern.matcher(contentDisposition);
      if (matcher.find()) {
        String fileName = matcher.group(1);
        log.info("Извлечено оригинальное имя файла: {}", fileName);
        return fileName;
      }
    }

    // Если не нашли в headers, генерируем имя
    return String.format("%s_part%d_%s.zip",
        nsiCode,
        partNumber + 1,
        LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
  }

  private void validateDownloadedContent(byte[] content) {
    if (content.length >= 4) {
      if (content[0] == 0x50 && content[1] == 0x4B && content[2] == 0x03 && content[3] == 0x04) {
        log.debug("✅ Файл является валидным ZIP архивом");
      } else {
        log.warn("⚠️ Файл не имеет ZIP сигнатуры. Первые байты: {} {} {} {}",
            String.format("%02X", content[0] & 0xFF),
            String.format("%02X", content[1] & 0xFF),
            String.format("%02X", content[2] & 0xFF),
            String.format("%02X", content[3] & 0xFF));

        // Если это не ZIP, возможно это текст ошибки
        String responseText = new String(content);
        if (responseText.contains("ошибка") || responseText.contains("error")) {
          throw new RuntimeException("Сервер вернул ошибку: " + responseText);
        }
      }
    }
  }

  private void applyDownloadDelay(int currentIndex, int totalArchives) {
    if (currentIndex < totalArchives - 1 && downloadDelayMs > 0) {
      log.debug("Задержка {} мс перед следующей загрузкой...", downloadDelayMs);
      try {
        Thread.sleep(downloadDelayMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public long calculateTotalFileSize(List<String> filePaths) {
    return filePaths.stream()
        .mapToLong(file -> {
          try {
            return Files.size(Paths.get(file));
          } catch (IOException e) {
            return 0L;
          }
        })
        .sum();
  }
}