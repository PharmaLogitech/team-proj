/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Order endpoints.                                 ║
 * ║                                                                              ║
 * ║  WHY:  This is how merchants place orders from the React frontend.           ║
 * ║        The frontend sends a JSON payload with the merchant ID and a list     ║
 * ║        of {productId, quantity} pairs.  This controller parses that          ║
 * ║        payload, converts it into entity objects, and delegates to            ║
 * ║        OrderService.placeOrder() — where the real logic lives.              ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add @GetMapping("/{id}") to view a specific order.                ║
 * ║        - Add @PutMapping("/{id}/status") to update order status.           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /* GET /api/orders → Returns all orders (with their items, thanks to cascade). */
    @GetMapping
    public List<Order> findAll() {
        return orderService.findAll();
    }

    /*
     * POST /api/orders → Place a new order.
     *
     * Expected JSON body:
     * {
     *   "merchantId": 1,
     *   "items": [
     *     { "productId": 10, "quantity": 3 },
     *     { "productId": 20, "quantity": 1 }
     *   ]
     * }
     *
     * We use Map<String, Object> + a nested OrderItemRequest record instead of
     * accepting the Order entity directly.  Why?  Because the frontend knows
     * product IDs, not full Product objects.  We need a simple DTO (Data
     * Transfer Object) to bridge the gap.
     *
     * ResponseEntity<?>  lets us return different HTTP status codes:
     *   200 OK — order created successfully, body = the saved Order.
     *   400 Bad Request — something went wrong (out of stock, bad id, etc.).
     */
    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        try {
            // Convert the lightweight DTOs into entity objects that the service expects.
            List<OrderItem> items = request.items().stream().map(itemReq -> {
                Product product = new Product();
                product.setId(itemReq.productId());

                OrderItem item = new OrderItem();
                item.setProduct(product);
                item.setQuantity(itemReq.quantity());
                return item;
            }).toList();

            Order savedOrder = orderService.placeOrder(request.merchantId(), items);
            return ResponseEntity.ok(savedOrder);

        } catch (RuntimeException e) {
            /*
             * If the service throws (e.g., insufficient stock), we catch it
             * here and return a 400 with the error message.
             * In production you'd use @ControllerAdvice for global exception
             * handling, but this is simpler for learning.
             */
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /*
     * ── Inner DTO Records ────────────────────────────────────────────────────
     *
     * Java Records (introduced in Java 16) are perfect for DTOs.
     * A record auto-generates: constructor, getters, equals, hashCode, toString.
     * They're immutable — once created, their fields can't change.
     *
     * We define them as inner classes because they're only used by this
     * controller.  In larger projects you'd put DTOs in a separate package.
     */

    /* Represents the full order request from the frontend. */
    record OrderRequest(Long merchantId, List<OrderItemRequest> items) {}

    /* Represents one item within the order request. */
    record OrderItemRequest(Long productId, Integer quantity) {}
}
