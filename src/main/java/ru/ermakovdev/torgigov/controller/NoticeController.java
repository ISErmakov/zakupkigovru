package ru.ermakovdev.torgigov.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ermakovdev.torgigov.model.Notice;
import ru.ermakovdev.torgigov.service.NoticeService;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeService noticeService;

  /**
   * Получить все извещения с геокодированными адресами
   * Для отображения на карте
   */
  @GetMapping
  public ResponseEntity<List<Notice>> getAllNotices() {
    try {
      List<Notice> notices = noticeService.getAllNotices();
      return ResponseEntity.ok(notices);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить только извещения с успешным геокодированием
   * Для отображения на карте
   */
  @GetMapping("/geocoded")
  public ResponseEntity<List<Notice>> getGeocodedNotices() {
    try {
      List<Notice> notices = noticeService.getGeocodedNotices();
      return ResponseEntity.ok(notices);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить извещение по ID
   */
  @GetMapping("/{id}")
  public ResponseEntity<Notice> getNoticeById(@PathVariable String id) {
    try {
      return noticeService.getNoticeById(id)
          .map(ResponseEntity::ok)
          .orElse(ResponseEntity.notFound().build());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Получить статистику по извещениям
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getStats() {
    try {
      Map<String, Object> stats = noticeService.getNoticeStats();
      return ResponseEntity.ok(stats);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }
}