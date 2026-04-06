package com.masterchefcuts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Configuration
@EnableAsync
@EnableScheduling
public class MasterchefcutsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MasterchefcutsApplication.class, args);
	}
}
