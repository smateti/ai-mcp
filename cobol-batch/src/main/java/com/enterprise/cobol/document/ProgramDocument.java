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
@Document(indexName = "cobol-programs")
public class ProgramDocument {

    @Id
    private String programId;

    @Field(type = FieldType.Keyword)
    private String programName;

    @Field(type = FieldType.Keyword)
    private String programType;

    @Field(type = FieldType.Keyword)
    private String author;

    @Field(type = FieldType.Integer)
    private int lineCount;

    @Field(type = FieldType.Integer)
    private int paragraphCount;

    @Field(type = FieldType.Boolean)
    private boolean usesCics;

    @Field(type = FieldType.Boolean)
    private boolean usesDb2;

    @Field(type = FieldType.Boolean)
    private boolean usesIdms;

    @Field(type = FieldType.Boolean)
    private boolean usesIms;

    @Field(type = FieldType.Boolean)
    private boolean usesMq;

    @Field(type = FieldType.Keyword)
    private List<String> calledPrograms;

    @Field(type = FieldType.Keyword)
    private List<String> copybooks;

    @Field(type = FieldType.Text)
    private String businessSummary;

    @Field(type = FieldType.Text, index = false)
    private String sourceCode;

    @Field(type = FieldType.Text)
    private List<String> dataStructures;

    @Field(type = FieldType.Text)
    private List<String> sqlStatements;

    @Field(type = FieldType.Text)
    private List<String> conditionNames;

    @Field(type = FieldType.Text)
    private List<String> fileOperations;

    @Field(type = FieldType.Text)
    private List<String> extractedBusinessRules;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime analyzedAt;

    @Field(type = FieldType.Keyword)
    private String batchRunId;

    @Field(type = FieldType.Long)
    private Long projectId;
}
