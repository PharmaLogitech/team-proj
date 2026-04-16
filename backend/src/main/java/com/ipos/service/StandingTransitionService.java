package com.ipos.service;

import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.repository.MerchantProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles automatic merchant standing transitions in a dedicated transaction.
 *
 * Methods here use REQUIRES_NEW so they commit independently of the calling
 * transaction.  This is necessary in OrderService.placeOrder — when an order
 * is rejected (exception thrown → outer tx rolled back), the standing change
 * must still persist.
 */
@Service
public class StandingTransitionService {

    private final MerchantProfileRepository profileRepository;

    public StandingTransitionService(MerchantProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Automatically moves a NORMAL merchant to IN_DEFAULT when their credit
     * limit has been exceeded.  Commits immediately in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoMoveToInDefault(Long merchantUserId) {
        profileRepository.findByUserId(merchantUserId).ifPresent(profile -> {
            if (profile.getStanding() == MerchantStanding.NORMAL) {
                profile.setStanding(MerchantStanding.IN_DEFAULT);
                profile.setInDefaultSince(Instant.now());
                profileRepository.save(profile);
            }
        });
    }

    /**
     * Automatically escalates an IN_DEFAULT merchant to SUSPENDED when they
     * have been in default for 30+ days.  Commits in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoMoveToSuspended(Long merchantUserId) {
        profileRepository.findByUserId(merchantUserId).ifPresent(profile -> {
            if (profile.getStanding() == MerchantStanding.IN_DEFAULT) {
                profile.setStanding(MerchantStanding.SUSPENDED);
                profileRepository.save(profile);
            }
        });
    }
}
