package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.model.NsiDownloadLog;
import ru.ermakovdev.torgigov.repository.NsiDownloadLogRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZakupkiService {

  private final SoapRequestService soapRequestService;
  private final ArchiveDownloadService archiveDownloadService;
  private final ArchiveProcessorService archiveProcessorService; // Для справочников ОКПД2
  private final NotificationService notificationService;
  private final Okpd2MongoService okpd2MongoService;
  private final NsiDownloadLogRepository logRepository;

  // ДОБАВЬТЕ ЭТИ СЕРВИСЫ ДЛЯ ИЗВЕЩЕНИЙ
  private final NotificationArchiveProcessorService notificationArchiveProcessorService;
  private final NotificationMongoService notificationMongoService;

  public void downloadNsiOkpd2() {
    String nsiCode = "nsiOKPD2";
    NsiDownloadLog downloadLog = new NsiDownloadLog();
    downloadLog.setNsiCode(nsiCode);

    try {
      log.info("=== НАЧАЛО ЗАГРУЗКИ СПРАВОЧНИКА {} ===", nsiCode);

      // 1. Получаем ссылки на архивы
      List<String> archiveUrls = soapRequestService.getArchiveUrls(nsiCode);
      log.info("Получено {} ссылок на архивы", archiveUrls.size());

      // 2. Скачиваем архивы
      List<String> downloadedFiles = archiveDownloadService.downloadAllArchives(archiveUrls, nsiCode);

      // 3. Обрабатываем архивы и сохраняем в MongoDB
      if (!downloadedFiles.isEmpty()) {
        archiveProcessorService.processDownloadedArchives(downloadedFiles);

        // Проверяем результат
        long savedCount = okpd2MongoService.getCount();
        log.info("=== ИТОГ: В MongoDB сохранено {} записей ОКПД2 ===", savedCount);
      } else {
        log.warn("Нет скачанных файлов для обработки");
      }

      // 4. Сохраняем информацию о загрузке
      downloadLog.setStatus(!downloadedFiles.isEmpty() ? "SUCCESS" : "ERROR");
      downloadLog.setFilePath(String.join("; ", downloadedFiles));
      downloadLog.setFileSize(archiveDownloadService.calculateTotalFileSize(downloadedFiles));

      log.info("=== ЗАГРУЗКА СПРАВОЧНИКА {} ЗАВЕРШЕНА ===", nsiCode);

    } catch (Exception e) {
      log.error("=== ОШИБКА ПРИ ЗАГРУЗКЕ СПРАВОЧНИКА {} ===", nsiCode, e);
      downloadLog.setStatus("ERROR");
      downloadLog.setErrorMessage(e.getMessage());
    }

    logRepository.save(downloadLog);
  }

  /**
   * Метод для загрузки извещений по Свердловской области
   */
  public void downloadSverdlovskNotifications(LocalDate date) {
    String regionCode = "66"; // Код Свердловской области

    NsiDownloadLog downloadLog = new NsiDownloadLog();
    downloadLog.setNsiCode("NOTIFICATIONS_" + regionCode + "_" + date);

    try {
      log.info("=== НАЧАЛО ЗАГРУЗКИ ИЗВЕЩЕНИЙ ДЛЯ РЕГИОНА {} ЗА {} ===", regionCode, date);

      // 1. Получаем ссылки на архивы с извещениями
      List<String> archiveUrls = notificationService.getNotificationArchiveUrls(regionCode, date);
      log.info("Получено {} ссылок на архивы с извещениями", archiveUrls.size());

      // 2. Скачиваем архивы
      List<String> downloadedFiles = archiveDownloadService.downloadAllArchives(archiveUrls, "notifications");

      // 3. Обрабатываем архивы с извещениями через ПРАВИЛЬНЫЙ процессор
      if (!downloadedFiles.isEmpty()) {
        notificationArchiveProcessorService.processNotificationArchives(downloadedFiles);

        // Проверяем результат
        long savedCount = notificationMongoService.getCount();
        log.info("=== ИТОГ: В MongoDB сохранено {} извещений ===", savedCount);
      } else {
        log.warn("Нет скачанных файлов с извещениями для обработки");
      }

      downloadLog.setStatus(!downloadedFiles.isEmpty() ? "SUCCESS" : "ERROR");
      downloadLog.setFilePath(String.join("; ", downloadedFiles));
      downloadLog.setFileSize(archiveDownloadService.calculateTotalFileSize(downloadedFiles));

      log.info("=== ЗАГРУЗКА ИЗВЕЩЕНИЙ ДЛЯ РЕГИОНА {} ЗА {} ЗАВЕРШЕНА ===", regionCode, date);

    } catch (Exception e) {
      log.error("=== ОШИБКА ПРИ ЗАГРУЗКЕ ИЗВЕЩЕНИЙ ДЛЯ РЕГИОНА {} ===", regionCode, e);
      downloadLog.setStatus("ERROR");
      downloadLog.setErrorMessage(e.getMessage());
    }

    logRepository.save(downloadLog);
  }

  /**
   * Метод для загрузки извещений за несколько дней
   */
  public void downloadSverdlovskNotificationsForPeriod(LocalDate startDate, LocalDate endDate) {
    LocalDate currentDate = startDate;

    while (!currentDate.isAfter(endDate)) {
      try {
        downloadSverdlovskNotifications(currentDate);
        // Пауза между запросами чтобы не перегружать API
        Thread.sleep(1000);
      } catch (Exception e) {
        log.error("Ошибка при загрузке извещений за {}", currentDate, e);
      }
      currentDate = currentDate.plusDays(1);
    }
  }
}