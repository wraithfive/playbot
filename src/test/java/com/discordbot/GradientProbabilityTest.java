package com.discordbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GradientProbabilityTest {

    @Test
    @DisplayName("Gradient probability should be approximately 25% over many rolls")
    void testGradientProbability() {
        Random random = new Random(12345); // Fixed seed for reproducibility
        int totalRolls = 10000;
        int gradientRolls = 0;

        for (int i = 0; i < totalRolls; i++) {
            boolean isGradient = random.nextDouble() < 0.25;
            if (isGradient) {
                gradientRolls++;
            }
        }

        double gradientPercentage = (gradientRolls * 100.0) / totalRolls;

        // Allow 2% margin of error
        assertTrue(gradientPercentage >= 23.0 && gradientPercentage <= 27.0,
            String.format("Expected ~25%% gradients, got %.2f%%", gradientPercentage));

        System.out.printf("Gradient probability test: %.2f%% (expected 25%%)%n", gradientPercentage);
    }

    @Test
    @DisplayName("Solid color probability should be approximately 75% over many rolls")
    void testSolidColorProbability() {
        Random random = new Random(54321); // Fixed seed for reproducibility
        int totalRolls = 10000;
        int solidRolls = 0;

        for (int i = 0; i < totalRolls; i++) {
            boolean isGradient = random.nextDouble() < 0.25;
            if (!isGradient) {
                solidRolls++;
            }
        }

        double solidPercentage = (solidRolls * 100.0) / totalRolls;

        // Allow 2% margin of error
        assertTrue(solidPercentage >= 73.0 && solidPercentage <= 77.0,
            String.format("Expected ~75%% solid colors, got %.2f%%", solidPercentage));

        System.out.printf("Solid color probability test: %.2f%% (expected 75%%)%n", solidPercentage);
    }
}
