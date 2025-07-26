// src/main/java/com/leadsyncpro/crm/CrmApplication.java
package com.leadsyncpro.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan; // Import eklendi

@SpringBootApplication
@ComponentScan(basePackages = {"com.leadsyncpro.crm", "com.leadsyncpro.config", "com.leadsyncpro.security", "com.leadsyncpro.service"}) // BURASI GÜNCELLENDİ
public class CrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrmApplication.class, args);
	}

}