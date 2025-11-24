package ru.ermakovdev.torgigov.repository;

import ru.ermakovdev.torgigov.model.NsiDownloadLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NsiDownloadLogRepository extends MongoRepository<NsiDownloadLog, String> {
}