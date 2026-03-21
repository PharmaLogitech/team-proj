/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Order endpoints (IPOS-SA-ORD).                   ║
 * ║                                                                              ║
 * ║  WHY:  This is how merchants place orders from the React frontend.           ║
 * ║        The frontend sends a JSON payload with the merchant ID and a list     ║
 * ║        of {productId, quantity} pairs.  This controller parses that          ║
 * ║        payload, converts it into entity objects, and delegates to            ║
 * ║        OrderService.placeOrder() — where the real logic lives.              ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4 — RBAC):                                           ║
 * ║        Enforced in SecurityConfig.java:                                     ║
 * ║        - /api/orders/** → Authenticated (all roles).                       ║
 * ║        Merchants place/track their own orders (ORD-US1).                   ║
 * ║        Admins and Managers can view all orders for oversight.               ║
 * ║                                                                              ║
 * ║  ORD-US1 (merchant isolation):                                               ║
 * ║        The controller extracts the authenticated user's ID and role from    ║
 * ║        the Spring Security context and passes them to the service.          ║
 * ║        If the caller is a MERCHANT, the service forces merchantId to the    ║
 * ║        caller's own ID — merchants cannot order on behalf of others.        ║
 * ║                                                                              ║
 * ║  FUTURE WORK:                                                                ║
 * ║        - Add GET /api/orders/mine for merchant-specific tracking.          ║
 * ║        - Add @PutMapping("/{id}/status") to update order status.           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import com.ipos.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;

    public OrderController(OrderService orderService, UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
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
     * The authenticated user's ID and role are extracted from the security
     * context and passed to the service for ORD-US1 enforcement.
     */
    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        try {
            /*
             * Extract the authenticated user from the Spring Security context.
             * This is guaranteed to exist because SecurityConfig requires
             * authentication for /api/orders/**.
             */
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User caller = userRepository.findByUsername(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            List<OrderItem> items = request.items().stream().map(itemReq -> {
                Product product = new Product();
                product.setId(itemReq.productId());

                OrderItem item = new OrderItem();
                item.setProduct(product);
                item.setQuantity(itemReq.quantity());
                return item;
            }).toList();

            Order savedOrder = orderService.placeOrder(
                    request.merchantId(), items, caller.getId(), caller.getRole());
            return ResponseEntity.ok(savedOrder);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* Represents the full order request from the frontend. */
    record OrderRequest(Long merchantId, List<OrderItemRequest> items) {}

    /* Represents one item within the order request. */
    record OrderItemRequest(Long productId, Integer quantity) {}
}
