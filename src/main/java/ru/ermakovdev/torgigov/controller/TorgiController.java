package ru.ermakovdev.torgigov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ermakovdev.torgigov.service.TorgiDataService;

@RestController
@RequestMapping("/api/torgi")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TorgiController {

  private final TorgiDataService torgiDataService;

  @PostMapping("/process/{action}")
  public ResponseEntity<String> processAction(@PathVariable String action) {
    try {
      torgiDataService.processAction(action);
      return ResponseEntity.ok("Action " + action + " completed successfully");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @GetMapping("/health")
  public ResponseEntity<String> healthCheck() {
    return ResponseEntity.ok("Backend is running!");
  }
}
