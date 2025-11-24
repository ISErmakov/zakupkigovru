package ru.ermakovdev.torgigov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ermakovdev.torgigov.service.ZakupkiService;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/admin/notifications") // Перенесем в admin
@RequiredArgsConstructor
public class NotificationController {

  private final ZakupkiService zakupkiService;

  @PostMapping("/sverdlovsk/{date}")
  public ResponseEntity<String> downloadSverdlovskNotifications(
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    try {
      zakupkiService.downloadSverdlovskNotifications(date);
      return ResponseEntity.ok("Загрузка извещений за " + date + " запущена");
    } catch (Exception e) {
      log.error("Ошибка при запуске загрузки извещений", e);
      return ResponseEntity.badRequest().body("Ошибка: " + e.getMessage());
    }
  }

  @PostMapping("/sverdlovsk/period")
  public ResponseEntity<String> downloadSverdlovskNotificationsForPeriod(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    try {
      zakupkiService.downloadSverdlovskNotificationsForPeriod(startDate, endDate);
      return ResponseEntity.ok("Загрузка извещений за период с " + startDate + " по " + endDate + " запущена");
    } catch (Exception e) {
      log.error("Ошибка при запуске загрузки извещений за период", e);
      return ResponseEntity.badRequest().body("Ошибка: " + e.getMessage());
    }
  }
}