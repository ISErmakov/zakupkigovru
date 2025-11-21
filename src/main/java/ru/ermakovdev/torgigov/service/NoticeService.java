package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.model.Notice;
import ru.ermakovdev.torgigov.repository.NoticeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

  private final NoticeRepository noticeRepository;

  /**
   * Получить все извещения
   */
  public List<Notice> getAllNotices() {
    try {
      List<Notice> notices = noticeRepository.findAll();
      log.info("Retrieved {} notices from database", notices.size());
      return notices;
    } catch (Exception e) {
      log.error("Error retrieving all notices", e);
      throw new RuntimeException("Failed to retrieve notices", e);
    }
  }

  /**
   * Получить только извещения с успешным геокодированием
   */
  public List<Notice> getGeocodedNotices() {
    try {
      List<Notice> notices = noticeRepository.findByIsCustomerGeocodedTrue();
      log.info("Retrieved {} geocoded notices from database", notices.size());
      return notices;
    } catch (Exception e) {
      log.error("Error retrieving geocoded notices", e);
      throw new RuntimeException("Failed to retrieve geocoded notices", e);
    }
  }

  /**
   * Получить извещение по ID
   */
  public Optional<Notice> getNoticeById(String id) {
    try {
      return noticeRepository.findById(id);
    } catch (Exception e) {
      log.error("Error retrieving notice by id: {}", id, e);
      throw new RuntimeException("Failed to retrieve notice by id", e);
    }
  }

  /**
   * Получить статистику по извещениям
   */
  public Map<String, Object> getNoticeStats() {
    try {
      Map<String, Object> stats = new HashMap<>();

      long totalNotices = noticeRepository.count();
      long geocodedNotices = noticeRepository.countByIsCustomerGeocodedTrue();
      long noticesWithLots = noticeRepository.countByLotsIsNotNull();

      // Общая сумма всех торгов
      Double totalAmount = noticeRepository.findAll().stream()
          .filter(notice -> notice.getTotalAmount() != null)
          .mapToDouble(Notice::getTotalAmount)
          .sum();

      // Средняя сумма
      double averageAmount = totalAmount / Math.max(noticesWithLots, 1);

      stats.put("totalNotices", totalNotices);
      stats.put("geocodedNotices", geocodedNotices);
      stats.put("noticesWithLots", noticesWithLots);
      stats.put("totalAmount", totalAmount);
      stats.put("averageAmount", averageAmount);
      stats.put("geocodingSuccessRate", (double) geocodedNotices / Math.max(totalNotices, 1) * 100);

      log.info("Calculated stats: {} total, {} geocoded, {} with lots, total amount: {}",
          totalNotices, geocodedNotices, noticesWithLots, totalAmount);

      return stats;
    } catch (Exception e) {
      log.error("Error calculating notice stats", e);
      throw new RuntimeException("Failed to calculate notice stats", e);
    }
  }

  /**
   * Найти извещения по региону
   */
  public List<Notice> getNoticesByRegion(String region) {
    try {
      // Ищем в отформатированном адресе или исходном адресе
      List<Notice> notices = noticeRepository.findByCustomerFormattedAddressContainingIgnoreCase(region);
      log.info("Found {} notices for region: {}", notices.size(), region);
      return notices;
    } catch (Exception e) {
      log.error("Error retrieving notices by region: {}", region, e);
      throw new RuntimeException("Failed to retrieve notices by region", e);
    }
  }
}