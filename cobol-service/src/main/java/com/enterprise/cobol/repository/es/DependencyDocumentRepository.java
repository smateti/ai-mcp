package com.enterprise.cobol.repository.es;

import com.enterprise.cobol.document.DependencyDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface DependencyDocumentRepository extends ElasticsearchRepository<DependencyDocument, String> {

    List<DependencyDocument> findByProgramId(String programId);

    List<DependencyDocument> findByProgramIdAndDependencyType(String programId, String dependencyType);

    List<DependencyDocument> findByBatchRunId(String batchRunId);
}
