package ru.ermakovdev.torgigov.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LotDto {
  private String lotNumber;
  private String lotName;
  private String lotDescription;
  private BigDecimal lotAmount;
  private String currency;
  private String okpd2;
}
