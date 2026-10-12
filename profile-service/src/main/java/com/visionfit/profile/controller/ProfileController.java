package com.visionfit.profile.controller;

import com.visionfit.profile.dto.ProfileDto;
import com.visionfit.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The caller's own profile. The user id always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/profile/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** 404 until the user finishes onboarding. */
    @GetMapping
    public ResponseEntity<ProfileDto> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(profileService.get(userId(jwt)));
    }

    @PutMapping
    public ProfileDto put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileDto body) {
        return profileService.upsert(userId(jwt), body);
    }

    /** Safe to parse: SecurityConfig rejects tokens whose `uid` is not a UUID. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }
}
