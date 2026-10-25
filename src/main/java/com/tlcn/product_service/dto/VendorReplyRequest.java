package com.tlcn.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VendorReplyRequest {
    @NotBlank(message = "Reply content cannot be blank")
    @Size(max = 1000, message = "Reply cannot exceed 1000 characters")
    private String reply;
}