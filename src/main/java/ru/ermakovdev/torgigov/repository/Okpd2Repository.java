package ru.ermakovdev.torgigov.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ru.ermakovdev.torgigov.model.Okpd2Item;

@Repository
public interface Okpd2Repository extends MongoRepository<Okpd2Item, String> {
  // Можно добавить кастомные методы запросов, если нужно
  // Например, поиск по коду
  Okpd2Item findByCode(String code);

  // Поиск актуальных записей
  List<Okpd2Item> findByActualTrue();
}