package com.discordbot;

import com.discordbot.web.controller.AuthRedirectController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRedirectControllerTest {

    @Test
    void login_redirectsToDiscordOAuth() {
        AuthRedirectController c = new AuthRedirectController();
        String view = c.login();
        assertEquals("redirect:/oauth2/authorization/discord", view);
    }
}
