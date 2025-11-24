package ru.ermakovdev.torgigov.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "nsi_download_logs")
public class NsiDownloadLog {
  @Id
  private String id;

  private String nsiCode;
  private LocalDateTime downloadDate;
  private String filePath;
  private Long fileSize;
  private String status; // SUCCESS, ERROR
  private String errorMessage;

  public NsiDownloadLog() {
    this.downloadDate = LocalDateTime.now();
  }
}