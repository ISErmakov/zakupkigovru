package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.ermakovdev.torgigov.model.Notice;
import ru.ermakovdev.torgigov.repository.NoticeRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorgiDataService {

  private final NoticeRepository noticeRepository;
  private final RestTemplate restTemplate;
  private final YandexGeocodingService geocodingService;

  private static final String TORGI_API_URL = "https://torgi.gov.ru/new/opendata/7710568760-notice/data-20251120T0000-20251121T0000-structure-20240401.json";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

  public void processAction(String action) {
    log.info("Processing action: {}", action);

    switch (action) {
      case "1":
        downloadAndProcessNotifications();
        break;
      case "2":
        loadLotsForAllNotices(); // Исправлено: загрузка лотов
        break;
      case "3":
        linkPartnersToMembers();
        break;
      case "4":
        geocodeKladrAddresses();
        break;
      case "5":
        generateGeoJson();
        break;
      default:
        throw new IllegalArgumentException("Unknown action: " + action);
    }
  }

  private void downloadAndProcessNotifications() {
    log.info("Starting download and processing of notifications from: {}", TORGI_API_URL);

    try {
      // 1. Загружаем мета-данные
      var responseType = new ParameterizedTypeReference<Map<String, Object>>() {};
      Map<String, Object> apiResponse = restTemplate.exchange(
          TORGI_API_URL,
          HttpMethod.GET,
          null,
          responseType
      ).getBody();

      log.info("Successfully received JSON response");

      // 2. Извлекаем список объектов
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> listObjects = (List<Map<String, Object>>) apiResponse.get("listObjects");

      if (listObjects == null) {
        log.warn("No 'listObjects' found in response");
        return;
      }

      log.info("Found {} total objects in response", listObjects.size());

      // 3. Фильтруем извещения за последний день
      List<Map<String, Object>> recentNoticesData = listObjects.stream()
          .filter(data -> {
            try {
              String publishDateStr = (String) data.get("publishDate");
              String documentType = (String) data.get("documentType");
              LocalDateTime publishDate = LocalDateTime.parse(publishDateStr, DATE_FORMATTER);
              LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
              return publishDate.isAfter(yesterday) && "notice".equals(documentType);
            } catch (Exception e) {
              return false;
            }
          })
          .toList();

      log.info("Filtered {} recent notices from the last day", recentNoticesData.size());

      // 4. ОГРАНИЧИВАЕМ до 30 записей для разработки
      List<Map<String, Object>> limitedNoticesData = recentNoticesData.stream()
          .limit(30)
          .toList();

      log.info("Limited to {} notices for development", limitedNoticesData.size());

      // 5. Обрабатываем каждое извещение (мета-данные + детали)
      int savedCount = 0;
      int duplicateCount = 0;
      int errorCount = 0;

      for (Map<String, Object> noticeData : limitedNoticesData) {
        try {
          String regNum = (String) noticeData.get("regNum");

          // Проверяем дубликат
          if (noticeRepository.existsByRegNum(regNum)) {
            duplicateCount++;
            log.debug("Skipped duplicate notice: {}", regNum);
            continue;
          }

          // Создаем базовый объект Notice из мета-данных
          Notice notice = convertToNotice(noticeData);

          // Загружаем и парсим детальную информацию
          enrichNoticeWithDetails(notice);

          // Сохраняем полный объект
          noticeRepository.save(notice);
          savedCount++;
          log.debug("Saved complete notice: {}", regNum);

        } catch (Exception e) {
          errorCount++;
          log.error("Failed to process notice {}: {}", noticeData.get("regNum"), e.getMessage());
        }
      }

      log.info("Processing completed. Saved: {}, Duplicates: {}, Errors: {}",
          savedCount, duplicateCount, errorCount);

    } catch (Exception e) {
      log.error("Error downloading and processing notifications", e);
      throw new RuntimeException("Failed to download and process notifications", e);
    }
  }

  private Notice convertToNotice(Map<String, Object> data) {
    Notice notice = new Notice();
    notice.setRegNum((String) data.get("regNum"));
    notice.setBidderOrgCode((String) data.get("bidderOrgCode"));
    notice.setRightHolderCode((String) data.get("rightHolderCode"));
    notice.setDocumentType((String) data.get("documentType"));
    notice.setPublishDate((String) data.get("publishDate"));
    notice.setBiddTypeCode((String) data.get("biddTypeCode"));
    notice.setOwnershipFormsCode((String) data.get("ownershipFormsCode"));
    notice.setSubjectEstateCode((String) data.get("subjectEstateCode"));
    notice.setSubjectRightHolderCode((String) data.get("subjectRightHolderCode"));
    notice.setHref((String) data.get("href"));
    notice.setIngestedAt(LocalDateTime.now());
    notice.setIsProcessed(false); // Пока не обработаны детали

    return notice;
  }

  private void enrichNoticeWithDetails(Notice notice) {
    try {
      log.debug("Loading detailed info for notice: {}", notice.getRegNum());

      // Загружаем детальный JSON
      Map<String, Object> detailedData = restTemplate.getForObject(
          notice.getHref(),
          Map.class
      );

      if (detailedData != null) {
        // Извлекаем информацию о заказчике
        extractCustomerInfo(notice, detailedData);

        // Извлекаем информацию о лотах
        extractLotsInfo(notice, detailedData);

        notice.setIsProcessed(true);
        log.debug("Successfully enriched notice with details: {}", notice.getRegNum());
      }

    } catch (Exception e) {
      log.warn("Failed to enrich notice {} with details: {}", notice.getRegNum(), e.getMessage());
      // Не прерываем выполнение - сохраняем хотя бы мета-данные
    }
  }

  private void extractCustomerInfo(Notice notice, Map<String, Object> detailedData) {
    try {
      // Исправленный парсинг структуры JSON
      @SuppressWarnings("unchecked")
      Map<String, Object> exportObject = (Map<String, Object>) detailedData.get("exportObject");

      if (exportObject != null) {
        @SuppressWarnings("unchecked")
        Map<String, Object> structuredObject = (Map<String, Object>) exportObject.get("structuredObject");

        if (structuredObject != null) {
          @SuppressWarnings("unchecked")
          Map<String, Object> noticeData = (Map<String, Object>) structuredObject.get("notice");

          if (noticeData != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bidderOrg = (Map<String, Object>) noticeData.get("bidderOrg");

            if (bidderOrg != null) {
              @SuppressWarnings("unchecked")
              Map<String, Object> orgInfo = (Map<String, Object>) bidderOrg.get("orgInfo");

              if (orgInfo != null) {
                notice.setCustomerName((String) orgInfo.get("name"));
                String address = (String) orgInfo.get("legalAddress");
                if (address == null) {
                  address = (String) orgInfo.get("actualAddress");
                }
                notice.setCustomerAddress(address);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to extract customer info for notice {}: {}", notice.getRegNum(), e.getMessage());
    }
  }

  private void extractLotsInfo(Notice notice, Map<String, Object> detailedData) {
    try {
      // Исправленный парсинг лотов
      @SuppressWarnings("unchecked")
      Map<String, Object> exportObject = (Map<String, Object>) detailedData.get("exportObject");

      if (exportObject != null) {
        @SuppressWarnings("unchecked")
        Map<String, Object> structuredObject = (Map<String, Object>) exportObject.get("structuredObject");

        if (structuredObject != null) {
          @SuppressWarnings("unchecked")
          Map<String, Object> noticeData = (Map<String, Object>) structuredObject.get("notice");

          if (noticeData != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lotsData = (List<Map<String, Object>>) noticeData.get("lots");

            if (lotsData != null && !lotsData.isEmpty()) {
              List<Notice.Lot> lots = lotsData.stream()
                  .map(lotData -> {
                    Notice.Lot lot = new Notice.Lot();

                    // Исправленные названия полей согласно реальному JSON
                    Object lotNumber = lotData.get("lotNumber");
                    if (lotNumber != null) {
                      lot.setLotNumber(String.valueOf(lotNumber));
                    }

                    lot.setLotName((String) lotData.get("lotName"));
                    lot.setLotDescription((String) lotData.get("lotDescription"));

                    // Получаем цену из priceMin
                    String priceMin = (String) lotData.get("priceMin");
                    if (priceMin != null) {
                      try {
                        lot.setLotAmount(Double.parseDouble(priceMin));
                      } catch (NumberFormatException e) {
                        log.warn("Invalid priceMin format for lot: {}", priceMin);
                      }
                    }

                    // Получаем валюту
                    @SuppressWarnings("unchecked")
                    Map<String, Object> currency = (Map<String, Object>) lotData.get("currency");
                    if (currency != null) {
                      lot.setCurrency((String) currency.get("name"));
                    }

                    return lot;
                  })
                  .toList();

              notice.setLots(lots);
              notice.setTotalLots(lots.size());

              // Рассчитываем общую сумму
              double totalAmount = lots.stream()
                  .filter(lot -> lot.getLotAmount() != null)
                  .mapToDouble(Notice.Lot::getLotAmount)
                  .sum();
              notice.setTotalAmount(totalAmount);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to extract lots info for notice {}: {}", notice.getRegNum(), e.getMessage());
    }
  }

  // Метод для загрузки лотов для одного notice
  private boolean loadLotsForNotice(Notice notice) {
    try {
      log.debug("Loading lots for notice: {}", notice.getRegNum());

      // Загружаем детальный JSON
      Map<String, Object> detailedData = restTemplate.getForObject(
          notice.getHref(),
          Map.class
      );

      if (detailedData != null) {
        // Извлекаем информацию о лотах
        extractLotsInfo(notice, detailedData);

        // Сохраняем обновленный notice с лотами
        noticeRepository.save(notice);
        log.info("Successfully loaded {} lots for notice {}",
            notice.getLots() != null ? notice.getLots().size() : 0,
            notice.getRegNum());
        return true;
      }

    } catch (Exception e) {
      log.error("Failed to load lots for notice {}: {}", notice.getRegNum(), e.getMessage());
    }

    return false;
  }

  private void loadLotsForAllNotices() {
    log.info("Starting to load lots for all notices");

    try {
      // Находим все notices, у которых нет лотов
      List<Notice> noticesWithoutLots = noticeRepository.findByLotsIsNull();

      log.info("Found {} notices to process", noticesWithoutLots.size());

      int successCount = 0;
      int errorCount = 0;

      for (Notice notice : noticesWithoutLots) {
        try {
          if (loadLotsForNotice(notice)) {
            successCount++;
            log.debug("Successfully loaded lots for notice: {}", notice.getRegNum());
          } else {
            errorCount++;
            log.warn("Failed to load lots for notice: {}", notice.getRegNum());
          }
        } catch (Exception e) {
          errorCount++;
          log.error("Error loading lots for notice {}: {}", notice.getRegNum(), e.getMessage());
        }
      }

      log.info("Lots loading completed. Success: {}, Errors: {}", successCount, errorCount);

    } catch (Exception e) {
      log.error("Error in loadLotsForAllNotices", e);
      throw new RuntimeException("Failed to load lots for notices", e);
    }
  }

  // Остальные методы остаются как заготовки
  private void processNotificationDetails() {
    log.info("Processing notification details - for manual reprocessing");
  }

  private void linkPartnersToMembers() {
    log.info("Linking partners to members");
  }

  private void geocodeKladrAddresses() {
    log.info("Starting geocoding of customer addresses");

    try {
      // Находим notices с адресом заказчика, но без координат
      List<Notice> noticesWithAddress = noticeRepository.findByCustomerAddressIsNotNullAndIsCustomerGeocodedFalse();

      // Ограничиваем для разработки
      List<Notice> limitedNotices = noticesWithAddress.stream()
          .limit(10)
          .toList();

      log.info("Found {} notices with customer addresses, processing {}",
          noticesWithAddress.size(), limitedNotices.size());

      int totalProcessed = 0;
      int totalGeocoded = 0;
      int totalErrors = 0;

      for (Notice notice : limitedNotices) {
        try {
          totalProcessed++;

          String address = notice.getCustomerAddress();
          if (address == null || address.trim().isEmpty()) {
            log.warn("Empty customer address for notice: {}", notice.getRegNum());
            notice.setCustomerGeocodingError("Empty address");
            noticeRepository.save(notice);
            totalErrors++;
            continue;
          }

          // Выполняем геокодирование
          YandexGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(address);

          if (result.isSuccess()) {
            // Сохраняем координаты в notice
            notice.setCustomerLatitude(result.getLatitude());
            notice.setCustomerLongitude(result.getLongitude());
            notice.setCustomerFormattedAddress(result.getFormattedAddress());
            notice.setIsCustomerGeocoded(true);
            notice.setCustomerGeocodingError(null);

            totalGeocoded++;

            log.info("✅ Successfully geocoded customer address for notice {}: {} -> [{}, {}]",
                notice.getRegNum(), address, result.getLatitude(), result.getLongitude());
          } else {
            notice.setCustomerGeocodingError(result.getMessage());
            totalErrors++;
            log.warn("❌ Failed to geocode customer address for notice {}: {} - {}",
                notice.getRegNum(), address, result.getMessage());
          }

          // Сохраняем notice в любом случае (обновляем статус геокодирования)
          noticeRepository.save(notice);

        } catch (Exception e) {
          totalErrors++;
          log.error("🚨 Error geocoding customer address for notice {}: {}",
              notice.getRegNum(), e.getMessage());
        }
      }

      log.info("🎯 Customer address geocoding completed. Total: {} notices, ✅ Geocoded: {}, ❌ Errors: {}",
          totalProcessed, totalGeocoded, totalErrors);

    } catch (Exception e) {
      log.error("💥 Error in geocodeKladrAddresses", e);
    }
  }

  private String buildGeocodingAddress(Notice.Lot lot, Notice notice) {
    // Пробуем разные варианты адреса в порядке приоритета

    // 1. Используем описание лота (часто содержит полный адрес)
    if (lot.getLotDescription() != null && !lot.getLotDescription().trim().isEmpty()) {
      String description = lot.getLotDescription().trim();
      // Ищем в описании упоминание адреса
      if (description.contains("адресу:") || description.contains("расположенный")) {
        return extractAddressFromDescription(description);
      }
      return description;
    }

    // 2. Используем название лота
    if (lot.getLotName() != null && !lot.getLotName().trim().isEmpty()) {
      return lot.getLotName().trim();
    }

    // 3. Используем адрес заказчика как fallback
    if (notice.getCustomerAddress() != null && !notice.getCustomerAddress().trim().isEmpty()) {
      return notice.getCustomerAddress().trim();
    }

    return null;
  }

  private String extractAddressFromDescription(String description) {
    // Простая логика извлечения адреса из описания
    String[] markers = {"адресу:", "расположенный", "адрес:", "по адресу"};

    for (String marker : markers) {
      int index = description.indexOf(marker);
      if (index != -1) {
        String addressPart = description.substring(index + marker.length()).trim();
        // Берем первую часть до точки или запятой
        String[] separators = {".", ",", ";", "Срок", "Площадь"};
        for (String sep : separators) {
          int sepIndex = addressPart.indexOf(sep);
          if (sepIndex != -1) {
            return addressPart.substring(0, sepIndex).trim();
          }
        }
        return addressPart;
      }
    }

    return description;
  }

  private void generateGeoJson() {
    log.info("Generating GeoJSON");
  }
}