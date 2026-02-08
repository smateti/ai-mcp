package com.enterprise.cobol.repository.es;

import com.enterprise.cobol.document.ParagraphDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ParagraphDocumentRepository extends ElasticsearchRepository<ParagraphDocument, String> {

    List<ParagraphDocument> findByProgramId(String programId);

    List<ParagraphDocument> findByBatchRunId(String batchRunId);
}
