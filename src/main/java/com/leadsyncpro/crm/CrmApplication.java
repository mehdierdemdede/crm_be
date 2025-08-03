// src/main/java/com/leadsyncpro/crm/CrmApplication.java
package com.leadsyncpro.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan; // Yeni import

@SpringBootApplication
@ComponentScan(basePackages = "com.leadsyncpro") // Tüm ana paketi tara
@EnableJpaRepositories(basePackages = "com.leadsyncpro.repository") // Repository'leri tara
@EntityScan(basePackages = "com.leadsyncpro.model") // BURASI EKLENDİ: Entity'leri tara
public class CrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrmApplication.class, args);
	}

}