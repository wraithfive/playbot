package com.discordbot.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthRedirectController {

    /**
     * Some browsers or links may hit backend /login directly. Since we use OAuth2 login only,
     * redirect that path to the Spring Security OAuth2 authorization endpoint for Discord.
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/discord";
    }
}
