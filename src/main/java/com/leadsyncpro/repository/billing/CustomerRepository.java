package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}
