package ru.ermakovdev.torgigov.service;

import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YandexGeocodingService {

  private final RestTemplate yandexRestTemplate;

  @Value("${yandex.geocoder.url:https://geocode-maps.yandex.ru/1.x}")
  private String yandexGeocoderUrl;

  @Value("${yandex.geocoder.api.key:}")
  private String yandexApiKey;

  @Value("${app.geocoding.enabled:true}")
  private boolean geocodingEnabled;

  @Value("${app.geocoding.delay-ms:100}")
  private long geocodingDelayMs;

  public GeocodingResult geocodeAddress(String address) {
    if (!geocodingEnabled) {
      log.debug("Geocoding is disabled");
      return new GeocodingResult(false, "Geocoding disabled");
    }

    if (yandexApiKey == null || yandexApiKey.trim().isEmpty()) {
      log.warn("Yandex API key is not configured");
      return new GeocodingResult(false, "API key not configured");
    }

    if (address == null || address.trim().isEmpty()) {
      return new GeocodingResult(false, "Empty address");
    }

    try {
      if (geocodingDelayMs > 0) {
        Thread.sleep(geocodingDelayMs);
      }

      // Создаем параметры запроса
      Map<String, String> params = new HashMap<>();
      params.put("apikey", yandexApiKey);
      params.put("geocode", address);
      params.put("format", "json");
      params.put("results", "1");

      // RestTemplate автоматически закодирует параметры
      String url = yandexGeocoderUrl + "?apikey={apikey}&geocode={geocode}&format={format}&results={results}";

      log.debug("Geocoding address: {}", address);

      Map<String, Object> response = yandexRestTemplate.getForObject(url, Map.class, params);

      return parseGeocodingResponse(response, address);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new GeocodingResult(false, "Geocoding interrupted");
    } catch (Exception e) {
      log.error("Error geocoding address '{}': {}", address, e.getMessage());
      return new GeocodingResult(false, "Geocoding error: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private GeocodingResult parseGeocodingResponse(Map<String, Object> response, String originalAddress) {
    try {
      log.debug("=== START PARSING RESPONSE ===");
      log.debug("Raw API response type: {}", response != null ? response.getClass().getSimpleName() : "null");
      log.debug("Response keys: {}", response != null ? response.keySet() : "null");

      if (response == null) {
        return new GeocodingResult(false, "Empty response");
      }

      // Для JSON формата: response -> GeoObjectCollection
      Map<String, Object> responseMap = (Map<String, Object>) response.get("response");
      log.debug("ResponseMap keys: {}", responseMap != null ? responseMap.keySet() : "null");

      if (responseMap == null) {
        return new GeocodingResult(false, "No 'response' field found");
      }

      Map<String, Object> geoObjectCollection = (Map<String, Object>) responseMap.get("GeoObjectCollection");
      log.debug("GeoObjectCollection keys: {}", geoObjectCollection != null ? geoObjectCollection.keySet() : "null");

      if (geoObjectCollection == null) {
        return new GeocodingResult(false, "No GeoObjectCollection found");
      }

      // Проверяем количество найденных результатов
      Map<String, Object> metaDataProperty = (Map<String, Object>) geoObjectCollection.get("metaDataProperty");
      log.debug("MetaDataProperty keys: {}", metaDataProperty != null ? metaDataProperty.keySet() : "null");

      if (metaDataProperty != null) {
        Map<String, Object> geocoderResponseMetaData = (Map<String, Object>) metaDataProperty.get("GeocoderResponseMetaData");
        log.debug("GeocoderResponseMetaData: {}", geocoderResponseMetaData);

        if (geocoderResponseMetaData != null) {
          String found = (String) geocoderResponseMetaData.get("found");
          log.debug("Found results: {}", found);
          if ("0".equals(found)) {
            return new GeocodingResult(false, "Address not found");
          }
        }
      }

      // Получаем список результатов (в JSON это List, а не Map)
      List<Map<String, Object>> featureMember = (List<Map<String, Object>>) geoObjectCollection.get("featureMember");
      log.debug("FeatureMember size: {}", featureMember != null ? featureMember.size() : "null");

      if (featureMember == null || featureMember.isEmpty()) {
        return new GeocodingResult(false, "No features found");
      }

      // Берем первый результат
      Map<String, Object> firstFeature = featureMember.get(0);
      log.debug("FirstFeature keys: {}", firstFeature.keySet());

      Map<String, Object> geoObject = (Map<String, Object>) firstFeature.get("GeoObject");
      log.debug("GeoObject keys: {}", geoObject != null ? geoObject.keySet() : "null");

      if (geoObject == null) {
        return new GeocodingResult(false, "No GeoObject found");
      }

      // Получаем координаты
      Map<String, Object> point = (Map<String, Object>) geoObject.get("Point");
      log.debug("Point: {}", point);

      if (point == null) {
        return new GeocodingResult(false, "No Point found");
      }

      String pos = (String) point.get("pos");
      log.debug("Coordinates string: {}", pos);

      if (pos == null) {
        return new GeocodingResult(false, "No coordinates string");
      }

      // Формат: "lon lat" - ДОЛГОТА ПРОБЕЛ ШИРОТА
      String[] coordinates = pos.split(" ");
      if (coordinates.length != 2) {
        return new GeocodingResult(false, "Invalid coordinates format: " + pos);
      }

      // ИСПРАВЛЕНИЕ: меняем порядок - сначала широта, потом долгота
      double latitude = Double.parseDouble(coordinates[1]);  // ШИРОТА
      double longitude = Double.parseDouble(coordinates[0]); // ДОЛГОТА

      // Получаем форматированный адрес
      String formattedAddress = originalAddress;
      Map<String, Object> metaDataPropertyGeo = (Map<String, Object>) geoObject.get("metaDataProperty");
      if (metaDataPropertyGeo != null) {
        Map<String, Object> geocoderMetaData = (Map<String, Object>) metaDataPropertyGeo.get("GeocoderMetaData");
        if (geocoderMetaData != null) {
          formattedAddress = (String) geocoderMetaData.get("text");
        }
      }

      log.debug("=== SUCCESSFULLY PARSED ===");
      log.info("✅ Successfully geocoded address: {} -> [{}, {}]",
          originalAddress, latitude, longitude);

      return new GeocodingResult(true, "Success", latitude, longitude, formattedAddress);

    } catch (Exception e) {
      log.error("❌ Error parsing geocoding response: {}", e.getMessage());
      log.error("Stack trace:", e);
      return new GeocodingResult(false, "Parse error: " + e.getMessage());
    }
  }


  public static class GeocodingResult {
    private final boolean success;
    private final String message;
    private Double latitude;
    private Double longitude;
    private String formattedAddress;

    // Конструктор для неудачных результатов
    public GeocodingResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    // Конструктор для успешных результатов
    public GeocodingResult(boolean success, String message, Double latitude, Double longitude, String formattedAddress) {
      this.success = success;
      this.message = message;
      this.latitude = latitude;
      this.longitude = longitude;
      this.formattedAddress = formattedAddress;
    }

    // Геттеры
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getFormattedAddress() { return formattedAddress; }
  }
}