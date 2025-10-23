package com.discordbot;

import com.discordbot.web.dto.qotd.QotdDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QotdDtosTest {

    @Test
    @DisplayName("Instantiate outer QotdDtos class to cover default constructor")
    void instantiateOuterClass() {
        QotdDtos dtos = new QotdDtos();
        assertNotNull(dtos);
    }
}
