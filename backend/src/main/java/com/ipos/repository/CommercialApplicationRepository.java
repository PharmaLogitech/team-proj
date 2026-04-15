package com.ipos.repository;

import com.ipos.entity.CommercialApplication;
import com.ipos.entity.CommercialApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommercialApplicationRepository extends JpaRepository<CommercialApplication, Long> {

    @Query("SELECT a FROM CommercialApplication a LEFT JOIN FETCH a.decidedBy WHERE a.id = :id")
    Optional<CommercialApplication> findByIdWithDecidedBy(@Param("id") Long id);

    Optional<CommercialApplication> findByExternalReferenceId(String externalReferenceId);

    List<CommercialApplication> findByStatusOrderByCreatedAtDesc(CommercialApplicationStatus status);

    List<CommercialApplication> findAllByOrderByCreatedAtDesc();
}
