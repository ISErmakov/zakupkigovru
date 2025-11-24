package ru.ermakovdev.torgigov.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeocodedNoticeDto {
  private String _id;
  private String regNum;
  private String customerName;
  private String customerAddress;
  private Double customerLatitude;
  private Double customerLongitude;
  private String customerFormattedAddress;
  private Integer totalLots;
  private BigDecimal totalAmount;
  private LocalDateTime publishDate;
  private List<LotDto> lots;
}

