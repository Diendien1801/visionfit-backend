package com.visionfit.analysis.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Finds the dishes on a meal photo. Claude in production ({@link ClaudeDishRecognizer}), a fake in
 * tests so they run offline and for free.
 */
public interface DishRecognizer {

    /**
     * @return every dish found, empty when there is no food on the photo.
     * @throws Exception when the model fails, refuses, or answers something unusable.
     */
    List<RecognizedDish> recognize(byte[] photo, String mediaType) throws Exception;

    /** What the model must answer: the JSON schema of the structured output is derived from it. */
    record Recognition(
            @JsonPropertyDescription("Every dish and drink on the photo; empty when there is no food or drink")
            List<RecognizedDish> dishes
    ) {
    }

    record RecognizedDish(
            @JsonPropertyDescription("Common Vietnamese name of the dish, short, e.g. \"Thịt kho trứng\", \"Trà sữa trân châu\"")
            String name,
            @JsonPropertyDescription("Estimated weight of the portion on the photo, in grams")
            int grams,
            @JsonPropertyDescription("Energy per 100 g of this dish as served, in kcal (not for the whole portion)")
            double kcalPer100g,
            @JsonPropertyDescription("Protein per 100 g, in grams")
            double proteinPer100g,
            @JsonPropertyDescription("Carbohydrates per 100 g, in grams")
            double carbsPer100g,
            @JsonPropertyDescription("Fat per 100 g, in grams")
            double fatPer100g,
            @JsonPropertyDescription("How sure you are of the dish name, from 0 to 100")
            int confidence,
            @JsonPropertyDescription("Horizontal center of the dish: 0 is the left edge of the photo, 1 the right edge")
            double centerX,
            @JsonPropertyDescription("Vertical center of the dish: 0 is the top edge of the photo, 1 the bottom edge")
            double centerY,
            @JsonPropertyDescription("Radius of the dish as a fraction of the photo width")
            double radius
    ) {
    }
}
