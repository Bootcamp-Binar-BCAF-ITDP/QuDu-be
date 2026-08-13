package com.delvin.loan.dto.response.menu;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponse {

    private Integer menuId;
    private String menuName;

}