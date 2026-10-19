package com.visionfit.analysis.service;

import com.visionfit.analysis.ai.DishRecognizer.RecognizedDish;
import com.visionfit.analysis.domain.Detection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Whatever the model answers, the stored dishes fit meal-service's constraints. */
class ToDetectionsTest {

    @Test
    void dropsUnusableDishesAndPullsNumbersIntoRange() {
        List<Detection> detections = AnalysisService.toDetections(List.of(
                new RecognizedDish("  ", 100, 100, 1, 1, 1, 50, 0.5, 0.5, 0.1),
                new RecognizedDish("Phở bò", 0, 100, 1, 1, 1, 50, 0.5, 0.5, 0.1),
                new RecognizedDish(" Lẩu ", 9000, 1200, -3, 150, Double.NaN, 140, 1.4, -0.2, 0.3),
                new RecognizedDish("Trà đá", 300, 0, 0, 0, 0, 90, Double.NaN, 0.5, 0.1)
        ));

        assertThat(detections).hasSize(2);
        Detection hotpot = detections.get(0);
        assertThat(hotpot.getName()).isEqualTo("Lẩu");
        assertThat(hotpot.getGrams()).isEqualTo(5000);
        assertThat(hotpot.getKcalPer100g()).isEqualTo(900);
        assertThat(hotpot.getProteinPer100g()).isZero();
        assertThat(hotpot.getCarbsPer100g()).isEqualTo(100);
        assertThat(hotpot.getFatPer100g()).isZero();
        assertThat(hotpot.getConfidence()).isEqualTo(100);
        assertThat(hotpot.getCenterX()).isEqualTo(1);
        assertThat(hotpot.getCenterY()).isZero();
        assertThat(detections.get(1).getCenterX()).as("an unreadable region is dropped, not guessed").isNull();
    }

    @Test
    void noAnswerIsNoDish() {
        assertThat(AnalysisService.toDetections(null)).isEmpty();
    }
}
