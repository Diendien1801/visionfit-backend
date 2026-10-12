package com.visionfit.profile.service;

import com.visionfit.profile.domain.BodyProfile;
import com.visionfit.profile.dto.ProfileDto;
import com.visionfit.profile.repository.BodyProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {

    private final BodyProfileRepository repository;

    public ProfileService(BodyProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileDto> get(UUID userId) {
        return repository.findById(userId).map(ProfileDto::from);
    }

    /**
     * Creates the profile on first save, overwrites it afterwards.
     * ponytail: two concurrent first saves for one user race on the primary key and one gets a 500;
     * the app sends one save at a time, add a retry or an INSERT ... ON CONFLICT if that changes.
     */
    @Transactional
    public ProfileDto upsert(UUID userId, ProfileDto dto) {
        BodyProfile profile = repository.findById(userId).orElseGet(() -> new BodyProfile(userId));
        profile.update(dto.sex(), dto.ageYears(), dto.heightCm(), dto.weightKg(), dto.activityLevel(), dto.goal());
        return ProfileDto.from(repository.save(profile));
    }
}
