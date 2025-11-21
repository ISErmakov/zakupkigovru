package ru.ermakovdev.torgigov.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ru.ermakovdev.torgigov.model.Notice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends MongoRepository<Notice, String> {

  Optional<Notice> findByRegNum(String regNum);

  List<Notice> findByIsProcessedFalse();

  @Query("{ 'parsedPublishDate': { $gte: ?0 } }")
  List<Notice> findRecentNotices(LocalDateTime date);

  boolean existsByRegNum(String regNum);

  List<Notice> findByLotsIsNull();

  List<Notice> findByLotsIsNotNull();

  List<Notice> findByCustomerAddressIsNotNullAndIsCustomerGeocodedFalse();

  List<Notice> findByIsCustomerGeocodedTrue();

  long countByIsCustomerGeocodedTrue();

  long countByLotsIsNotNull();

  List<Notice> findByCustomerFormattedAddressContainingIgnoreCase(String region);
}