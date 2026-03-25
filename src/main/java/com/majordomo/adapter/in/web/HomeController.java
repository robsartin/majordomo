package com.majordomo.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Majordomo home page at the root URL.
 */
@Controller
public class HomeController {

    /**
     * Returns the home page template.
     *
     * @return the Thymeleaf template name for the home page
     */
    @GetMapping("/")
    public String home() {
        return "home";
    }
}
