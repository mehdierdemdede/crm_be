package com.leadsyncpro.billing.integration.iyzico;

import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.PaymentMethod;
import com.leadsyncpro.model.billing.Price;
import java.time.Instant;

public interface IyzicoClient {

    String createOrAttachPaymentMethod(Customer customer, String cardToken);

    String tokenizePaymentMethod(
            String cardHolderName, String cardNumber, String expireMonth, String expireYear, String cvc);

    IyzicoSubscriptionResponse createSubscription(
            Customer customer, Price price, int seatCount, PaymentMethod paymentMethod);

    void changeSubscriptionPlan(
            String externalSubscriptionId, Price newPrice, ProrationBehavior prorationBehavior);

    void updateSeatCount(String externalSubscriptionId, int seatCount, ProrationBehavior prorationBehavior);

    void cancelSubscription(String externalSubscriptionId, boolean cancelAtPeriodEnd);

    IyzicoInvoiceResponse createInvoice(
            String externalSubscriptionId,
            Instant periodStart,
            Instant periodEnd,
            long amountCents,
            String currency);

    boolean verifyWebhook(String signatureHeader, String payload);
}
