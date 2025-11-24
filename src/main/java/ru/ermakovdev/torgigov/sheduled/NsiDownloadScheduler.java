package ru.ermakovdev.torgigov.scheduler;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import ru.ermakovdev.torgigov.service.ZakupkiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NsiDownloadScheduler {

  private final ZakupkiService zakupkiService;

  // Запуск каждый день в 3:00 ночи
  @Scheduled(cron = "0 0 3 * * ?")
  public void downloadOkpd2Daily() {
    log.info("Запуск ежедневной загрузки справочника ОКПД2");

    try {
      zakupkiService.downloadNsiOkpd2();
      log.info("Ежедневная загрузка справочника ОКПД2 завершена успешно");
    } catch (Exception e) {
      log.error("Ошибка при ежедневной загрузке справочника ОКПД2: {}", e.getMessage(), e);
    }
  }


//  @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
//  public void downloadOnStartup() {
//    log.info("Тестовая загрузка справочника ОКПД2 при старте приложения");
//    try {
//      zakupkiService.downloadNsiOkpd2();
//    } catch (Exception e) {
//      log.error("Ошибка при тестовой загрузке: {}", e.getMessage(), e);
//    }
//  }
//
//  // Альтернативный вариант: использование ApplicationReadyEvent
//  @EventListener(ApplicationReadyEvent.class)
//  public void downloadOnStartupAlternative() {
//    log.info("Загрузка справочника ОКПД2 после полного запуска приложения");
//    try {
//      // Ждем 5 секунд после старта
//      Thread.sleep(5000);
//      zakupkiService.downloadNsiOkpd2();
//    } catch (Exception e) {
//      log.error("Ошибка при загрузке после старта: {}", e.getMessage(), e);
//    }
//  }
}