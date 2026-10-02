package com.hakimi.aviation.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Document(indexName = "flight_index")
@Data
public class FlightIndexDoc {

    @Id // 对应 MySQL flight 表的主键
    private Long id;

    @Field(type = FieldType.Keyword)
    private String flightNo;

    @Field(type = FieldType.Keyword)
    private String deptCity;

    @Field(type = FieldType.Keyword)
    private String arrCity;

    // MySQL 中的 date 类型，映射为 LocalDate
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd")
    private LocalDate flightDate;

    // 价格放大 100 倍存储为长整型，检索效率极高
    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal totalPrice;

    // 航段起飞时间，映射为 LocalDateTime
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstDeptTime;

    // 航段降落时间，映射为 LocalDateTime
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastArrTime;

}