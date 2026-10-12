package com.coldchainsentinel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Single Page Application (SPA) routing forward controller.
 *
 * Forwards HTML5 client-side navigation routes (e.g. /facilities, /sensors, /alerts,
 * /risk-assessment) to index.html so React Router can render them seamlessly on direct
 * navigation or browser refresh.
 *
 * Explicitly excludes:
 * - /api/** (handled by REST API controllers)
 * - /error (handled by Spring Boot ErrorController)
 * - Any path containing a file extension (e.g. .js, .css, .ico, .png, .svg) so static assets are served directly
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/{path:[^\\.]*}",
            "/**/{path:[^\\.]*}"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
