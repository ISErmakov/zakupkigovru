package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.model.PurchaseNotification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationArchiveProcessorService {

  private final NotificationParserService notificationParserService;
  private final NotificationMongoService notificationMongoService;

  /**
   * Обрабатывает архивы с извещениями о закупках
   */
  public void processNotificationArchives(List<String> archivePaths) {
    log.info("=== НАЧАЛО ОБРАБОТКИ АРХИВОВ С ИЗВЕЩЕНИЯМИ ===");
    log.info("Количество архивов для обработки: {}", archivePaths.size());

    List<PurchaseNotification> allNotifications = new ArrayList<>();
    int totalArchives = 0;
    int totalFiles = 0;

    for (String archivePath : archivePaths) {
      try {
        log.info("Обработка архива с извещениями {}/{}: {}",
            ++totalArchives, archivePaths.size(), archivePath);

        // Очищаем старые файлы перед распаковкой
        cleanupExtractedFiles(archivePath);

        // Распаковываем архив
        List<String> xmlFiles = extractZipArchive(archivePath);
        totalFiles += xmlFiles.size();

        // Парсим каждый XML файл
        for (String xmlFile : xmlFiles) {
          log.debug("Парсинг файла извещения: {}", xmlFile);
          List<PurchaseNotification> notifications = notificationParserService.parseNotificationFile(new File(xmlFile));

          if (notifications != null && !notifications.isEmpty()) {
            allNotifications.addAll(notifications);
            log.debug("Из файла {} извлечено {} извещений (всего: {})",
                xmlFile, notifications.size(), allNotifications.size());
          } else {
            log.debug("В файле {} не найдено извещений", xmlFile);
          }
        }

        log.info("✅ Архив с извещениями {} обработан успешно", archivePath);

      } catch (Exception e) {
        log.error("❌ Ошибка обработки архива с извещениями {}: {}", archivePath, e.getMessage(), e);
      }
    }

    log.info("=== ОБРАБОТКА АРХИВОВ С ИЗВЕЩЕНИЯМИ ЗАВЕРШЕНА ===");
    log.info("Обработано архивов: {}, файлов: {}, извлечено извещений: {}",
        totalArchives, totalFiles, allNotifications.size());

    // Сохраняем все извещения в MongoDB
    if (!allNotifications.isEmpty()) {
      log.info("Сохранение {} извещений в MongoDB...", allNotifications.size());
      notificationMongoService.saveNotifications(allNotifications);
      log.info("✅ Все извещения успешно сохранены в MongoDB");
    } else {
      log.warn("⚠️ Нет извещений для сохранения в MongoDB");
    }
  }

  /**
   * Распаковывает ZIP архив и возвращает список XML файлов
   */
  private List<String> extractZipArchive(String archivePath) throws IOException {
    List<String> extractedFiles = new ArrayList<>();
    Path zipPath = Paths.get(archivePath);

    // Создаем директорию для распаковки
    Path extractDir = zipPath.getParent().resolve(zipPath.getFileName().toString() + "_extracted");
    Files.createDirectories(extractDir);

    log.info("Распаковка архива {} в директорию {}", archivePath, extractDir);

    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
          Path filePath = extractDir.resolve(entry.getName());

          // Создаем родительские директории если нужно
          Files.createDirectories(filePath.getParent());

          // Копируем файл из архива с заменой существующих
          Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
          extractedFiles.add(filePath.toString());

          log.debug("Извлечен XML файл: {}", filePath);
        }
        zis.closeEntry();
      }
    }

    log.info("Из архива {} извлечено {} XML файлов", archivePath, extractedFiles.size());
    return extractedFiles;
  }

  /**
   * Очищает директорию с распакованными файлами перед новой распаковкой
   */
  private void cleanupExtractedFiles(String archivePath) {
    try {
      Path zipPath = Paths.get(archivePath);
      Path extractDir = zipPath.getParent().resolve(zipPath.getFileName().toString() + "_extracted");

      if (Files.exists(extractDir)) {
        log.info("Очистка директории: {}", extractDir);
        // Удаляем все файлы в директории
        Files.walk(extractDir)
            .sorted((a, b) -> -a.compareTo(b)) // обратный порядок для удаления файлов перед директориями
            .forEach(path -> {
              try {
                Files.delete(path);
              } catch (IOException e) {
                log.warn("Не удалось удалить файл: {}", path);
              }
            });
        log.info("Директория очищена: {}", extractDir);
      }
    } catch (Exception e) {
      log.warn("Не удалось очистить директорию распаковки: {}", e.getMessage());
    }
  }

  /**
   * Обрабатывает один архив с извещениями
   */
  public void processSingleNotificationArchive(String archivePath) {
    processNotificationArchives(List.of(archivePath));
  }
}