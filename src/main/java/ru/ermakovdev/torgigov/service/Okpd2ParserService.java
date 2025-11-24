package ru.ermakovdev.torgigov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import ru.ermakovdev.torgigov.model.Okpd2Item;

@Slf4j
@Service
public class Okpd2ParserService {

  public List<Okpd2Item> parseXmlFile(String xmlFilePath) {
    List<Okpd2Item> items = new ArrayList<>();

    try {
      File xmlFile = new File(xmlFilePath);
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true); // ВАЖНО: включаем поддержку namespace
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(xmlFile);

      // Ищем элементы с namespace
      NodeList itemNodes = document.getElementsByTagNameNS(
          "http://zakupki.gov.ru/oos/types/1", "nsiOKPD2");

      // Если не нашли с namespace, пробуем без него
      if (itemNodes.getLength() == 0) {
        itemNodes = document.getElementsByTagName("nsiOKPD2");
      }

      log.info("Найдено {} элементов nsiOKPD2 в файле {}", itemNodes.getLength(), xmlFilePath);

      for (int i = 0; i < itemNodes.getLength(); i++) {
        Element itemElement = (Element) itemNodes.item(i);
        Okpd2Item item = parseOkpd2Element(itemElement);
        if (item != null) {
          items.add(item);
        }
      }

      log.info("Из файла {} извлечено {} записей ОКПД2", xmlFilePath, items.size());

    } catch (Exception e) {
      log.error("Ошибка парсинга XML файла {}: {}", xmlFilePath, e.getMessage(), e);
    }

    return items;
  }

  private Okpd2Item parseOkpd2Element(Element element) {
    try {
      Okpd2Item item = new Okpd2Item();

      // Парсим с учетом namespace
      item.setCode(getElementText(element, "oos:code"));
      if (item.getCode() == null) item.setCode(getElementText(element, "code"));

      item.setName(getElementText(element, "oos:name"));
      if (item.getName() == null) item.setName(getElementText(element, "name"));

      item.setActual(getElementBoolean(element, "oos:actual"));
      if (item.getActual() == null) item.setActual(getElementBoolean(element, "actual"));

      // Дополнительные поля
      item.setId(getElementText(element, "oos:id"));
      if (item.getId() == null) item.setId(getElementText(element, "id"));

      item.setParentCode(getElementText(element, "oos:parentCode"));
      if (item.getParentCode() == null) item.setParentCode(getElementText(element, "parentCode"));

      // Если не нашли код и название, логируем для отладки
      if (item.getCode() == null || item.getName() == null) {
        log.warn("Элемент без кода или названия: code={}, name={}", item.getCode(), item.getName());
        return null;
      }

      return item;

    } catch (Exception e) {
      log.warn("Ошибка парсинга элемента: {}", e.getMessage());
      return null;
    }
  }

  private String getElementText(Element parent, String tagName) {
    try {
      // Сначала пробуем с namespace
      NodeList nodes = parent.getElementsByTagNameNS("http://zakupki.gov.ru/oos/types/1", tagName);
      if (nodes.getLength() == 0) {
        // Пробуем без namespace
        nodes = parent.getElementsByTagName(tagName);
      }
      return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private Boolean getElementBoolean(Element parent, String tagName) {
    String text = getElementText(parent, tagName);
    if (text != null) {
      return "true".equalsIgnoreCase(text) || "1".equals(text);
    }
    return null;
  }
}