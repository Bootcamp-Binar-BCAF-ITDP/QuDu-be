package com.delvin.loan.dto.request.loanreq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchManagerDecisionRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    @NotNull(message = "approve is required")
    private Boolean approve;

    /** Optional note explaining the decision. */
    private String note;
}
