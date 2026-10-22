package com.morak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MorakApplication {

	public static void main(String[] args) {
		SpringApplication.run(MorakApplication.class, args);
	}
}
