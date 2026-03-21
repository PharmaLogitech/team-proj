/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Placeholder component for the Account Management package             ║
 * ║        (IPOS-SA-ACC).                                                       ║
 * ║                                                                              ║
 * ║  WHY:  This stub proves that RBAC navigation works correctly:               ║
 * ║        - ADMIN can see and navigate to this page.                          ║
 * ║        - MANAGER and MERCHANT cannot see this nav item or access it.       ║
 * ║                                                                              ║
 * ║        The actual account management features will be built in future      ║
 * ║        iterations:                                                          ║
 * ║          - ACC-US1: Merchant Account Creation (contact details, credit     ║
 * ║                     limit, discount plan, Active/Inactive status)          ║
 * ║          - ACC-US2: Fixed Discount Plan Assignment                         ║
 * ║          - ACC-US3: Flexible Discount Plan Configuration                   ║
 * ║          - ACC-US5: Managing Defaulted Accounts                            ║
 * ║          - ACC-US6: Managing Accounts (edit credit/discount)               ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        Visible to: ADMIN only                                              ║
 * ║        Hidden from: MANAGER, MERCHANT                                      ║
 * ║        Enforced by: rbac.js + App.jsx nav guards                           ║
 * ║        Backend: /api/users/** → hasRole("ADMIN")                           ║
 * ║                                                                              ║
 * ║  HOW TO REPLACE:                                                             ║
 * ║        When implementing ACC-US1–US6, replace this component with a real   ║
 * ║        AccountManagement.jsx that includes user CRUD, merchant account     ║
 * ║        creation forms, discount plan editors, and status management.       ║
 * ║        The RBAC wiring in App.jsx and rbac.js will continue to work.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

function AccountsPlaceholder() {
  return (
    <div className="placeholder-page">
      <h2>Account Management (IPOS-SA-ACC)</h2>
      <p className="status-message">
        Account management features are coming in a future iteration.
      </p>
      <div style={{ marginTop: "1.5rem", color: "#6b7280", fontSize: "0.9rem" }}>
        <p><strong>Planned features:</strong></p>
        <ul style={{ marginTop: "0.5rem", paddingLeft: "1.5rem" }}>
          <li>Merchant Account Creation (ACC-US1)</li>
          <li>Fixed Discount Plan Assignment (ACC-US2)</li>
          <li>Flexible Discount Plan Configuration (ACC-US3)</li>
          <li>Managing Defaulted Accounts (ACC-US5)</li>
          <li>Managing Accounts — credit/discount editing (ACC-US6)</li>
        </ul>
      </div>
    </div>
  );
}

export default AccountsPlaceholder;
