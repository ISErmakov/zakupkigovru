package ru.ermakovdev.torgigov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ermakovdev.torgigov.dto.GeocodedNoticeDto;
import ru.ermakovdev.torgigov.model.Okpd2Item;
import ru.ermakovdev.torgigov.service.NoticeService;
import ru.ermakovdev.torgigov.service.NotificationMongoService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notices") // Отдельный путь для фронта
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class NoticeController {

  private final NotificationMongoService notificationMongoService;
  private final NoticeService noticeService;

  /**
   * Получить все извещения для отображения на карте
   * GET /api/notices
   */
  @GetMapping
  public ResponseEntity<List<GeocodedNoticeDto>> getAllNotices() {
    try {
      log.info("Запрос всех извещений для карты");
      List<GeocodedNoticeDto> notices = notificationMongoService.getGeocodedNotices();
      log.info("Возвращено {} извещений", notices.size());
      return ResponseEntity.ok(notices);
    } catch (Exception e) {
      log.error("Ошибка при получении всех извещений", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  @GetMapping("/api/notices/okpd2")
  public List<Okpd2Item> getOkpd2Codes() {
    return null;
  }

  /**
   * Получить только извещения с успешным геокодированием
   * GET /api/notices/geocoded
   */
  @GetMapping("/geocoded")
  public ResponseEntity<List<GeocodedNoticeDto>> getGeocodedNotices() {
    try {
      log.info("Запрос геокодированных извещений");
      List<GeocodedNoticeDto> notices = notificationMongoService.getGeocodedNotices();

      // Фильтруем только те, у которых есть координаты
      List<GeocodedNoticeDto> geocodedNotices = notices.stream()
          .filter(notice -> notice.getCustomerLatitude() != null && notice.getCustomerLongitude() != null)
          .toList();

      log.info("Возвращено {} геокодированных извещений (из {})",
          geocodedNotices.size(), notices.size());
      return ResponseEntity.ok(geocodedNotices);
    } catch (Exception e) {
      log.error("Ошибка при получении геокодированных извещений", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить извещения по региону
   * GET /api/notices/region/66
   */
  @GetMapping("/region/{regionCode}")
  public ResponseEntity<List<GeocodedNoticeDto>> getNoticesByRegion(@PathVariable String regionCode) {
    try {
      log.info("Запрос извещений для региона: {}", regionCode);
      List<GeocodedNoticeDto> notices = notificationMongoService.getGeocodedNoticesByRegion(regionCode);
      log.info("Возвращено {} извещений для региона {}", notices.size(), regionCode);
      return ResponseEntity.ok(notices);
    } catch (Exception e) {
      log.error("Ошибка при получении извещений для региона {}", regionCode, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить извещение по ID
   * GET /api/notices/12345
   */
  @GetMapping("/{id}")
  public ResponseEntity<GeocodedNoticeDto> getNoticeById(@PathVariable String id) {
    try {
      log.info("Запрос извещения по ID: {}", id);
      return notificationMongoService.getNoticeById(id)
          .map(ResponseEntity::ok)
          .orElse(ResponseEntity.notFound().build());
    } catch (Exception e) {
      log.error("Ошибка при получении извещения по ID: {}", id, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить статистику по извещениям
   * GET /api/notices/stats
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getStats() {
    try {
      log.info("Запрос статистики по извещениям");
      Map<String, Object> stats = notificationMongoService.getNoticeStats();
      return ResponseEntity.ok(stats);
    } catch (Exception e) {
      log.error("Ошибка при получении статистики", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Поиск извещений по названию заказчика
   * GET /api/notices/search?customer=кировград
   */
  @GetMapping("/search")
  public ResponseEntity<List<GeocodedNoticeDto>> searchNotices(
      @RequestParam(required = false) String customer,
      @RequestParam(required = false) String product) {
    try {
      log.info("Поиск извещений: customer={}, product={}", customer, product);
      // TODO: Добавить метод поиска в NotificationMongoService
      List<GeocodedNoticeDto> allNotices = notificationMongoService.getGeocodedNotices();

      List<GeocodedNoticeDto> filteredNotices = allNotices.stream()
          .filter(notice -> {
            boolean matches = true;
            if (customer != null && !customer.isEmpty()) {
              matches = notice.getCustomerName().toLowerCase().contains(customer.toLowerCase());
            }
            if (matches && product != null && !product.isEmpty()) {
              matches = notice.getLots().stream()
                  .anyMatch(lot -> lot.getLotName().toLowerCase().contains(product.toLowerCase()));
            }
            return matches;
          })
          .toList();

      log.info("Найдено {} извещений по поисковому запросу", filteredNotices.size());
      return ResponseEntity.ok(filteredNotices);
    } catch (Exception e) {
      log.error("Ошибка при поиске извещений", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}