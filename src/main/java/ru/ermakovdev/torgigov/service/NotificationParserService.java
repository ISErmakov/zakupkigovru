package ru.ermakovdev.torgigov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.ermakovdev.torgigov.model.PurchaseNotification;
import ru.ermakovdev.torgigov.model.PurchaseObject;
import ru.ermakovdev.torgigov.service.YandexGeocodingService.GeocodingResult;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationParserService {

  private final YandexGeocodingService geocodingService;

  public List<PurchaseNotification> parseNotificationFile(File xmlFile) {
    List<PurchaseNotification> notifications = new ArrayList<>();

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(xmlFile);

      // Ищем все извещения в файле
      NodeList notificationList = document.getElementsByTagNameNS("*", "epNotificationEF2020");

      for (int i = 0; i < notificationList.getLength(); i++) {
        Element notificationElement = (Element) notificationList.item(i);
        PurchaseNotification notification = parseNotification(notificationElement);
        if (notification != null) {
          // ГЕОКОДИРОВАНИЕ на этапе парсинга
          geocodeNotification(notification);

          notification.setSourceFile(xmlFile.getName());
          notification.setProcessedAt(LocalDateTime.now());
          notifications.add(notification);
          log.debug("Успешно распарсено и геокодировано извещение: {}", notification.getPurchaseNumber());
        }
      }

    } catch (Exception e) {
      log.error("Ошибка при парсинге файла извещения: {}", xmlFile.getName(), e);
    }

    return notifications;
  }

  /**
   * Геокодирование извещения
   */
  private void geocodeNotification(PurchaseNotification notification) {
    if (notification.getCustomerPostAddress() == null ||
        notification.getCustomerPostAddress().trim().isEmpty()) {
      notification.setGeocodingSuccess(false);
      notification.setGeocodingError("Адрес не указан");
      return;
    }

    try {
      log.debug("Геокодирование адреса заказчика: {}", notification.getCustomerPostAddress());
      GeocodingResult result = geocodingService.geocodeAddress(notification.getCustomerPostAddress());

      if (result.isSuccess()) {
        notification.setCustomerLatitude(result.getLatitude());
        notification.setCustomerLongitude(result.getLongitude());
        notification.setCustomerFormattedAddress(result.getFormattedAddress());
        notification.setGeocodingSuccess(true);
        log.debug("✅ Успешно геокодирован адрес: {} -> [{}, {}]",
            notification.getCustomerPostAddress(), result.getLatitude(), result.getLongitude());
      } else {
        notification.setGeocodingSuccess(false);
        notification.setGeocodingError(result.getMessage());
        log.warn("❌ Не удалось геокодировать адрес: {} - {}",
            notification.getCustomerPostAddress(), result.getMessage());
      }

    } catch (Exception e) {
      log.error("Ошибка при геокодировании адреса: {}", notification.getCustomerPostAddress(), e);
      notification.setGeocodingSuccess(false);
      notification.setGeocodingError("Ошибка геокодирования: " + e.getMessage());
    }
  }

  private PurchaseNotification parseNotification(Element notificationElement) {
    try {
      PurchaseNotification notification = new PurchaseNotification();
      notification.setId(UUID.randomUUID().toString());

      // Основная информация
      notification.setRegistrationNumber(getElementText(notificationElement, "id"));
      notification.setPurchaseNumber(getElementText(notificationElement, "purchaseNumber"));
      notification.setDocNumber(getElementText(notificationElement, "docNumber"));
      notification.setPurchaseObjectInfo(getElementText(notificationElement, "purchaseObjectInfo"));

      // Даты
      notification.setCreateDateTime(parseDateTime(getElementText(notificationElement, "directDT")));
      notification.setPublishDateTime(parseDateTime(getElementText(notificationElement, "publishDTInEIS")));
      notification.setPlannedPublishDate(parseDateTime(getElementText(notificationElement, "plannedPublishDate")));

      // Способ закупки
      Element placingWay = getElement(notificationElement, "placingWay");
      if (placingWay != null) {
        notification.setPurchaseMethodCode(getElementText(placingWay, "code"));
        notification.setPurchaseMethodName(getElementText(placingWay, "name"));
      }

      // ЭТП
      Element etp = getElement(notificationElement, "ETP");
      if (etp != null) {
        notification.setEtpCode(getElementText(etp, "code"));
        notification.setEtpName(getElementText(etp, "name"));
        notification.setEtpUrl(getElementText(etp, "url"));
      }

      // Информация о заказчике
      Element responsibleOrg = getElement(notificationElement, "responsibleOrgInfo");
      if (responsibleOrg != null) {
        notification.setCustomerRegNum(getElementText(responsibleOrg, "regNum"));
        notification.setCustomerFullName(getElementText(responsibleOrg, "fullName"));
        notification.setCustomerShortName(getElementText(responsibleOrg, "shortName"));
        notification.setCustomerInn(getElementText(responsibleOrg, "INN"));
        notification.setCustomerKpp(getElementText(responsibleOrg, "KPP"));
        notification.setCustomerPostAddress(getElementText(responsibleOrg, "postAddress"));

        // Извлекаем регион из адреса
        String address = notification.getCustomerPostAddress();
        if (address != null && address.contains("Свердловская")) {
          notification.setCustomerRegion("66");
          notification.setRegionCode("66");
        }
      }

      // Контактная информация
      Element responsibleInfo = getElement(notificationElement, "responsibleInfo");
      if (responsibleInfo != null) {
        Element contactPerson = getElement(responsibleInfo, "contactPersonInfo");
        if (contactPerson != null) {
          String lastName = getElementText(contactPerson, "lastName");
          String firstName = getElementText(contactPerson, "firstName");
          String middleName = getElementText(contactPerson, "middleName");
          notification.setContactPerson(String.format("%s %s %s",
              lastName != null ? lastName : "",
              firstName != null ? firstName : "",
              middleName != null ? middleName : "").trim());
        }
        notification.setContactEmail(getElementText(responsibleInfo, "contactEMail"));
        notification.setContactPhone(getElementText(responsibleInfo, "contactPhone"));
      }

      // Ссылка
      notification.setHref(getElementText(notificationElement, "href"));

      // Флаги
      notification.setNotPublishedOnEIS("true".equals(getElementText(notificationElement, "notPublishedOnEIS")));
      notification.setContractConclusionOnSt83Ch2("true".equals(getElementText(notificationElement, "contractConclusionOnSt83Ch2")));

      // Парсим основную информацию об извещении
      parseNotificationInfo(notificationElement, notification);

      // Парсим объекты закупки
      parsePurchaseObjects(notificationElement, notification);

      return notification;

    } catch (Exception e) {
      log.error("Ошибка при парсинге извещения", e);
      return null;
    }
  }

  private void parseNotificationInfo(Element notificationElement, PurchaseNotification notification) {
    try {
      Element notificationInfo = getElement(notificationElement, "notificationInfo");
      if (notificationInfo == null) return;

      // Сроки подачи заявок
      Element collectingInfo = getElement(notificationInfo, "collectingInfo");
      if (collectingInfo != null) {
        notification.setApplicationStartDate(parseDateTime(getElementText(collectingInfo, "startDT")));
        notification.setApplicationEndDate(parseDateTime(getElementText(collectingInfo, "endDT")));
      }

      // Даты торгов и подведения итогов
      notification.setBiddingDate(parseDateTime(getElementText(notificationInfo, "biddingDate")));
      notification.setSummarizingDate(parseDateTime(getElementText(notificationInfo, "summarizingDate")));

      // Цена
      Element contractConditions = getElement(notificationInfo, "contractConditionsInfo");
      if (contractConditions != null) {
        Element maxPriceInfo = getElement(contractConditions, "maxPriceInfo");
        if (maxPriceInfo != null) {
          String priceStr = getElementText(maxPriceInfo, "maxPrice");
          if (priceStr != null && !priceStr.isEmpty()) {
            notification.setInitialPrice(new BigDecimal(priceStr));
          }

          Element currency = getElement(maxPriceInfo, "currency");
          if (currency != null) {
            notification.setCurrency(getElementText(currency, "code"));
          }
        }
      }

      // Обеспечение контракта
      Element customerRequirements = getElement(notificationInfo, "customerRequirementsInfo");
      if (customerRequirements != null) {
        Element customerRequirement = getElement(customerRequirements, "customerRequirementInfo");
        if (customerRequirement != null) {
          Element contractGuarantee = getElement(customerRequirement, "contractGuarantee");
          if (contractGuarantee != null) {
            String guaranteeStr = getElementText(contractGuarantee, "amount");
            if (guaranteeStr != null && !guaranteeStr.isEmpty()) {
              notification.setContractGuarantee(new BigDecimal(guaranteeStr));
            }
          }
        }
      }

    } catch (Exception e) {
      log.error("Ошибка при парсинге notificationInfo", e);
    }
  }

  private void parsePurchaseObjects(Element notificationElement, PurchaseNotification notification) {
    try {
      List<PurchaseObject> purchaseObjects = new ArrayList<>();

      Element notificationInfo = getElement(notificationElement, "notificationInfo");
      if (notificationInfo == null) return;

      Element purchaseObjectsInfo = getElement(notificationInfo, "purchaseObjectsInfo");
      if (purchaseObjectsInfo == null) return;

      Element notDrugPurchaseObjects = getElement(purchaseObjectsInfo, "notDrugPurchaseObjectsInfo");
      if (notDrugPurchaseObjects == null) return;

      NodeList purchaseObjectList = notDrugPurchaseObjects.getElementsByTagNameNS("*", "purchaseObject");

      for (int i = 0; i < purchaseObjectList.getLength(); i++) {
        Element purchaseObjectElement = (Element) purchaseObjectList.item(i);
        PurchaseObject purchaseObject = new PurchaseObject();

        purchaseObject.setSid(purchaseObjectElement.getAttribute("sid"));
        purchaseObject.setName(getElementText(purchaseObjectElement, "name"));

        // ОКПД2
        Element okpd2 = getElement(purchaseObjectElement, "OKPD2");
        if (okpd2 != null) {
          purchaseObject.setOkpd2Code(getElementText(okpd2, "OKPDCode"));
          purchaseObject.setOkpd2Name(getElementText(okpd2, "OKPDName"));
        }

        // ОКЕИ
        Element okei = getElement(purchaseObjectElement, "OKEI");
        if (okei != null) {
          purchaseObject.setOkeiCode(getElementText(okei, "code"));
          purchaseObject.setOkeiName(getElementText(okei, "name"));
        }

        // Цена и количество
        String priceStr = getElementText(purchaseObjectElement, "price");
        if (priceStr != null && !priceStr.isEmpty()) {
          purchaseObject.setPrice(new BigDecimal(priceStr));
        }

        Element quantity = getElement(purchaseObjectElement, "quantity");
        if (quantity != null) {
          String quantityStr = getElementText(quantity, "value");
          if (quantityStr != null && !quantityStr.isEmpty()) {
            purchaseObject.setQuantity(new BigDecimal(quantityStr));
          }
        }

        String sumStr = getElementText(purchaseObjectElement, "sum");
        if (sumStr != null && !sumStr.isEmpty()) {
          purchaseObject.setSum(new BigDecimal(sumStr));
        }

        purchaseObject.setType(getElementText(purchaseObjectElement, "type"));

        purchaseObjects.add(purchaseObject);
      }

      notification.setPurchaseObjects(purchaseObjects);

    } catch (Exception e) {
      log.error("Ошибка при парсинге объектов закупки", e);
    }
  }

  // Вспомогательные методы для работы с XML
  private String getElementText(Element element, String tagName) {
    try {
      NodeList nodes = element.getElementsByTagNameNS("*", tagName);
      if (nodes.getLength() > 0) {
        return nodes.item(0).getTextContent().trim();
      }
    } catch (Exception e) {
      log.trace("Элемент {} не найден", tagName);
    }
    return null;
  }

  private Element getElement(Element element, String tagName) {
    try {
      NodeList nodes = element.getElementsByTagNameNS("*", tagName);
      if (nodes.getLength() > 0) {
        return (Element) nodes.item(0);
      }
    } catch (Exception e) {
      log.trace("Элемент {} не найден", tagName);
    }
    return null;
  }

  private LocalDateTime parseDateTime(String dateTimeStr) {
    if (dateTimeStr == null || dateTimeStr.isEmpty()) {
      return null;
    }

    try {
      // Пробуем разные форматы дат
      DateTimeFormatter[] formatters = {
          DateTimeFormatter.ISO_DATE_TIME,
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      };

      for (DateTimeFormatter formatter : formatters) {
        try {
          return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
          // Пробуем следующий формат
        }
      }

      // Если не удалось распарсить как LocalDateTime, пробуем как LocalDate
      try {
        return LocalDate.parse(dateTimeStr, DateTimeFormatter.ISO_DATE).atStartOfDay();
      } catch (Exception e) {
        // Игнорируем
      }

    } catch (Exception e) {
      log.debug("Не удалось распарсить дату: {}", dateTimeStr);
    }
    return null;
  }
}