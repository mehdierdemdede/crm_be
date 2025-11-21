package com.leadsyncpro.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.leadsyncpro")
@EnableJpaRepositories(basePackages = "com.leadsyncpro.repository")
@EntityScan(basePackages = "com.leadsyncpro.model")
@EnableScheduling
public class CrmApplication {

        public static void main(String[] args) {
                SpringApplication.run(CrmApplication.class, args);
        }
}
