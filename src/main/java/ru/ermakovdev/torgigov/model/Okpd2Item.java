package ru.ermakovdev.torgigov.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "okpd2_items")
public class Okpd2Item {

  @Id
  private String id;

  @Field("internal_id")
  private String internalId; // oos:id из XML

  @Field("code")
  private String code; // oos:code из XML

  @Field("name")
  private String name; // oos:name из XML

  @Field("parent_code")
  private String parentCode; // oos:parentCode из XML

  @Field("actual")
  private Boolean actual; // oos:actual из XML

  @Field("create_date_time")
  private String createDateTime;

  @Field("modify_date_time")
  private String modifyDateTime;

  // Конструкторы
  public Okpd2Item() {}

  public Okpd2Item(String code, String name, Boolean actual) {
    this.code = code;
    this.name = name;
    this.actual = actual;
  }
}