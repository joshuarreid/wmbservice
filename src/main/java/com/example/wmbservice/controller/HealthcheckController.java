package com.example.wmbservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
public class HealthcheckController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/health")
    public String health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return "OK";
            } else {
                return "Database connection invalid";
            }
        } catch (Exception e) {
            return "Database connection error: " + e.getMessage();
        }
    }
}