/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit Platform suite — runs core IPOS-SA test classes in one batch.   ║
 * ║                                                                              ║
 * ║  WHY:  Brief asks for a test suite; this aggregates ACC, CAT, and ORD tests  ║
 * ║        already on main plus the dedicated ProductServiceTest in this PR.     ║
 * ║                                                                              ║
 * ║  HOW TO RUN:                                                                 ║
 * ║        cd backend && mvn test                                                  ║
 * ║        mvn test -Dtest=IposTestSuite                                           ║
 * ║        Run this class from the IDE to execute the suite only.                 ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos;

import com.ipos.cat.CatalogueCatTest;
import com.ipos.ord.ORDInvoicePaymentTest;
import com.ipos.ord.ORDOrderTest;
import com.ipos.service.MerchantAccountServiceTest;
import com.ipos.service.ProductServiceTest;
import com.ipos.service.ReportingServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        MerchantAccountServiceTest.class,
        ProductServiceTest.class,
        ReportingServiceTest.class,
        CatalogueCatTest.class,
        ORDOrderTest.class,
        ORDInvoicePaymentTest.class
})
public class IposTestSuite {
}
