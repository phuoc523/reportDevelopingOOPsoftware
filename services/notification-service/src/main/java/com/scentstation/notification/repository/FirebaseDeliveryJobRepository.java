package com.scentstation.notification.repository;

import com.scentstation.notification.entity.FirebaseDeliveryJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirebaseDeliveryJobRepository extends JpaRepository<FirebaseDeliveryJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select job from FirebaseDeliveryJob job
        where job.nextAttemptAt <= :now
          and job.status in :statuses
        order by job.nextAttemptAt, job.id
        """)
    List<FirebaseDeliveryJob> findReady(
        @Param("now") Instant now,
        @Param("statuses") List<FirebaseDeliveryJob.Status> statuses,
        Pageable pageable
    );
}
