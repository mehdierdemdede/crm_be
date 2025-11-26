package com.leadsyncpro.billing.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(name = "PublicSignupPaymentInitializeRequest")
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicSignupPaymentInitializeRequest(
        @NotNull @Schema(description = "Identifier of the selected plan", example = "5d3c7f8b-8a8c-4f2e-9f8f-9b0c1d2e3f01")
                UUID planId,
        @NotNull @Schema(description = "Billing period for the subscription", example = "MONTH") BillingPeriod billingPeriod,
        @NotNull @Min(1) @Max(200) @Schema(description = "Number of seats requested", example = "10") Integer seatCount,
        @Valid @NotNull Account account,
        @Valid @NotNull Organization organization,
        @Valid @NotNull Card card,
        @Valid @Schema(hidden = true) TokenizePaymentMethodRequest legacyCard) {

    @Schema(name = "PublicSignupPaymentAccount")
    public record Account(
            @NotBlank @Size(max = 100) @Schema(description = "First name of the organization admin", example = "Ada")
                    String firstName,
            @NotBlank @Size(max = 100) @Schema(description = "Last name of the organization admin", example = "Lovelace")
                    String lastName,
            @NotBlank @Email @Size(max = 255)
                    @Schema(description = "Work email address for the admin", example = "ada@acme.co")
                    String email,
            @NotBlank
                    @Size(min = 8, max = 255)
                    @Schema(
                            description = "Password to be set for the admin account",
                            example = "P@ssw0rd!")
                    String password,
            @Size(max = 40) @Schema(description = "Optional phone number for the admin", example = "+90 555 000 0000")
                    String phone) {}

    @Schema(name = "PublicSignupPaymentOrganization")
    public record Organization(
            @NotBlank @Size(max = 255) @Schema(description = "Name of the organization to be created", example = "Acme Inc.")
                    String organizationName,
            @NotBlank
                    @Size(min = 2, max = 3)
                    @Schema(
                            description = "ISO alpha-2 or alpha-3 country code for the organization",
                            example = "TR")
                    String country,
            @NotBlank
                    @Size(max = 50)
                    @Schema(description = "Tax identification number for the organization", example = "1234567890")
                    String taxNumber,
            @Size(max = 50) @Schema(description = "Optional company size label", example = "51-100") String companySize) {}

    @Schema(name = "PublicSignupPaymentCard")
    public record Card(
            @NotBlank @Size(max = 255) @Schema(description = "Full name printed on the card", example = "Ada Lovelace")
                    String cardHolderName,
            @NotBlank
                    @Pattern(regexp = "^[0-9]{12,19}$", message = "Card number must contain 12 to 19 digits")
                    @Schema(description = "Primary account number without spaces", example = "5528790000000008")
                    String cardNumber,
            @NotNull
                    @Min(1)
                    @Max(12)
                    @Schema(description = "Expiration month as a number", example = "1")
                    Integer expireMonth,
            @NotNull
                    @Min(2000)
                    @Max(2100)
                    @Schema(description = "Full expiration year as a number", example = "2026")
                    Integer expireYear,
            @NotBlank
                    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVC must be three or four digits")
                    @Schema(description = "Card verification code", example = "123")
                    String cvc,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    @Pattern(regexp = "^[0-9]{2,4}$", message = "Legacy expiration month")
                    String legacyExpireMonth,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    @Pattern(regexp = "^[0-9]{2,4}$", message = "Legacy expiration year")
                    String legacyExpireYear) {

        public Card(
                String cardHolderName,
                String cardNumber,
                Integer expireMonth,
                Integer expireYear,
                String cvc) {
            this(cardHolderName, cardNumber, expireMonth, expireYear, cvc, null, null);
        }

        @JsonIgnore
        public String formattedExpireMonth() {
            if (expireMonth != null) {
                return String.format("%02d", expireMonth);
            }
            return legacyExpireMonth;
        }

        @JsonIgnore
        public String formattedExpireYear() {
            if (expireYear != null) {
                return expireYear.toString();
            }
            return legacyExpireYear;
        }
    }

    @JsonIgnore
    public Card resolvedCard() {
        if (card != null) {
            return card;
        }
        if (legacyCard != null) {
            Integer legacyMonth = null;
            if (legacyCard.expireMonth() != null && legacyCard.expireMonth().matches("^[0-9]{1,2}$")) {
                legacyMonth = Integer.parseInt(legacyCard.expireMonth());
            }
            Integer legacyYear = null;
            if (legacyCard.expireYear() != null && legacyCard.expireYear().matches("^[0-9]{2,4}$")) {
                legacyYear = Integer.parseInt(legacyCard.expireYear());
            }
            return new Card(
                    legacyCard.cardHolderName(),
                    legacyCard.cardNumber(),
                    legacyMonth,
                    legacyYear,
                    legacyCard.cvc(),
                    legacyCard.expireMonth(),
                    legacyCard.expireYear());
        }
        return null;
    }
}
