package com.delvin.loan.support;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.model.Branch;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanReview;
import com.delvin.loan.model.Plafond;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestFixtures {

    private TestFixtures() {}

    public static final String CUSTOMER_ID = "CUST-001";
    public static final String APPLICATION_ID = "APP-001";
    public static final String USER_ID = "USR-001";

    public static final int BRANCH_ID = 1;

    public static Plafond plafond(int level, long min, long max) {
        Plafond plafond = new Plafond();
        plafond.setPlafondId(level);
        plafond.setLevel(level);
        plafond.setDescription("Level " + level);
        plafond.setMinimumAmount(BigDecimal.valueOf(min));
        plafond.setMaxAmount(BigDecimal.valueOf(max));
        plafond.setMinTenor(3);
        plafond.setMaxTenor(24);
        plafond.setIsActive(true);
        plafond.setInterestRate(BigDecimal.valueOf(0.12));
        plafond.setAdminFee(BigDecimal.valueOf(100_000));
        return plafond;
    }

    public static Customer customer() {
        return customer(BRANCH_ID);
    }

    public static Customer customer(int branchId) {
        Customer customer = new Customer();
        customer.setCustomerId(CUSTOMER_ID);
        customer.setBranch(branch(branchId));
        customer.setCustomerName("Test Customer");
        customer.setEmail("customer@example.com");
        customer.setNik("3201010101010001");
        customer.setPhoneNumber("081200000000");
        customer.setPlafond(plafond(1, 1_000_000L, 10_000_000L));
        customer.setApprovedLimit(BigDecimal.valueOf(10_000_000));
        return customer;
    }

    public static User user(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);

        User user = new User();
        user.setUserId(USER_ID);
        user.setUsername("staff");
        user.setEmail("staff@example.com");
        user.setFullName("Test Staff");
        user.setIsActive(true);
        user.setRole(role);
        return user;
    }

    public static AppUser principal(String userId, String roleName) {
        AppUser appUser = new AppUser();
        appUser.setUserId(userId);
        appUser.setUsername("staff");
        appUser.setRole(roleName);
        appUser.setAccountType(AccountType.USER);
        return appUser;
    }

    public static AppUser principal(String userId) {
        return principal(userId, RoleName.MARKETING);
    }

    public static Branch branch(int branchId) {
        Branch branch = new Branch();
        branch.setBranchId(branchId);
        branch.setBranchCode("BR-" + branchId);
        branch.setBranchName("Branch " + branchId);
        branch.setLocation("Jakarta");
        branch.setIsActive(true);
        return branch;
    }

    public static User user(String roleName, int branchId) {
        User user = user(roleName);
        user.setUserId(roleName + "-" + branchId);
        user.setBranch(branch(branchId));
        return user;
    }

    public static LoanApplication reviewedApplication(String status, int branchId) {
        LoanApplication application = application(status, branchId);

        LoanReview review = new LoanReview();
        review.setApplication(application);
        review.setMarketing(user(RoleName.MARKETING, branchId));
        review.setRecommendation("ACCEPT");
        review.setReviewNote("Looks fine");
        review.setUploadedAt(LocalDate.of(2026, 1, 16));

        application.setReview(review);
        return application;
    }

    public static LoanApplication application(String status) {
        return application(status, BRANCH_ID);
    }

    public static LoanApplication application(String status, int branchId) {
        LoanApplication application = new LoanApplication();
        application.setApplicationId(APPLICATION_ID);
        application.setCustomer(customer(branchId));
        application.setBranch(branch(branchId));
        application.setRequestedAmount(BigDecimal.valueOf(5_000_000));
        application.setTenor(12);
        application.setPurpose("Business");
        application.setIncome(BigDecimal.valueOf(8_000_000));
        application.setStatus(status);
        application.setSubmissionDate(LocalDate.of(2026, 1, 15));
        application.setBank("BCA");
        application.setBankAccountNumber("1234567890");
        application.setBankAccountName("Test Customer");
        return application;
    }

    public static LoanApplication checkingApplication() {
        return application(LoanStatus.CHECKING);
    }
}
