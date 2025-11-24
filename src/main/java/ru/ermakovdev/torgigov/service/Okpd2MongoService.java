package ru.ermakovdev.torgigov.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import ru.ermakovdev.torgigov.model.Okpd2Item;
import ru.ermakovdev.torgigov.repository.Okpd2Repository;

@Slf4j
@Service
@RequiredArgsConstructor
public class Okpd2MongoService {

  private final MongoTemplate mongoTemplate;
  private final Okpd2Repository okpd2Repository;

  public void saveAll(List<Okpd2Item> items) {
    if (items.isEmpty()) {
      log.info("Нет данных для сохранения");
      return;
    }

    try {
      log.info("Начинаем сохранение {} записей в MongoDB...", items.size());

      // Сохраняем все данные
      List<Okpd2Item> savedItems = okpd2Repository.saveAll(items);

      log.info("✅ Успешно сохранено {} записей ОКПД2 в MongoDB", savedItems.size());

    } catch (Exception e) {
      log.error("❌ Ошибка при сохранении данных в MongoDB: {}", e.getMessage(), e);
      throw new RuntimeException("Ошибка сохранения в MongoDB", e);
    }
  }

  public long getCount() {
    long count = okpd2Repository.count();
    log.info("Текущее количество записей в MongoDB: {}", count);
    return count;
  }
}