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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "cobol-dependencies")
public class DependencyDocument {

    @Id
    private String dependencyId;

    @Field(type = FieldType.Keyword)
    private String programId;

    @Field(type = FieldType.Keyword)
    private String programName;

    @Field(type = FieldType.Keyword)
    private String dependencyType;

    @Field(type = FieldType.Keyword)
    private String targetName;

    @Field(type = FieldType.Object)
    private Map<String, Object> details;

    @Field(type = FieldType.Keyword)
    private String callingContext;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime analyzedAt;

    @Field(type = FieldType.Keyword)
    private String batchRunId;

    @Field(type = FieldType.Long)
    private Long projectId;
}
