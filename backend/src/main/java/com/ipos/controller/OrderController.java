/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Order endpoints (IPOS-SA-ORD).                   ║
 * ║                                                                              ║
 * ║  WHY:  This is how merchants place and track orders from the React          ║
 * ║        frontend.  The controller delegates business logic to OrderService.  ║
 * ║                                                                              ║
 * ║  ENDPOINTS:                                                                  ║
 * ║        GET  /api/orders              Role-scoped order list (ORD-US2).      ║
 * ║             MERCHANT: own orders only.  MANAGER/ADMIN: all orders.          ║
 * ║        POST /api/orders              Place a new order (ORD-US1).           ║
 * ║        PUT  /api/orders/{id}/status  Update order status (ORD-US2).         ║
 * ║             MANAGER/ADMIN only.  Validates lifecycle transitions.            ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4, RBAC):                                             ║
 * ║        SecurityConfig.java:                                                  ║
 * ║        - PUT .../orders/{id}/status  MANAGER, ADMIN                          ║
 * ║        - /api/orders/ (all)          Authenticated (all roles)               ║
 * ║        ORD-US1 merchant isolation enforced in OrderService.                  ║
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /**
     * GET /api/orders -- role-scoped order listing (ORD-US2).
     * MERCHANT sees only their own orders; MANAGER/ADMIN see all.
     */
    @GetMapping
    public List<Order> getOrders(Authentication auth) {
        User caller = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return orderService.findOrdersForActor(caller.getId(), caller.getRole());
    }

    /*
     * POST /api/orders -- Place a new order.
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
             * authentication for /api/orders.
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

    /**
     * PUT /api/orders/{id}/status -- advance order lifecycle (ORD-US2).
     * MANAGER / ADMIN only (SecurityConfig URL rule + @PreAuthorize defence-in-depth).
     * When transitioning to DISPATCHED, optional shipping fields are accepted.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody UpdateOrderStatusRequest request) {
        try {
            Order updated = orderService.updateOrderStatus(id, request.status(),
                    request.courierName(), request.courierReference(),
                    request.dispatchDate(), request.expectedDeliveryDate());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* Represents the full order request from the frontend. */
    record OrderRequest(Long merchantId, List<OrderItemRequest> items) {}

    /* Represents one item within the order request. */
    record OrderItemRequest(Long productId, Integer quantity) {}

    /** Status update payload for PUT /api/orders/{id}/status. */
    record UpdateOrderStatusRequest(Order.OrderStatus status,
                                    String courierName,
                                    String courierReference,
                                    java.time.LocalDate dispatchDate,
                                    java.time.LocalDate expectedDeliveryDate) {}
}
