package com.example.demo.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class CarrierPlanDTO {

    @NotBlank(message = "Carrier ID is required")
    private String carrierId;

    @NotBlank(message = "Plan Type is required")
    private String planType;

    private String accountId;

    @Pattern(regexp = "Y|N", message = "Prospect Client must be either 'Y' or 'N'")
    private String prospectClient;

    // Getters and Setters
    public String getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(String carrierId) {
        this.carrierId = carrierId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getProspectClient() {
        return prospectClient;
    }

    public void setProspectClient(String prospectClient) {
        this.prospectClient = prospectClient;
    }
}
