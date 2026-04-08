package com.ipos.dto;

/**
 * One product row for stock turnover (RPT-US5): sold vs received quantities.
 */
public class StockTurnoverRowDto {

    private Long productId;
    private String productCode;
    private long quantitySold;
    private long quantityReceived;

    public StockTurnoverRowDto() {
    }

    public StockTurnoverRowDto(Long productId, String productCode, long quantitySold, long quantityReceived) {
        this.productId = productId;
        this.productCode = productCode;
        this.quantitySold = quantitySold;
        this.quantityReceived = quantityReceived;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public long getQuantitySold() {
        return quantitySold;
    }

    public void setQuantitySold(long quantitySold) {
        this.quantitySold = quantitySold;
    }

    public long getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(long quantityReceived) {
        this.quantityReceived = quantityReceived;
    }
}
