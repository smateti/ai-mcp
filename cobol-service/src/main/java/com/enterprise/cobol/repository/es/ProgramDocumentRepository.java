package com.enterprise.cobol.repository.es;

import com.enterprise.cobol.document.ProgramDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramDocumentRepository extends ElasticsearchRepository<ProgramDocument, String> {

    Optional<ProgramDocument> findByProgramName(String programName);

    List<ProgramDocument> findByProgramType(String programType);

    List<ProgramDocument> findByBatchRunId(String batchRunId);

    List<ProgramDocument> findByBatchRunIdAndProgramType(String batchRunId, String programType);
}
