package com.morak.demo;

import com.morak.MorakApplication;
import org.springframework.boot.SpringApplication;

public class TestDemoApplication {

	public static void main(String[] args) {
		SpringApplication.from(MorakApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
