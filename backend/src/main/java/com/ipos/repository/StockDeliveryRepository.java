package com.ipos.repository;

import com.ipos.entity.StockDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockDeliveryRepository extends JpaRepository<StockDelivery, Long> {

    /** All deliveries for a product in reverse-chronological order (most recent first). */
    List<StockDelivery> findByProduct_IdOrderByDeliveryDateDesc(Long productId);
}
