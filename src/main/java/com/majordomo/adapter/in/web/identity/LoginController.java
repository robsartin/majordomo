package com.majordomo.adapter.in.web.identity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login page for form-based authentication.
 */
@Controller
public class LoginController {

    /**
     * Returns the login page template.
     *
     * @return the Thymeleaf template name for the login form
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
