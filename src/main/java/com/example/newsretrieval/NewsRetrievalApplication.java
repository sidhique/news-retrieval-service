package com.example.newsretrieval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NewsRetrievalApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsRetrievalApplication.class, args);
    }
}
