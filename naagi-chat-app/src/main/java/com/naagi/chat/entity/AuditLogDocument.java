package com.naagi.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "naagi-audit-logs")
public class AuditLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String messageId;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Text)
    private String userQuestion;

    @Field(type = FieldType.Text)
    private String systemPrompt;

    @Field(type = FieldType.Text)
    private String assistantResponse;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String intent;

    @Field(type = FieldType.Keyword)
    private String selectedTool;

    @Field(type = FieldType.Double)
    private Double confidence;

    @Field(type = FieldType.Long)
    private Long processingTimeMs;

    @Field(type = FieldType.Integer)
    private Integer inputTokens;

    @Field(type = FieldType.Integer)
    private Integer outputTokens;

    @Field(type = FieldType.Boolean)
    private Boolean success;

    @Field(type = FieldType.Text)
    private String errorMessage;

    @Field(type = FieldType.Text)
    private String errorStackTrace;

    @Field(type = FieldType.Keyword)
    private String clientIp;

    @Field(type = FieldType.Keyword)
    private String userAgent;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime timestamp;
}
