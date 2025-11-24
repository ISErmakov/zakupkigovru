package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.dto.GeocodedNoticeDto;
import ru.ermakovdev.torgigov.dto.LotDto;
import ru.ermakovdev.torgigov.model.PurchaseNotification;
import ru.ermakovdev.torgigov.model.PurchaseObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMongoService {

  private final MongoTemplate mongoTemplate;

  public void saveNotifications(List<PurchaseNotification> notifications) {
    if (notifications == null || notifications.isEmpty()) {
      log.info("Нет извещений для сохранения");
      return;
    }

    try {
      mongoTemplate.insertAll(notifications);
      log.info("Сохранено {} извещений в MongoDB", notifications.size());
    } catch (Exception e) {
      log.error("Ошибка при сохранении извещений в MongoDB", e);
      throw new RuntimeException("Ошибка сохранения извещений", e);
    }
  }

  public long getCount() {
    return mongoTemplate.count(new Query(), PurchaseNotification.class);
  }

  public List<PurchaseNotification> findByRegion(String region) {
    Query query = new Query(Criteria.where("regionCode").is(region));
    return mongoTemplate.find(query, PurchaseNotification.class);
  }

  public List<PurchaseNotification> findByPurchaseNumber(String purchaseNumber) {
    Query query = new Query(Criteria.where("purchaseNumber").is(purchaseNumber));
    return mongoTemplate.find(query, PurchaseNotification.class);
  }

  public void deleteAll() {
    mongoTemplate.remove(new Query(), PurchaseNotification.class);
    log.info("Все извещения удалены из MongoDB");
  }

  /**
   * Получить все извещения в формате для фронтенда
   */
  public List<GeocodedNoticeDto> getGeocodedNotices() {
    try {
      List<PurchaseNotification> notifications = mongoTemplate.findAll(PurchaseNotification.class);
      log.info("Найдено {} извещений для преобразования в DTO", notifications.size());

      return notifications.stream()
          .map(this::convertToGeocodedDto)
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("Ошибка при получении геокодированных извещений", e);
      return Collections.emptyList();
    }
  }

  /**
   * Получить извещения по региону в формате для фронтенда
   */
  public List<GeocodedNoticeDto> getGeocodedNoticesByRegion(String regionCode) {
    try {
      Query query = new Query(Criteria.where("regionCode").is(regionCode));
      List<PurchaseNotification> notifications = mongoTemplate.find(query, PurchaseNotification.class);
      log.info("Найдено {} извещений для региона {}", notifications.size(), regionCode);

      return notifications.stream()
          .map(this::convertToGeocodedDto)
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("Ошибка при получении извещений для региона {}", regionCode, e);
      return Collections.emptyList();
    }
  }

  /**
   * Получить извещение по ID
   */
  public Optional<GeocodedNoticeDto> getNoticeById(String id) {
    try {
      PurchaseNotification notification = mongoTemplate.findById(id, PurchaseNotification.class);
      if (notification != null) {
        return Optional.of(convertToGeocodedDto(notification));
      }
      return Optional.empty();
    } catch (Exception e) {
      log.error("Ошибка при получении извещения по ID: {}", id, e);
      return Optional.empty();
    }
  }

  /**
   * Получить базовую статистику
   */
  public Map<String, Object> getNoticeStats() {
    try {
      long totalNotices = getCount();

      // Базовая статистика
      Map<String, Object> stats = new HashMap<>();
      stats.put("totalNotices", totalNotices);
      stats.put("lastUpdate", LocalDateTime.now());

      // Если есть извещения, добавляем дополнительную статистику
      if (totalNotices > 0) {
        List<PurchaseNotification> allNotifications = mongoTemplate.findAll(PurchaseNotification.class);

        // Статистика по регионам
        Map<String, Long> regionStats = allNotifications.stream()
            .filter(n -> n.getRegionCode() != null)
            .collect(Collectors.groupingBy(
                PurchaseNotification::getRegionCode,
                Collectors.counting()
            ));
        stats.put("regions", regionStats);

        // Статистика по способам закупки
        Map<String, Long> methodStats = allNotifications.stream()
            .filter(n -> n.getPurchaseMethodName() != null)
            .collect(Collectors.groupingBy(
                PurchaseNotification::getPurchaseMethodName,
                Collectors.counting()
            ));
        stats.put("purchaseMethods", methodStats);

        // Общая сумма закупок
        BigDecimal totalAmount = allNotifications.stream()
            .filter(n -> n.getInitialPrice() != null)
            .map(PurchaseNotification::getInitialPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalAmount", totalAmount);

        // Статистика геокодирования
        long geocodedCount = allNotifications.stream()
            .filter(n -> n.getCustomerLatitude() != null && n.getCustomerLongitude() != null)
            .count();
        stats.put("geocodedNotices", geocodedCount);
        stats.put("geocodingSuccessRate", totalNotices > 0 ?
            (double) geocodedCount / totalNotices * 100 : 0);
      }

      return stats;

    } catch (Exception e) {
      log.error("Ошибка при получении статистики", e);
      return Map.of(
          "error", "Не удалось получить статистику",
          "message", e.getMessage()
      );
    }
  }

  /**
   * Конвертировать PurchaseNotification в GeocodedNoticeDto
   */
  private GeocodedNoticeDto convertToGeocodedDto(PurchaseNotification notification) {
    GeocodedNoticeDto dto = new GeocodedNoticeDto();

    // Основная информация
    dto.set_id(notification.getId());
    dto.setRegNum(notification.getPurchaseNumber());
    dto.setCustomerName(notification.getCustomerFullName());
    dto.setCustomerAddress(notification.getCustomerPostAddress());
    dto.setPublishDate(notification.getPublishDateTime());

    // Координаты (уже геокодированы на этапе парсинга)
    dto.setCustomerLatitude(notification.getCustomerLatitude());
    dto.setCustomerLongitude(notification.getCustomerLongitude());
    dto.setCustomerFormattedAddress(notification.getCustomerFormattedAddress());

    // Лоты
    dto.setLots(convertLots(notification.getPurchaseObjects()));
    dto.setTotalLots(notification.getPurchaseObjects() != null ?
        notification.getPurchaseObjects().size() : 0);
    dto.setTotalAmount(notification.getInitialPrice());

    return dto;
  }

  private List<LotDto> convertLots(List<PurchaseObject> purchaseObjects) {
    if (purchaseObjects == null) {
      return Collections.emptyList();
    }

    return purchaseObjects.stream()
        .map(this::convertToLotDto)
        .collect(Collectors.toList());
  }

  private LotDto convertToLotDto(PurchaseObject purchaseObject) {
    LotDto lot = new LotDto();
    lot.setLotNumber(purchaseObject.getSid() != null ? purchaseObject.getSid() : "1");
    lot.setLotName(purchaseObject.getName());
    lot.setLotDescription(purchaseObject.getOkpd2Name());
    lot.setLotAmount(purchaseObject.getSum());
    lot.setCurrency("RUB");
    lot.setOkpd2(purchaseObject.getOkpd2Code());
    return lot;
  }
}