package com.delvin.loan.service;

import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.User;
import org.springframework.stereotype.Component;

@Component
public class BranchRouting {

    public Integer branchOf(LoanApplication application) {

        if (application == null) {
            return null;
        }

        if (application.getBranch() != null) {
            return application.getBranch().getBranchId();
        }

        if (application.getCustomer() != null && application.getCustomer().getBranch() != null) {
            return application.getCustomer().getBranch().getBranchId();
        }

        if (application.getReview() != null
                && application.getReview().getMarketing() != null
                && application.getReview().getMarketing().getBranch() != null) {
            return application.getReview().getMarketing().getBranch().getBranchId();
        }

        return null;
    }

    public Integer requireBranchOf(User user) {
        if (user.getBranch() == null) {
            throw BusinessException.badRequest("User " + user.getUserId() + " has no branch assigned.");
        }
        return user.getBranch().getBranchId();
    }

    public void requireSameBranch(User staff, LoanApplication application) {

        Integer staffBranchId = requireBranchOf(staff);
        Integer applicationBranchId = branchOf(application);

        if (applicationBranchId == null) {
            throw BusinessException.badRequest(
                    "Application " + application.getApplicationId()
                            + " has no branch. The customer registered before branch routing existed;"
                            + " assign them a branch first.");
        }

        if (!applicationBranchId.equals(staffBranchId)) {
            throw BusinessException.forbidden("This application belongs to a different branch");
        }
    }
}
