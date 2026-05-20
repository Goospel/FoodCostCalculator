package com.goosepl.coastCalculator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.goosepl.coastCalculator.config")
public class CoastCalculatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoastCalculatorApplication.class, args);
	}

}
