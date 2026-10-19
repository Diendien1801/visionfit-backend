package com.visionfit.meal;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HS256 tokens, signed the way identity-service signs them, against the whole HTTP stack and the real migration. */
@SpringBootTest
@AutoConfigureMockMvc
class MealApiTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-characters-long";

    /** Totals of {@link #meal}: 250 g of cơm tấm and 120 g of sườn nướng. */
    private static final int MEAL_KCAL = 325 + 348;
    private static final double MEAL_PROTEIN_G = 6.75 + 28.8;

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsMissingAndForgedTokens() throws Exception {
        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/meals").contentType(MediaType.APPLICATION_JSON).content(meal(null, "LUNCH", "2026-10-14T12:00:00")))
                .andExpect(status().isUnauthorized());

        String forged = token("another-secret-that-is-at-least-32-chars", UUID.randomUUID());
        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savesAMealAndReturnsItAsSent() throws Exception {
        save(bearerFor(UUID.randomUUID()), meal("job-1", "LUNCH", "2026-10-14T12:05:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(content().json("""
                        {"jobId":"job-1","type":"LUNCH","loggedAt":"2026-10-14T12:05:00","photoUrl":"https://cdn.example/1.jpg",
                         "isEdited":true,"items":[
                           {"name":"Cơm tấm","grams":250,"per100g":{"kcal":130,"proteinG":2.7,"carbsG":28,"fatG":0.3},"confidence":94},
                           {"name":"Sườn nướng","grams":120,"per100g":{"kcal":290,"proteinG":24,"carbsG":4,"fatG":20},"confidence":null}]}
                        """))
                // 250 g × 130 = 325 kcal, 120 g × 290 = 348 kcal.
                .andExpect(jsonPath("$.kcal").value(MEAL_KCAL))
                .andExpect(jsonPath("$.proteinG").value(closeTo(MEAL_PROTEIN_G, 1e-9)))
                .andExpect(jsonPath("$.carbsG").value(closeTo(74.8, 1e-9)))
                .andExpect(jsonPath("$.fatG").value(closeTo(24.75, 1e-9)));
    }

    @Test
    void returnsEveryDayOfTheRangeWithItsTotalsAndMealsNewestFirst() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        save(bearer, meal(null, "DINNER", "2026-10-12T23:59:59")).andExpect(status().isCreated());
        save(bearer, meal(null, "BREAKFAST", "2026-10-13T00:00:00")).andExpect(status().isCreated());
        save(bearer, meal(null, "LUNCH", "2026-10-13T12:00:00")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/meals?from=2026-10-12&to=2026-10-14").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].date").value(contains("2026-10-12", "2026-10-13", "2026-10-14")))
                .andExpect(jsonPath("$[1].mealCount").value(2))
                .andExpect(jsonPath("$[1].kcal").value(2 * MEAL_KCAL))
                .andExpect(jsonPath("$[1].proteinG").value(closeTo(2 * MEAL_PROTEIN_G, 1e-9)))
                .andExpect(jsonPath("$[1].meals[0].loggedAt").value("2026-10-13T12:00:00"))
                .andExpect(jsonPath("$[1].meals[1].loggedAt").value("2026-10-13T00:00:00"))
                .andExpect(jsonPath("$[1].meals[0].kcal").value(MEAL_KCAL))
                // Items keep the order they were sent in.
                .andExpect(jsonPath("$[1].meals[0].items[0].name").value("Cơm tấm"))
                .andExpect(jsonPath("$[1].meals[0].items[1].name").value("Sườn nướng"))
                // A day without meals is there too, with zeros.
                .andExpect(jsonPath("$[2].mealCount").value(0))
                .andExpect(jsonPath("$[2].kcal").value(0))
                .andExpect(jsonPath("$[2].meals", hasSize(0)));
    }

    @Test
    void streakCountsTheDaysInARowBeforeToday() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        for (String day : new String[]{"2026-10-14", "2026-10-13", "2026-10-12", "2026-10-10"}) {
            save(bearer, meal(null, "LUNCH", day + "T12:00:00")).andExpect(status().isCreated());
        }

        streak(bearer, "2026-10-15").andExpect(status().isOk()).andExpect(content().json("{\"days\":3}")); // 10-11 breaks it
        streak(bearer, "2026-10-14").andExpect(content().json("{\"days\":2}"));  // today's meal neither breaks nor extends it
        streak(bearer, "2026-10-17").andExpect(content().json("{\"days\":0}"));  // nothing yesterday
        streak(bearerFor(UUID.randomUUID()), "2026-10-15").andExpect(content().json("{\"days\":0}"));
        mvc.perform(get("/api/v1/meals/streak").header("Authorization", bearer)).andExpect(status().isBadRequest());
    }

    @Test
    void resendingTheSameIdReturnsTheFirstMealEvenWithoutAJob() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        UUID id = UUID.randomUUID();

        save(bearer, meal(id, null, "SNACK", "2026-10-14T15:00:00")).andExpect(status().isCreated());
        // A retry after a timeout: same id, so the server recognizes it although job_id is NULL.
        save(bearer, meal(id, null, "SNACK", "2026-10-14T15:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].mealCount").value(1));
    }

    @Test
    void anotherIdForTheSameJobStillReturnsTheFirstMeal() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        String id = idOf(save(bearer, meal("job-1", "LUNCH", "2026-10-14T12:00:00")).andExpect(status().isCreated()));

        // The app restarted and made a new id for the same photo: one photo is still one meal.
        save(bearer, meal("job-1", "DINNER", "2026-10-14T19:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.type").value("LUNCH"));

        // Different meals without a job are different meals.
        save(bearer, meal(null, "SNACK", "2026-10-14T15:00:00")).andExpect(status().isCreated());
        save(bearer, meal(null, "SNACK", "2026-10-14T15:00:00")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].mealCount").value(3));
    }

    @Test
    void anIdTakenByAnotherUserIsRefused() throws Exception {
        UUID id = UUID.randomUUID();
        String owner = bearerFor(UUID.randomUUID());
        save(owner, meal(id, null, "LUNCH", "2026-10-14T12:00:00")).andExpect(status().isCreated());

        save(bearerFor(UUID.randomUUID()), meal(id, null, "DINNER", "2026-10-14T19:00:00"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Cơm tấm"))));

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", owner))
                .andExpect(jsonPath("$[0].meals[0].type").value("LUNCH"));
    }

    @Test
    void concurrentRetriesOfOneMealKeepOneMeal() throws Exception {
        String body = meal(UUID.randomUUID(), null, "LUNCH", "2026-10-14T12:00:00");
        assertOneMealSurvives(bearerFor(UUID.randomUUID()), () -> body); // races on the primary key
    }

    @Test
    void concurrentSavesOfOneJobKeepOneMeal() throws Exception {
        // Every save makes its own id: races on UNIQUE (user_id, job_id) instead.
        assertOneMealSurvives(bearerFor(UUID.randomUUID()), () -> meal("job-race", "LUNCH", "2026-10-14T12:00:00"));
    }

    private void assertOneMealSurvives(String bearer, Supplier<String> bodies) throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        List<Callable<Integer>> saves = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String body = bodies.get();
            saves.add(() -> {
                start.await();
                ResultActions result = save(bearer, body);
                ids.add(idOf(result));
                return result.andReturn().getResponse().getStatus();
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> statuses = saves.stream().map(pool::submit).toList();
            start.countDown();
            int created = 0;
            for (Future<Integer> status : statuses) {
                int code = status.get();
                if (code == 201) created++;
                else assertEquals(200, code);
            }
            assertEquals(1, created);
            assertEquals(1, ids.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void usersOnlySeeTheirOwnMeals() throws Exception {
        save(bearerFor(UUID.randomUUID()), meal("job-1", "LUNCH", "2026-10-14T12:00:00")).andExpect(status().isCreated());

        String other = bearerFor(UUID.randomUUID());
        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", other))
                .andExpect(jsonPath("$[0].mealCount").value(0));
        // Same job id, different user: a new meal, not someone else's.
        save(other, meal("job-1", "LUNCH", "2026-10-14T12:00:00")).andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidMeals() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        String valid = meal("job-1", "LUNCH", "2026-10-14T12:05:00");
        for (String invalid : new String[]{
                valid.replaceAll("(?s)\"items\":\\[.*]", "\"items\":[]"),
                valid.replace("\"Cơm tấm\"", "\"   \""),
                valid.replace("\"grams\":250", "\"grams\":0"),
                valid.replace("\"kcal\":130", "\"kcal\":901"),
                valid.replace("\"proteinG\":2.7", "\"proteinG\":-1"),
                valid.replace("\"confidence\":94", "\"confidence\":101"),
                valid.replace("\"LUNCH\"", "\"BRUNCH\""),
                valid.replace("\"loggedAt\":\"2026-10-14T12:05:00\",", ""),
                valid.replace("\"job-1\"", "\"\""),
                valid.replaceFirst("\"id\":\"[^\"]+\",", ""),          // no id
                valid.replaceFirst("\"id\":\"[^\"]+\"", "\"id\":\"42\""), // not a UUID
        }) {
            save(bearer, invalid).andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsBadRanges() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        for (String query : new String[]{
                "from=2026-10-14&to=2026-10-13",
                "from=2026-07-14&to=2026-10-14", // 93 days
                "from=2026-10-14",
                "from=14-10-2026&to=2026-10-14",
        }) {
            mvc.perform(get("/api/v1/meals?" + query).header("Authorization", bearer)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/meals?from=2026-07-15&to=2026-10-14").header("Authorization", bearer)) // 92 days
                .andExpect(status().isOk());
    }

    @Test
    void updatesTheTypeAndDishesInPlaceAndMarksTheMealEdited() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        String unedited = meal("job-1", "LUNCH", "2026-10-14T12:05:00").replace("\"isEdited\":true", "\"isEdited\":false");
        String id = idOf(save(bearer, unedited).andExpect(status().isCreated()));

        // Fewer dishes than before: the extra row is removed.
        update(bearer, id, changes("DINNER", dish("Phở bò", 400, 94)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":"%s","jobId":"job-1","type":"DINNER","loggedAt":"2026-10-14T12:05:00","photoUrl":"https://cdn.example/1.jpg",
                         "isEdited":true,"items":[
                           {"name":"Phở bò","grams":400,"per100g":{"kcal":110,"proteinG":7,"carbsG":14,"fatG":3},"confidence":94}]}
                        """.formatted(id)));

        // More dishes than before: rows are rewritten, new ones get ids, the order is kept.
        update(bearer, id, changes("DINNER", dish("Phở bò", 350, 94), dish("Quẩy", 60, null), dish("Trà đá", 300, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[2].id").isNotEmpty());

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].meals", hasSize(1)))
                .andExpect(jsonPath("$[0].meals[0].type").value("DINNER"))
                .andExpect(jsonPath("$[0].meals[0].items[*].name").value(contains("Phở bò", "Quẩy", "Trà đá")))
                .andExpect(jsonPath("$[0].meals[0].items[0].grams").value(350))
                // 350 g + 60 g + 300 g at 110 kcal / 100 g.
                .andExpect(jsonPath("$[0].kcal").value(385 + 66 + 330));
    }

    @Test
    void deletesTheMealOnce() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        String id = idOf(save(bearer, meal("job-1", "LUNCH", "2026-10-14T12:05:00")).andExpect(status().isCreated()));

        mvc.perform(delete("/api/v1/meals/" + id).header("Authorization", bearer)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].meals", hasSize(0)))
                .andExpect(jsonPath("$[0].kcal").value(0));
        mvc.perform(delete("/api/v1/meals/" + id).header("Authorization", bearer)).andExpect(status().isNotFound());
        // The job is free again: saving it once more creates a new meal.
        save(bearer, meal("job-1", "LUNCH", "2026-10-14T12:05:00")).andExpect(status().isCreated());
    }

    @Test
    void nobodyChangesOrDeletesSomeoneElsesMeal() throws Exception {
        String owner = bearerFor(UUID.randomUUID());
        String id = idOf(save(owner, meal("job-1", "LUNCH", "2026-10-14T12:05:00")).andExpect(status().isCreated()));
        String other = bearerFor(UUID.randomUUID());

        update(other, id, changes("DINNER", dish("Phở bò", 400, 94))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/meals/" + id).header("Authorization", other)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/meals?from=2026-10-14&to=2026-10-14").header("Authorization", owner))
                .andExpect(jsonPath("$[0].meals[0].type").value("LUNCH"))
                .andExpect(jsonPath("$[0].meals[0].items", hasSize(2)));
    }

    @Test
    void rejectsInvalidUpdates() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        String id = idOf(save(bearer, meal("job-1", "LUNCH", "2026-10-14T12:05:00")).andExpect(status().isCreated()));

        update(bearer, id, changes("DINNER")).andExpect(status().isBadRequest());                          // no dishes
        update(bearer, id, changes("BRUNCH", dish("Phở bò", 400, 94))).andExpect(status().isBadRequest()); // unknown type
        update(bearer, id, changes("DINNER", dish("Phở bò", 0, 94))).andExpect(status().isBadRequest());   // 0 g
        update(bearer, "not-a-uuid", changes("DINNER", dish("Phở bò", 400, 94))).andExpect(status().isBadRequest());
        update(bearer, UUID.randomUUID().toString(), changes("DINNER", dish("Phở bò", 400, 94))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/meals/" + id).contentType(MediaType.APPLICATION_JSON).content(changes("DINNER", dish("Phở bò", 400, 94))))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/meals/" + id)).andExpect(status().isUnauthorized());
    }

    private ResultActions streak(String bearer, String today) throws Exception {
        return mvc.perform(get("/api/v1/meals/streak?today=" + today).header("Authorization", bearer));
    }

    private ResultActions update(String bearer, String id, String body) throws Exception {
        return mvc.perform(put("/api/v1/meals/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String changes(String type, String... dishes) {
        return """
                {"type":"%s","items":[%s]}""".formatted(type, String.join(",", dishes));
    }

    private static String dish(String name, int grams, Integer confidence) {
        return """
                {"name":"%s","grams":%d,"per100g":{"kcal":110,"proteinG":7,"carbsG":14,"fatG":3},"confidence":%s}"""
                .formatted(name, grams, confidence);
    }

    private ResultActions save(String bearer, String body) throws Exception {
        return mvc.perform(post("/api/v1/meals").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    /** A new meal, with a fresh id as the app would make it. */
    private static String meal(String jobId, String type, String loggedAt) {
        return meal(UUID.randomUUID(), jobId, type, loggedAt);
    }

    private static String meal(UUID id, String jobId, String type, String loggedAt) {
        return """
                {"id":"%s","jobId":%s,"type":"%s","loggedAt":"%s","photoUrl":"https://cdn.example/1.jpg","isEdited":true,"items":[
                  {"name":"Cơm tấm","grams":250,"per100g":{"kcal":130,"proteinG":2.7,"carbsG":28,"fatG":0.3},"confidence":94},
                  {"name":"Sườn nướng","grams":120,"per100g":{"kcal":290,"proteinG":24,"carbsG":4,"fatG":20},"confidence":null}]}
                """.formatted(id, jobId == null ? "null" : "\"" + jobId + "\"", type, loggedAt);
    }

    private static String bearerFor(UUID userId) {
        return "Bearer " + token(SECRET, userId);
    }

    private static String token(String secret, UUID userId) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("someone@visionfit.vn")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("uid", userId.toString())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
