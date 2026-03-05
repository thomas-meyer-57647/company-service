package de.innologic.companyservice;

import org.springframework.boot.builder.SpringApplicationBuilder;

public class TestCompanyServiceApplication {

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CompanyServiceApplication.class);
        if (isTestcontainersRequested()) {
            builder = builder.sources(TestcontainersConfiguration.class);
        }
        builder.run(args);
    }

    private static boolean isTestcontainersRequested() {
        return "tc".equalsIgnoreCase(System.getProperty("test.db"));
    }

}
