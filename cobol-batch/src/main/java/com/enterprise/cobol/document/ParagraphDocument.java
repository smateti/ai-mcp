package com.enterprise.cobol.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "cobol-paragraphs")
public class ParagraphDocument {

    @Id
    private String paragraphId;

    @Field(type = FieldType.Keyword)
    private String programId;

    @Field(type = FieldType.Keyword)
    private String programName;

    @Field(type = FieldType.Keyword)
    private String paragraphName;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Text, index = false)
    private String sourceCode;

    @Field(type = FieldType.Text)
    private String businessSummary;

    @Field(type = FieldType.Integer)
    private int startLine;

    @Field(type = FieldType.Integer)
    private int endLine;

    @Field(type = FieldType.Integer)
    private int lineCount;

    @Field(type = FieldType.Keyword)
    private List<String> performsCalls;

    @Field(type = FieldType.Boolean)
    private boolean hasExecSql;

    @Field(type = FieldType.Boolean)
    private boolean hasExecCics;

    @Field(type = FieldType.Boolean)
    private boolean hasCallStatement;

    @Field(type = FieldType.Text)
    private List<String> businessRules;

    @Field(type = FieldType.Text)
    private List<String> dataAccess;

    @Field(type = FieldType.Text)
    private List<String> calculations;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime analyzedAt;

    @Field(type = FieldType.Keyword)
    private String batchRunId;

    @Field(type = FieldType.Long)
    private Long projectId;
}
