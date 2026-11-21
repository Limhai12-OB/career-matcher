package com.careermatcher.job;

import com.careermatcher.job.JobEnums.DataOrigin;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

  long countByDataOrigin(DataOrigin origin);

  boolean existsByDataOriginAndSourceName(DataOrigin origin, String sourceName);
}
