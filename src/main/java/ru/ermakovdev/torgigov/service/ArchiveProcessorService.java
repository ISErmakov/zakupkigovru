package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.model.Okpd2Item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveProcessorService {

  private final Okpd2ParserService okpd2ParserService;
  private final Okpd2MongoService okpd2MongoService;

  public void processDownloadedArchives(List<String> archivePaths) {
    log.info("Начинаем обработку {} архивов", archivePaths.size());

    List<Okpd2Item> allItems = new ArrayList<>();
    int totalArchives = 0;
    int totalFiles = 0;

    for (String archivePath : archivePaths) {
      try {
        log.info("Обработка архива {}/{}: {}",
            ++totalArchives, archivePaths.size(), archivePath);

        List<String> xmlFiles = extractZipArchive(archivePath);
        totalFiles += xmlFiles.size();

        for (String xmlFile : xmlFiles) {
          log.debug("Парсинг XML файла: {}", xmlFile);
          List<Okpd2Item> items = okpd2ParserService.parseXmlFile(xmlFile);
          allItems.addAll(items);
          log.info("Из файла {} извлечено {} записей (всего: {})",
              xmlFile, items.size(), allItems.size());
        }

        log.info("✅ Архив {} обработан успешно", archivePath);

      } catch (Exception e) {
        log.error("❌ Ошибка обработки архива {}: {}", archivePath, e.getMessage(), e);
      }
    }

    log.info("=== ОБРАБОТКА АРХИВОВ ЗАВЕРШЕНА ===");
    log.info("Обработано архивов: {}, файлов: {}, извлечено записей: {}",
        totalArchives, totalFiles, allItems.size());

    // Сохраняем ВСЕ данные ОДИН РАЗ в конце
    if (!allItems.isEmpty()) {
      log.info("Сохранение {} записей в MongoDB...", allItems.size());
      okpd2MongoService.saveAll(allItems);
      log.info("✅ Все данные успешно сохранены в MongoDB");
    } else {
      log.warn("⚠️ Нет данных для сохранения в MongoDB");
    }
  }

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
        if (!entry.isDirectory()) {
          Path filePath = extractDir.resolve(entry.getName());

          // Создаем родительские директории если нужно
          Files.createDirectories(filePath.getParent());

          // Удаляем файл если он уже существует (перезаписываем)
          if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.debug("Удален существующий файл: {}", filePath);
          }

          // Копируем файл из архива
          Files.copy(zis, filePath);
          extractedFiles.add(filePath.toString());

          log.debug("Извлечен файл: {}", filePath);
        }
        zis.closeEntry();
      }
    }

    log.info("Из архива {} извлечено {} файлов", archivePath, extractedFiles.size());
    return extractedFiles;
  }
}