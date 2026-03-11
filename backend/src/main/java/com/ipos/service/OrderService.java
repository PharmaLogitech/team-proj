/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Order-related business logic.                      ║
 * ║                                                                              ║
 * ║  WHY:  THIS IS THE MOST IMPORTANT SERVICE IN PHASE 1.                       ║
 * ║        Creating an order is NOT a simple save — it involves:                 ║
 * ║          1. Looking up the merchant (user).                                  ║
 * ║          2. For each requested item, looking up the product.                 ║
 * ║          3. Checking that enough stock exists.                               ║
 * ║          4. Reducing the product's availabilityCount.                        ║
 * ║          5. Saving the order with all its items.                             ║
 * ║        All of this must happen ATOMICALLY (all-or-nothing) inside a          ║
 * ║        database transaction.  If step 4 fails for the second item, the      ║
 * ║        stock reduction from step 4 of the first item must be rolled back.   ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add an updateOrderStatus() method.                                 ║
 * ║        - Add cancellation logic that RESTORES stock.                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  THE CORE BUSINESS OPERATION: Place an Order
     * ══════════════════════════════════════════════════════════════════════════
     *
     * @Transactional — THIS IS CRUCIAL.  Here's what it does:
     *
     *   1. Before this method runs, Spring opens a DATABASE TRANSACTION.
     *   2. Every database operation inside this method (reads, writes)
     *      happens within that single transaction.
     *   3. If the method completes normally → Spring COMMITS the transaction.
     *      All changes become permanent in the database.
     *   4. If the method throws an exception → Spring ROLLS BACK the
     *      transaction.  ALL changes are undone.  The database looks
     *      exactly like it did before the method started.
     *
     *   WHY THIS MATTERS FOR STOCK REDUCTION:
     *   Imagine an order with 2 items.  We reduce stock for item 1 (success),
     *   then item 2 fails because it's out of stock.  Without @Transactional,
     *   item 1's stock reduction is ALREADY saved — the data is inconsistent.
     *   With @Transactional, the rollback undoes item 1's stock change too.
     *
     *   REAL-WORLD ANALOGY: It's like a bank transfer.  Deducting $100 from
     *   account A and adding $100 to account B must BOTH succeed or BOTH fail.
     *   You never want only half to happen.
     *
     * Parameters:
     *   @param merchantId  — The ID of the user placing the order.
     *   @param items       — A list of OrderItems with product and quantity set.
     *                         (The frontend sends productId + quantity; the
     *                          controller maps productId to a Product entity.)
     */
    @Transactional
    public Order placeOrder(Long merchantId, List<OrderItem> items) {

        // Step 1: Find the merchant.  Throws if not found.
        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found with id: " + merchantId));

        // Step 2: Create the order shell.
        Order order = new Order();
        order.setMerchant(merchant);
        order.setStatus(Order.OrderStatus.PENDING);

        // Step 3: Process each item — validate stock and reduce it.
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found with id: " + item.getProduct().getId()));

            // Stock check — if not enough, throw exception → triggers rollback.
            if (product.getAvailabilityCount() < item.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for product '" + product.getDescription()
                        + "'. Available: " + product.getAvailabilityCount()
                        + ", Requested: " + item.getQuantity());
            }

            /*
             * REDUCE STOCK — This is the critical write.
             * Because we are inside @Transactional, this change will be
             * rolled back if anything fails later in the loop.
             */
            product.setAvailabilityCount(product.getAvailabilityCount() - item.getQuantity());
            productRepository.save(product);

            // Link the item to the order (sets the FK relationship).
            item.setProduct(product);
            item.setOrder(order);
            order.getItems().add(item);
        }

        /*
         * Step 4: Save the order.
         * Because cascade = CascadeType.ALL on Order.items, this single
         * save() call also INSERTs all the OrderItem rows.
         */
        return orderRepository.save(order);
    }
}
