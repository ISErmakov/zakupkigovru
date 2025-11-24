package ru.ermakovdev.torgigov.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "purchase_notifications")
public class PurchaseNotification {
  @Id
  private String id;

  // Основная информация
  private String registrationNumber;
  private String purchaseNumber;
  private String docNumber;
  private String purchaseObjectInfo;

  // Даты
  private LocalDateTime createDateTime;
  private LocalDateTime publishDateTime;
  private LocalDateTime plannedPublishDate;

  // Способ закупки
  private String purchaseMethodCode;
  private String purchaseMethodName;

  // ЭТП
  private String etpCode;
  private String etpName;
  private String etpUrl;

  // Заказчик
  private String customerRegNum;
  private String customerFullName;
  private String customerShortName;
  private String customerInn;
  private String customerKpp;
  private String customerRegion;
  private String customerPostAddress;

  // Геокодированные координаты
  private Double customerLatitude;
  private Double customerLongitude;
  private String customerFormattedAddress;
  private Boolean geocodingSuccess;
  private String geocodingError;

  // Контактная информация
  private String contactPerson;
  private String contactEmail;
  private String contactPhone;

  // Финансовая информация
  private BigDecimal initialPrice;
  private String currency;
  private BigDecimal contractGuarantee;

  // Сроки
  private LocalDateTime applicationStartDate;
  private LocalDateTime applicationEndDate;
  private LocalDateTime biddingDate;
  private LocalDateTime summarizingDate;

  // Объекты закупки
  private List<PurchaseObject> purchaseObjects;

  // Дополнительные поля
  private String status;
  private String href;
  private Boolean notPublishedOnEIS;
  private Boolean contractConclusionOnSt83Ch2;

  // Информация о бюджете
  private String budgetLevel;
  private String financeSource;
  private Integer financeYear;

  // Системные поля
  private LocalDateTime processedAt;
  private String sourceFile;
  private String regionCode;
  private LocalDate notificationDate;
}