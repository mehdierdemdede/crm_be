package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.PaymentMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    List<PaymentMethod> findByCustomer(Customer customer);

    Optional<PaymentMethod> findByCustomerAndDefaultMethodIsTrue(Customer customer);
}
