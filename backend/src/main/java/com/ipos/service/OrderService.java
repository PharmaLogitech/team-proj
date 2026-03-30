/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Order-related business logic.                      ║
 * ║                                                                              ║
 * ║  WHY:  THIS IS THE MOST IMPORTANT SERVICE IN THE SYSTEM.                    ║
 * ║        Creating an order is NOT a simple save — it involves:                 ║
 * ║          1. Looking up the merchant (user) and their MerchantProfile.        ║
 * ║          2. Verifying account standing (NORMAL = allowed; else blocked).     ║
 * ║          3. For each requested item, looking up the product and snapshotting ║
 * ║             its price.                                                       ║
 * ║          4. Checking that enough stock exists and reducing it.               ║
 * ║          5. Computing gross total, applying discount (FIXED or FLEXIBLE     ║
 * ║             credit), and calculating totalDue.                              ║
 * ║          6. Enforcing the credit limit.                                     ║
 * ║          7. Saving the order with all its items.                             ║
 * ║        All of this must happen ATOMICALLY (all-or-nothing) inside a          ║
 * ║        database transaction.  If any step fails, everything is rolled back. ║
 * ║                                                                              ║
 * ║  DISCOUNT LOGIC (brief §i):                                                  ║
 * ║        FIXED   — fixedDiscountAmount = gross * (percent/100).               ║
 * ║                  Applied at order placement; reduces totalDue immediately.   ║
 * ║        FLEXIBLE — No percentage off at placement.  Instead, consume any     ║
 * ║                   accumulated flexibleDiscountCredit (from prior month-close ║
 * ║                   settlements).  The credit reduces totalDue.               ║
 * ║                                                                              ║
 * ║  CREDIT LIMIT:                                                               ║
 * ║        Sum of totalDue across non-cancelled orders + new order's totalDue   ║
 * ║        must not exceed the merchant's creditLimit.                           ║
 * ║                                                                              ║
 * ║  ORD-US1 (merchant isolation):                                               ║
 * ║        If the caller is a MERCHANT, the merchantId is forced to the         ║
 * ║        authenticated user's own ID.  Merchants cannot place orders on       ║
 * ║        behalf of others.                                                    ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add an updateOrderStatus() method.                                 ║
 * ║        - Add cancellation logic that RESTORES stock.                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MerchantProfileRepository profileRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        MerchantProfileRepository profileRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    /*
     * getOrderStatus — Retrieve the current status of a single order.
     *
     * Design-document test cases T7-T11 specify:
     *   - orderId must not be null or blank (T10, T11).
     *   - orderId must refer to an existing order (T9).
     *   - On success, the enum status is returned (T7, T8).
     *
     * The external interface accepts a String orderId so that callers (other
     * subsystems, front-end, test harnesses) do not need to know the internal
     * Long primary key format.  If the string cannot be parsed as a number,
     * the order simply doesn't exist → "Order not found".
     *
     * @param orderId  String representation of the order's numeric ID.
     * @return         The current OrderStatus enum value.
     * @throws RuntimeException  If orderId is null/blank, or no such order exists.
     */
    public Order.OrderStatus getOrderStatus(String orderId) {
        if (orderId == null) {
            throw new RuntimeException("Order ID cannot be null.");
        }
        if (orderId.isBlank()) {
            throw new RuntimeException("Invalid order ID.");
        }

        Long id;
        try {
            id = Long.parseLong(orderId.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return order.getStatus();
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
     *   WHY THIS MATTERS FOR STOCK REDUCTION + DISCOUNT:
     *   Imagine we reduce stock for item 1, compute the discount, then
     *   fail on the credit limit check.  Without @Transactional, item 1's
     *   stock is already gone.  With it, everything rolls back cleanly.
     *
     * Parameters:
     *   @param merchantId     The ID of the user placing the order.
     *   @param items          A list of OrderItems with product and quantity set.
     *   @param callerUserId   The authenticated user's ID (for ORD-US1 enforcement).
     *   @param callerRole     The authenticated user's role.
     */
    @Transactional
    public Order placeOrder(Long merchantId, List<OrderItem> items,
                            Long callerUserId, User.Role callerRole) {

        /*
         * ── ORD-US1: Merchant Isolation ──────────────────────────────────
         * Merchants can only place orders for themselves.  If the caller is
         * a MERCHANT, force the merchantId to their own user ID regardless
         * of what was submitted.  Admins and Managers can place orders on
         * behalf of any merchant (for testing / support).
         *
         * We assign to a new effectively-final variable because Java
         * requires local variables referenced from lambdas to be final.
         */
        final Long resolvedMerchantId =
                (callerRole == User.Role.MERCHANT) ? callerUserId : merchantId;

        /* Step 1: Find the merchant user. */
        User merchant = userRepository.findById(resolvedMerchantId)
                .orElseThrow(() -> new RuntimeException(
                        "Merchant not found with id: " + resolvedMerchantId));

        /*
         * Step 2: Load the MerchantProfile.
         * Every MERCHANT user must have a profile (created atomically by
         * MerchantAccountService).  If one is missing, the account was
         * created via the old staff path and is invalid.
         */
        MerchantProfile profile = profileRepository.findByUserId(resolvedMerchantId)
                .orElseThrow(() -> new RuntimeException(
                        "Merchant profile not found for user " + resolvedMerchantId
                        + ". Merchant accounts must be created via the merchant account endpoint."));

        /*
         * Step 3: Standing guard (brief §iii).
         * Only merchants with NORMAL standing can trade.  IN_DEFAULT and
         * SUSPENDED merchants are blocked until a Manager restores them.
         */
        if (profile.getStanding() != MerchantStanding.NORMAL) {
            throw new RuntimeException(
                    "Your account is currently " + profile.getStanding()
                    + ". Orders are blocked. Please contact a manager to resolve your account status.");
        }

        /* Step 4: Create the order shell. */
        Order order = new Order();
        order.setMerchant(merchant);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPlacedAt(Instant.now());

        /*
         * Step 5: Process each item — validate stock, snapshot price, reduce stock.
         * Also accumulate the gross total across all items.
         */
        BigDecimal grossTotal = BigDecimal.ZERO;

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found with id: " + item.getProduct().getId()));

            if (product.getAvailabilityCount() < item.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for product '" + product.getDescription()
                        + "'. Available: " + product.getAvailabilityCount()
                        + ", Requested: " + item.getQuantity());
            }

            /* Reduce stock. Rolled back by @Transactional if anything fails. */
            product.setAvailabilityCount(product.getAvailabilityCount() - item.getQuantity());
            productRepository.save(product);

            /* Snapshot the unit price so historical totals are stable. */
            item.setUnitPriceAtOrder(product.getPrice());
            item.setProduct(product);
            item.setOrder(order);
            order.getItems().add(item);

            /* Accumulate gross: unitPrice * quantity. */
            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            grossTotal = grossTotal.add(lineTotal);
        }

        order.setGrossTotal(grossTotal);

        /*
         * Step 6: Apply discount based on plan type (brief §i).
         *
         * FIXED:    Immediate percentage off the gross.
         * FLEXIBLE: Consume accumulated credit from prior month-close settlements.
         */
        BigDecimal fixedDiscount = BigDecimal.ZERO;
        BigDecimal flexibleCreditUsed = BigDecimal.ZERO;

        if (profile.getDiscountPlanType() == DiscountPlanType.FIXED) {
            /*
             * FIXED discount: gross * (fixedDiscountPercent / 100).
             * E.g. gross=200, percent=5 → discount=10.00, totalDue=190.00.
             */
            fixedDiscount = grossTotal
                    .multiply(profile.getFixedDiscountPercent())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        } else if (profile.getDiscountPlanType() == DiscountPlanType.FLEXIBLE) {
            /*
             * FLEXIBLE: no percentage off at placement.  Instead, consume
             * any accumulated credit balance (from prior month-close).
             * Credit used = min(available credit, grossTotal).
             */
            BigDecimal availableCredit = profile.getFlexibleDiscountCredit();
            if (availableCredit.compareTo(BigDecimal.ZERO) > 0) {
                flexibleCreditUsed = availableCredit.min(grossTotal);
                profile.setFlexibleDiscountCredit(
                        availableCredit.subtract(flexibleCreditUsed));
                profileRepository.save(profile);
            }
        }

        BigDecimal totalDue = grossTotal.subtract(fixedDiscount).subtract(flexibleCreditUsed);

        order.setFixedDiscountAmount(fixedDiscount);
        order.setFlexibleCreditApplied(flexibleCreditUsed);
        order.setTotalDue(totalDue);

        /*
         * Step 7: Credit limit check.
         * Sum of existing outstanding totalDue + this order's totalDue
         * must not exceed the merchant's credit limit.
         *
         * ASSUMPTION: "outstanding exposure" = sum of totalDue across all
         * non-cancelled orders.  A real system would subtract payments
         * received, but payment tracking is out of scope for now.
         */
        BigDecimal existingExposure = orderRepository.sumTotalDueByMerchantExcludingStatus(
                resolvedMerchantId, Order.OrderStatus.CANCELLED);
        BigDecimal newExposure = existingExposure.add(totalDue);

        if (newExposure.compareTo(profile.getCreditLimit()) > 0) {
            throw new RuntimeException(
                    "Order would exceed credit limit. "
                    + "Current exposure: £" + existingExposure.setScale(2, RoundingMode.HALF_UP)
                    + ", This order: £" + totalDue.setScale(2, RoundingMode.HALF_UP)
                    + ", Credit limit: £" + profile.getCreditLimit().setScale(2, RoundingMode.HALF_UP)
                    + ".");
        }

        /*
         * Step 8: Save the order.
         * cascade = CascadeType.ALL on Order.items means this single save()
         * also INSERTs all the OrderItem rows.
         */
        return orderRepository.save(order);
    }
}
