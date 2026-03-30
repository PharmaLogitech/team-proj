/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 Test Suite for IPOS-SA — runs all tests as a batch.         ║
 * ║                                                                              ║
 * ║  WHY:  The brief requires a test suite (set of tests run as a batch).       ║
 * ║        Running the suite exercises all test classes in one go, producing    ║
 * ║        a combined pass/fail report for the Implementation Report.           ║
 * ║                                                                              ║
 * ║  HOW TO RUN:                                                                 ║
 * ║        mvn test                         (all tests including this suite)    ║
 * ║        mvn test -pl backend             (from project root)                 ║
 * ║        Right-click IposTestSuite.java → Run in IntelliJ / Eclipse          ║
 * ║                                                                              ║
 * ║  TEST CLASSES INCLUDED:                                                      ║
 * ║                                                                              ║
 * ║  ── Unit Tests (non-trivial classes with 5+ methods) ─────────────────────  ║
 * ║    MerchantAccountServiceTest  — 20 tests covering ACC-US1/US5, discount    ║
 * ║                                   plans, OrderService standing + credit     ║
 * ║    ProductServiceTest          — 16 tests covering getCatalogue (T12-T13),  ║
 * ║                                   increaseStock (T17-T22),                  ║
 * ║                                   decreaseStock (T23-T27)                   ║
 * ║                                                                              ║
 * ║  ── Sub-System Tests (provided interface of IPOS-SA-ORD) ─────────────────  ║
 * ║    OrderServiceTest            — 5 tests covering getOrderStatus (T7-T11)   ║
 * ║    PaymentServiceTest          — 7 tests covering recordPayment (T1-T6)     ║
 * ║                                                                              ║
 * ║  ── System Tests (required interface to external email subsystem) ─────────  ║
 * ║    EmailServiceTest            — 7 tests covering sendEmail (T28-T34)       ║
 * ║                                                                              ║
 * ║  TOTAL: 55 tests across 5 test classes                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos;

import com.ipos.service.EmailServiceTest;
import com.ipos.service.MerchantAccountServiceTest;
import com.ipos.service.OrderServiceTest;
import com.ipos.service.PaymentServiceTest;
import com.ipos.service.ProductServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/*
 * @Suite      — Marks this class as a JUnit Platform test suite.
 * @SelectClasses — Lists the test classes to include in the suite.
 *                  Each class will run with its own lifecycle (@BeforeEach, etc.)
 *                  exactly as if run individually.
 */
@Suite
@SelectClasses({
        /* 1. Unit tests — non-trivial class #1 */
        MerchantAccountServiceTest.class,

        /* 2. Unit tests — non-trivial class #2 */
        ProductServiceTest.class,

        /* 3. Sub-system tests — IPOS-SA-ORD provided interface (getOrderStatus) */
        OrderServiceTest.class,

        /* 4. Sub-system tests — IPOS-SA-ORD provided interface (recordPayment) */
        PaymentServiceTest.class,

        /* 5. System tests — required interface (email subsystem) */
        EmailServiceTest.class
})
public class IposTestSuite {
    /*
     * This class intentionally left empty.
     * JUnit 5 @Suite discovers and runs the listed test classes automatically.
     */
}
