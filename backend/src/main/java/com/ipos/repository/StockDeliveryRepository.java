package com.ipos.repository;

import com.ipos.entity.StockDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockDeliveryRepository extends JpaRepository<StockDelivery, Long> {

    /** All deliveries for a product in reverse-chronological order (most recent first). */
    List<StockDelivery> findByProduct_IdOrderByDeliveryDateDesc(Long productId);

    /**
     * RPT-US5 — Sum received quantities per product for deliveries with business date in [start, end].
     */
    @Query("SELECT sd.product.id AS productId, sd.product.productCode AS productCode, "
            + "COALESCE(SUM(sd.quantityReceived), 0) AS qtyReceived FROM StockDelivery sd "
            + "WHERE sd.deliveryDate >= :start AND sd.deliveryDate <= :end "
            + "GROUP BY sd.product.id, sd.product.productCode")
    List<StockDeliveryQtyReceivedProjection> sumQuantityReceivedByProductBetween(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
