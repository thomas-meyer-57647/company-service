package de.innologic.companyservice;

import org.springframework.boot.SpringApplication;

public class TestCompanyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CompanyServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
