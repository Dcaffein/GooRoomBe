package com.example.GooRoomBe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableNeo4jRepositories
public class GooRoomBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(GooRoomBeApplication.class, args);
	}

}
