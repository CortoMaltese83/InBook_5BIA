package com.inbook.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KeepAliveController {

    @GetMapping(value = "/keeplive", produces = MediaType.TEXT_PLAIN_VALUE)
    public String keepAlive() {
        return "OK";
    }
}
