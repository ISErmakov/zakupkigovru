package ru.ermakovdev.torgigov.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class PurchaseObject {
  private String sid;
  private String okpd2Code;           // Код ОКПД2
  private String okpd2Name;           // Наименование ОКПД2
  private String name;                // Наименование товара/услуги
  private String okeiCode;            // Код ОКЕИ
  private String okeiName;            // Наименование ОКЕИ
  private BigDecimal price;           // Цена за единицу
  private BigDecimal quantity;        // Количество
  private BigDecimal sum;             // Сумма
  private String type;                // Тип (PRODUCT/SERVICE)
}
