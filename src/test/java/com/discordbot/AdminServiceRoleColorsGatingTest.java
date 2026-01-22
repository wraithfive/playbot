package com.discordbot;

import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import com.discordbot.repository.QotdStreamRepository;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import static org.mockito.Mockito.*;

class AdminServiceRoleColorsGatingTest {

    private JDA jda;
    private OAuth2AuthorizedClientService authorizedClientService;
    private GuildsCache guildsCache;
    private WebSocketNotificationService ws;
    private QotdStreamRepository qotdStreamRepository;

    @SuppressWarnings("unused")
    private AdminService service;

    @BeforeEach
    void setup() {
        jda = mock(JDA.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        guildsCache = mock(GuildsCache.class);
        ws = mock(WebSocketNotificationService.class);
        qotdStreamRepository = mock(QotdStreamRepository.class);
        service = new AdminService(jda, authorizedClientService, guildsCache, ws, qotdStreamRepository);
    }
}


