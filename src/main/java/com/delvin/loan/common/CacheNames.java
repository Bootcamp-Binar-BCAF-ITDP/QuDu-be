package com.delvin.loan.common;

public final class CacheNames {

    private CacheNames() {
    }

    // Plafond
    public static final String PLAFOND_CATALOG = "plafond:catalog";
    public static final String PLAFOND_BY_ID = "plafond:byId";
    public static final String PLAFOND_BY_LEVEL = "plafond:byLevel";
    public static final String PLAFOND_PAGE = "plafond:page";

    // Branch
    public static final String BRANCH_PAGE = "branch:page";
    public static final String BRANCH_OPTIONS = "branch:branchOptions";
    public static final String BRANCH_BY_ID = "branch:byId";

    // Role
    public static final String ROLE_PAGE = "role:page";

    // Role
    public static final String ROLE_OPTIONS = "role:roleOptions";
    public static final String ROLE_BY_ID = "role:byId";

    // Menu
    public static final String MENU_OPTIONS = "menu:menuOptions";
    public static final String MENU_BY_ID = "menu:byId";

    // User
    public static final String USER_PAGE = "user:page";
    public static final String USER_BY_ID = "user:byId";

    public static final String APPLICATION_PAGE = "application:page";

    public static final String APPLICATION_BY_CUSTOMER = "application:byCustomer";

    public static final String CUSTOMER_PLAFOND = "customer:plafond";
}
