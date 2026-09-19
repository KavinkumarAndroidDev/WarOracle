package com.kkdev.waroracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WarOracleApplication {

	static {
		System.setProperty("java.net.preferIPv6Addresses", "true");
	}

	public static void main(String[] args) {
		SpringApplication.run(WarOracleApplication.class, args);
	}

}
