package com.visionfit.meal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A confirmed meal, mirrors the app's `ConfirmedMeal`. Enum names are the wire format, so renaming one breaks the app.
 *
 * Implements {@link Persistable} because the id is assigned, not generated: without it Spring Data
 * would see an id, take the meal for an existing row and `merge` it, a needless SELECT before the INSERT.
 */
@Entity
@Table(name = "meals")
public class Meal implements Persistable<UUID> {

    public enum Type { BREAKFAST, LUNCH, SNACK, DINNER }

    /** Created by the app before the first save and sent again on every retry: the key that stops duplicates. */
    @Id
    private UUID id;

    /** `true` until the meal is stored or loaded. */
    @Transient
    private boolean isNew = true;

    /** The `uid` claim of the owner's access token. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;

    @Column(name = "photo_url", length = 2048)
    private String photoUrl;

    @Column(name = "is_edited", nullable = false)
    private boolean edited;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "meal_id", nullable = false)
    @OrderBy("position")
    private List<MealItem> items = new ArrayList<>();

    protected Meal() {
        // for JPA
    }

    public Meal(UUID id, UUID userId, String jobId, Type type, LocalDateTime loggedAt, String photoUrl, boolean edited, List<MealItem> items) {
        this.id = id;
        this.userId = userId;
        this.jobId = jobId;
        this.type = type;
        this.loggedAt = loggedAt;
        this.photoUrl = photoUrl;
        this.edited = edited;
        this.items.addAll(items);
    }

    @PrePersist
    void stampCreatedAt() {
        createdAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    void markStored() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * The user changed the meal after saving it. Dishes are rewritten in place by position rather
     * than cleared and re-added: Hibernate inserts before it deletes, so fresh rows would hit
     * UNIQUE (meal_id, position) while the old ones still exist.
     */
    public void update(Type type, List<MealItem> replacement) {
        this.type = type;
        this.edited = true;
        for (int i = 0; i < replacement.size(); i++) {
            if (i < items.size()) {
                items.get(i).copyValuesFrom(replacement.get(i));
            } else {
                items.add(replacement.get(i));
            }
        }
        while (items.size() > replacement.size()) {
            items.remove(items.size() - 1);
        }
    }

    public Nutrition nutrition() {
        return Nutrition.sum(items, MealItem::nutrition);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getJobId() {
        return jobId;
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public boolean isEdited() {
        return edited;
    }

    public List<MealItem> getItems() {
        return items;
    }
}
