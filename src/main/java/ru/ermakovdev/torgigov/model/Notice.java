package ru.ermakovdev.torgigov.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "notices")
public class Notice {

  @Id
  private String id;

  @Indexed(unique = true)
  private String regNum;

  // Основные поля из мета-данных
  private String bidderOrgCode;
  private String rightHolderCode;
  private String documentType;
  private String publishDate;
  private String biddTypeCode;
  private String ownershipFormsCode;
  private String subjectEstateCode;
  private String subjectRightHolderCode;
  private String href;

  // Информация о заказчике
  private String customerName;
  private String customerAddress;

  // Координаты заказчика
  private Double customerLatitude;
  private Double customerLongitude;
  private String customerFormattedAddress;
  private Boolean isCustomerGeocoded = false;
  private String customerGeocodingError;

  // Лоты
  private List<Lot> lots;
  private Integer totalLots;
  private Double totalAmount;
  private Boolean isProcessed = false;
  private LocalDateTime ingestedAt;

  @Data
  public static class Lot {
    private String lotNumber;
    private String lotName;
    private String lotDescription;
    private Double lotAmount;
    private String currency;
    private String okpd2;

    // Конструкторы
    public Lot() {}

    public Lot(String lotNumber, String lotName, Double lotAmount, String currency) {
      this.lotNumber = lotNumber;
      this.lotName = lotName;
      this.lotAmount = lotAmount;
      this.currency = currency;
    }
  }
}