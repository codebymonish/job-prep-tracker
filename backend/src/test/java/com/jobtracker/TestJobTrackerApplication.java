package com.jobtracker;

import org.springframework.boot.SpringApplication;

public class TestJobTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
