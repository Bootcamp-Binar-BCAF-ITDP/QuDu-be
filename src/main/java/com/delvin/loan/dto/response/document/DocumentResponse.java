package com.delvin.loan.dto.response.document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentResponse {

    private Integer documentId;
    private String documentType;
    private String file_name;
}
