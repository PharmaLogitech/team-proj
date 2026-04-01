package com.ipos.repository;

import com.ipos.entity.ProductDeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductDeletionLogRepository extends JpaRepository<ProductDeletionLog, Long> {
}
