package com.visionfit.analysis.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** Asks Claude (vision + structured output) which dishes are on the photo. */
@Component
public class ClaudeDishRecognizer implements DishRecognizer {

    private static final String SYSTEM_PROMPT = """
            You are the vision model of VisionFit, a Vietnamese calorie-tracking app. The user sends \
            one photo of a meal and logs what you find, so a missed dish or a wrong portion becomes a \
            wrong calorie count in their diary.

            List every dish and drink on the photo that is meant to be eaten or drunk: each plate, \
            bowl, side dish and drink is its own entry. Leave out garnish and condiments too small to \
            matter for calories.

            - name: the common Vietnamese name, short ("Cơm trắng", "Canh chua cá", "Trà sữa trân \
            châu"). For non-Vietnamese food use the name Vietnamese people usually say.
            - grams: the portion actually on the photo. Use plates, bowls, cutlery, cups and hands \
            for scale.
            - nutrition: typical values per 100 g of the dish as served, not for the whole portion.
            - confidence: how sure you are of the name, 0 to 100.
            - centerX, centerY, radius: where the dish is, as fractions of the photo.

            If there is no food or drink on the photo, return an empty list.""";

    private final String apiKey;
    private final String model;
    private AnthropicClient client;

    public ClaudeDishRecognizer(@Value("${visionfit.ai.api-key:}") String apiKey, @Value("${visionfit.ai.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public List<RecognizedDish> recognize(byte[] photo, String mediaType) {
        StructuredMessageCreateParams<Recognition> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                // Medium is this model's default; set explicitly so a model change does not silently move it.
                .outputConfig(StructuredOutputConfig.<Recognition>builder()
                        .format(Recognition.class)
                        .effort(OutputConfig.Effort.MEDIUM)
                        .build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .data(Base64.getEncoder().encodeToString(photo))
                                        .mediaType(Base64ImageSource.MediaType.of(mediaType))
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text("Phân tích bữa ăn trong ảnh này.").build())))
                // If a safety classifier declines, the API retries on a fitting model instead of refusing.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        StructuredMessage<Recognition> response = client().messages().create(params);
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
            throw new IllegalStateException("Claude stopped with " + stop);
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude answered without a text block"))
                .text()
                .dishes();
    }

    /** Built on first use: without credentials the service still starts, and only the analyses fail. */
    private synchronized AnthropicClient client() {
        if (client == null) {
            AnthropicOkHttpClient.Builder builder = apiKey.isBlank() ? AnthropicOkHttpClient.builder().fromEnv() : AnthropicOkHttpClient.builder().apiKey(apiKey);
            client = builder.timeout(Duration.ofSeconds(120)).build();
        }
        return client;
    }
}
