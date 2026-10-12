
package com.coldchainsentinel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @GetMapping(value = {
            "/{path:[^\\.]*}",
            "/**/{path:[^\\.]*}"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
