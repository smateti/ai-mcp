package com.naagi.categoryadmin.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch document for chat/user audit logs.
 * These logs track user interactions with the chat application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "naagi-chat-audit-logs")
public class ChatAuditLogDocument {

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

    @Field(type = FieldType.Text, analyzer = "standard")
    private String userQuestion;

    @Field(type = FieldType.Text, analyzer = "standard")
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

    @Field(type = FieldType.Keyword)
    private String clientIp;

    @Field(type = FieldType.Text)
    private String userAgent;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime timestamp;

    @Field(type = FieldType.Keyword)
    private String environment;
}
