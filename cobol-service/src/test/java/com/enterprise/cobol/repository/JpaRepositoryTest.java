package com.enterprise.cobol.repository;

import com.enterprise.cobol.entity.AnalysisJob;
import com.enterprise.cobol.entity.Project;
import com.enterprise.cobol.repository.jpa.AnalysisJobRepository;
import com.enterprise.cobol.repository.jpa.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JpaRepositoryTest {

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private AnalysisJobRepository jobRepo;

    @Test
    void testCreateAndFindProject() {
        Project p = Project.builder()
                .name("CardDemo")
                .description("Test project")
                .basePath("/path/to/carddemo")
                .programsSubPath("cbl")
                .copybooksSubPath("cpy")
                .build();
        p = projectRepo.save(p);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getCreatedAt()).isNotNull();

        Optional<Project> found = projectRepo.findById(p.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("CardDemo");
    }

    @Test
    void testFindByName() {
        Project p = Project.builder()
                .name("UniqueProject")
                .basePath("/path")
                .programsSubPath("cbl")
                .copybooksSubPath("cpy")
                .build();
        projectRepo.save(p);

        Optional<Project> found = projectRepo.findByName("UniqueProject");
        assertThat(found).isPresent();
    }

    @Test
    void testExistsByName() {
        Project p = Project.builder()
                .name("ExistCheck")
                .basePath("/path")
                .programsSubPath("cbl")
                .copybooksSubPath("cpy")
                .build();
        projectRepo.save(p);

        assertThat(projectRepo.existsByName("ExistCheck")).isTrue();
        assertThat(projectRepo.existsByName("NonExistent")).isFalse();
    }

    @Test
    void testCreateAndFindJob() {
        AnalysisJob job = AnalysisJob.builder()
                .projectId(1L)
                .folderPath("/path/to/programs")
                .copybookPath("/path/to/copybooks")
                .status("RUNNING")
                .progress(0)
                .batchRunId("run-123")
                .startedAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);

        assertThat(job.getId()).isNotNull();

        Optional<AnalysisJob> found = jobRepo.findById(job.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("RUNNING");
        assertThat(found.get().getBatchRunId()).isEqualTo("run-123");
    }

    @Test
    void testFindByProjectId() {
        AnalysisJob job1 = AnalysisJob.builder()
                .projectId(10L).folderPath("/p1").status("COMPLETED")
                .startedAt(LocalDateTime.now()).build();
        AnalysisJob job2 = AnalysisJob.builder()
                .projectId(10L).folderPath("/p2").status("RUNNING")
                .startedAt(LocalDateTime.now().plusSeconds(1)).build();
        AnalysisJob job3 = AnalysisJob.builder()
                .projectId(20L).folderPath("/p3").status("COMPLETED")
                .startedAt(LocalDateTime.now()).build();
        jobRepo.saveAll(List.of(job1, job2, job3));

        List<AnalysisJob> project10Jobs = jobRepo.findByProjectId(10L);
        assertThat(project10Jobs).hasSize(2);

        List<AnalysisJob> project20Jobs = jobRepo.findByProjectId(20L);
        assertThat(project20Jobs).hasSize(1);
    }

    @Test
    void testFindByProjectIdOrderByStartedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        AnalysisJob older = AnalysisJob.builder()
                .projectId(10L).folderPath("/p").status("COMPLETED")
                .startedAt(now.minusHours(1)).build();
        AnalysisJob newer = AnalysisJob.builder()
                .projectId(10L).folderPath("/p").status("RUNNING")
                .startedAt(now).build();
        jobRepo.saveAll(List.of(older, newer));

        List<AnalysisJob> jobs = jobRepo.findByProjectIdOrderByStartedAtDesc(10L);
        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).getStatus()).isEqualTo("RUNNING"); // newer first
    }

    @Test
    void testFindByBatchRunId() {
        AnalysisJob job = AnalysisJob.builder()
                .projectId(1L).folderPath("/p").status("COMPLETED")
                .batchRunId("unique-run-id")
                .startedAt(LocalDateTime.now()).build();
        jobRepo.save(job);

        Optional<AnalysisJob> found = jobRepo.findByBatchRunId("unique-run-id");
        assertThat(found).isPresent();
        assertThat(found.get().getBatchRunId()).isEqualTo("unique-run-id");

        Optional<AnalysisJob> notFound = jobRepo.findByBatchRunId("nonexistent");
        assertThat(notFound).isEmpty();
    }

    @Test
    void testFindAllByOrderByStartedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        AnalysisJob j1 = AnalysisJob.builder()
                .folderPath("/p1").status("COMPLETED")
                .startedAt(now.minusMinutes(10)).build();
        AnalysisJob j2 = AnalysisJob.builder()
                .folderPath("/p2").status("RUNNING")
                .startedAt(now).build();
        jobRepo.saveAll(List.of(j1, j2));

        List<AnalysisJob> all = jobRepo.findAllByOrderByStartedAtDesc();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getStatus()).isEqualTo("RUNNING"); // newer first
    }

    @Test
    void testJobWithRunLabel() {
        AnalysisJob job = AnalysisJob.builder()
                .projectId(1L)
                .folderPath("/p")
                .status("RUNNING")
                .runLabel("After refactor")
                .startedAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);

        Optional<AnalysisJob> found = jobRepo.findById(job.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRunLabel()).isEqualTo("After refactor");
    }
}
