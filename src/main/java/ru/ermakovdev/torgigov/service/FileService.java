package ru.ermakovdev.torgigov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
public class FileService {

  @Value("${app.zakupki.download-dir:./downloads/nsi}")
  private String downloadDir;

  public String saveArchiveFile(byte[] content, String fileName, String nsiCode, int partNumber) throws IOException {
    // Создаем директорию если не существует
    Path downloadPath = Paths.get(downloadDir);
    Files.createDirectories(downloadPath);

    // Сохраняем файл
    Path filePath = downloadPath.resolve(fileName);
    Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    log.info("Архив {} успешно сохранен: {} (размер: {} байт)",
        partNumber + 1, filePath, content.length);

    // Проверяем сигнатуру ZIP файла
    checkZipSignature(content, filePath);

    return filePath.toString();
  }

  private void checkZipSignature(byte[] content, Path filePath) {
    if (content.length >= 4) {
      if (content[0] == 0x50 && content[1] == 0x4B && content[2] == 0x03 && content[3] == 0x04) {
        log.debug("✅ Файл {} является валидным ZIP архивом", filePath.getFileName());
      } else {
        log.warn("⚠️ Файл {} не имеет ZIP сигнатуры. Первые байты: {} {} {} {}",
            filePath.getFileName(),
            String.format("%02X", content[0] & 0xFF),
            String.format("%02X", content[1] & 0xFF),
            String.format("%02X", content[2] & 0xFF),
            String.format("%02X", content[3] & 0xFF));
      }
    }
  }
}