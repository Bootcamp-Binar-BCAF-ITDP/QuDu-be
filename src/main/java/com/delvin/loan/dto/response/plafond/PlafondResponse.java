package com.delvin.loan.dto.response.plafond;

import com.delvin.loan.model.Plafond;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlafondResponse {

    private Integer plafondId;
    private Integer level;
    private String description;
    private BigDecimal minimumAmount;
    private BigDecimal maxAmount;
    private Integer minTenor;
    private Integer maxTenor;
    private BigDecimal interestRate;
    private BigDecimal adminFee;

    public static PlafondResponse from(Plafond plafond) {

        if (plafond == null) {
            return null;
        }

        return PlafondResponse.builder()
                .plafondId(plafond.getPlafondId())
                .level(plafond.getLevel())
                .description(plafond.getDescription())
                .minimumAmount(plafond.getMinimumAmount())
                .maxAmount(plafond.getMaxAmount())
                .minTenor(plafond.getMinTenor())
                .maxTenor(plafond.getMaxTenor())
                .interestRate(plafond.getInterestRate())
                .adminFee(plafond.getAdminFee())
                .build();
    }
}
