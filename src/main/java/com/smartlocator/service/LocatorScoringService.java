package com.smartlocator.service;

import com.smartlocator.model.ElementMetadata;
import com.smartlocator.model.LocatorCandidate;
import com.smartlocator.model.LocatorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocatorScoringService {

    private final MetadataExtractionService metadataService;

    // Scoring weights
    private static final double STABILITY_WEIGHT = 0.40;
    private static final double SPECIFICITY_WEIGHT = 0.30;
    private static final double READABILITY_WEIGHT = 0.20;
    private static final double PERFORMANCE_WEIGHT = 0.10;

    /**
     * Score all locators and return the best result
     */
    public LocatorResult scoreAndSelectBest(List<LocatorCandidate> candidates, ElementMetadata metadata) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No locator candidates provided");
        }

        // Score each candidate
        for (LocatorCandidate candidate : candidates) {
            double score = calculateScore(candidate, metadata);
            candidate.setScore(score);
        }

        // Sort by score descending
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // Get best locator
        LocatorCandidate best = candidates.get(0);

        // Build candidates map
        Map<String, String> candidatesMap = new HashMap<>();
        for (LocatorCandidate candidate : candidates) {
            String key = candidate.getType();
            if (!candidatesMap.containsKey(key) || candidate.getScore() > scoreLocator(candidatesMap.get(key))) {
                candidatesMap.put(key, candidate.getLocator());
            }
        }

        // Build reasons
        List<String> reasons = buildReasons(best, metadata);

        return LocatorResult.builder()
                .bestLocator(best.getLocator())
                .candidates(candidatesMap)
                .score(best.getScore())
                .reasons(reasons)
                .metadata(metadata)
                .build();
    }

    /**
     * Calculate overall score for a locator
     */
    private double calculateScore(LocatorCandidate candidate, ElementMetadata metadata) {
        double stabilityScore = calculateStabilityScore(candidate, metadata);
        double specificityScore = calculateSpecificityScore(candidate, metadata);
        double readabilityScore = calculateReadabilityScore(candidate);
        double performanceScore = calculatePerformanceScore(candidate);

        return (stabilityScore * STABILITY_WEIGHT) +
               (specificityScore * SPECIFICITY_WEIGHT) +
               (readabilityScore * READABILITY_WEIGHT) +
               (performanceScore * PERFORMANCE_WEIGHT);
    }

    /**
     * Calculate stability score (40%)
     * Higher score for stable attributes that won't change
     */
    private double calculateStabilityScore(LocatorCandidate candidate, ElementMetadata metadata) {
        String locator = candidate.getLocator();
        double score = 50; // Base score

        // Unique ID gets highest stability
        if (candidate.getType().equals("id") && metadata.getId() != null) {
            if (metadataService.isStableId(metadata.getId())) {
                score = 100;
            } else {
                score = 30; // Dynamic ID
            }
        }

        // Stable attributes (data-test, aria-, role)
        if (containsStableAttribute(locator)) {
            score = 95;
        }

        // Role-based Playwright locators
        if (locator.contains("getByRole") || locator.contains("getByLabel")) {
            score = 90;
        }

        // Text-based locators (less stable but sometimes necessary)
        if (locator.contains("normalize-space()") || locator.contains("has-text")) {
            score = 60;
        }

        // Class-based locators (moderate stability)
        if (locator.contains("class") || locator.matches(".*\\.\\w+.*")) {
            score = 65;
        }

        // Index-based locators (low stability)
        if (locator.matches(".*\\[\\d+\\].*") || locator.contains("nth-of-type")) {
            score = 40;
        }

        // Absolute XPath (very low stability)
        if (locator.startsWith("/html/body")) {
            score = 15;
        }

        return score;
    }

    /**
     * Calculate specificity score (30%)
     * Higher score for locators that uniquely identify the element
     */
    private double calculateSpecificityScore(LocatorCandidate candidate, ElementMetadata metadata) {
        String locator = candidate.getLocator();
        double score = 50; // Base score

        // ID is most specific
        if (candidate.getType().equals("id")) {
            score = 100;
        }

        // Unique data-test attribute
        if (containsStableAttribute(locator)) {
            score = 90;
        }

        // Multiple attributes combined
        long attrCount = locator.chars().filter(ch -> ch == '@').count();
        if (attrCount > 1) {
            score = 85;
        }

        // Single attribute
        if (attrCount == 1) {
            score = 75;
        }

        // Tag with class
        if (locator.matches(".*\\w+\\.\\w+.*")) {
            score = 70;
        }

        // Tag only (low specificity)
        if (locator.matches("^\\w+$") || locator.matches("^//\\w+$")) {
            score = 30;
        }

        // Check if element is marked as unique
        if (metadata.isUnique()) {
            score = Math.min(100, score + 10);
        }

        return score;
    }

    /**
     * Calculate readability score (20%)
     * Higher score for human-readable locators
     */
    private double calculateReadabilityScore(LocatorCandidate candidate) {
        String locator = candidate.getLocator();
        double score = 50; // Base score

        // Length penalty
        int length = locator.length();
        if (length < 30) {
            score = 100;
        } else if (length < 50) {
            score = 85;
        } else if (length < 80) {
            score = 70;
        } else if (length < 120) {
            score = 55;
        } else {
            score = 40;
        }

        // Complexity penalty
        long slashCount = locator.chars().filter(ch -> ch == '/').count();
        if (slashCount > 5) {
            score -= 20;
        } else if (slashCount > 3) {
            score -= 10;
        }

        // Bonus for semantic locators
        if (locator.contains("getByRole") || locator.contains("getByLabel")) {
            score += 15;
        }

        // Bonus for readable attribute names
        if (containsStableAttribute(locator)) {
            score += 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculate performance score (10%)
     * Higher score for faster locators
     */
    private double calculatePerformanceScore(LocatorCandidate candidate) {
        String locator = candidate.getLocator();
        double score = 50; // Base score

        // ID is fastest
        if (candidate.getType().equals("id")) {
            score = 100;
        }

        // Direct attribute lookup is fast
        if (locator.matches(".*\\[@\\w+='[^']*'\\]") && !locator.contains("//")) {
            score = 90;
        }

        // CSS selectors are generally fast
        if (candidate.getType().equals("css") && !locator.contains(":nth-of-type")) {
            score = 85;
        }

        // XPath with specific attributes is moderately fast
        if (candidate.getType().equals("xpath") && containsStableAttribute(locator)) {
            score = 75;
        }

        // Text-based searches are slower
        if (locator.contains("normalize-space()") || locator.contains("has-text") || 
            locator.contains("contains(")) {
            score = 50;
        }

        // Complex XPath is slow
        if (locator.contains("//") && locator.chars().filter(ch -> ch == '/').count() > 4) {
            score = 40;
        }

        // Absolute XPath is slowest
        if (locator.startsWith("/html/body")) {
            score = 20;
        }

        return score;
    }

    /**
     * Check if locator contains stable attribute
     */
    private boolean containsStableAttribute(String locator) {
        return locator.contains("data-test") ||
               locator.contains("data-qa") ||
               locator.contains("data-cy") ||
               locator.contains("aria-") ||
               locator.contains("@role") ||
               locator.contains("@name");
    }

    /**
     * Build human-readable reasons for the score
     */
    private List<String> buildReasons(LocatorCandidate best, ElementMetadata metadata) {
        List<String> reasons = new ArrayList<>();
        String locator = best.getLocator();

        // Stability reasons
        if (best.getType().equals("id") && metadataService.isStableId(metadata.getId())) {
            reasons.add("Unique and stable ID detected");
        }

        if (containsStableAttribute(locator)) {
            reasons.add("Stable attribute detected (data-test, aria-*, role)");
        }

        // Specificity reasons
        if (metadata.isUnique()) {
            reasons.add("Locator uniquely identifies the element");
        }

        // Readability reasons
        if (locator.length() < 50) {
            reasons.add("Locator is short and readable");
        }

        if (locator.contains("getByRole") || locator.contains("getByLabel")) {
            reasons.add("Semantic Playwright locator");
        }

        // XPath reasons
        if (best.getType().equals("xpath")) {
            if (!locator.startsWith("/html")) {
                reasons.add("Relative XPath (not absolute)");
            }
            if (!locator.matches(".*\\[\\d+\\].*")) {
                reasons.add("No index-based selection");
            }
        }

        // Performance reasons
        if (best.getType().equals("id") || best.getType().equals("css")) {
            reasons.add("Fast DOM query performance");
        }

        // Add generic reason if no specific reasons
        if (reasons.isEmpty()) {
            reasons.add("Best available locator based on element attributes");
        }

        return reasons;
    }

    /**
     * Quick score for a locator string (used for deduplication)
     */
    private double scoreLocator(String locator) {
        double score = 50;
        
        if (locator.startsWith("#")) score = 95;
        else if (locator.contains("data-test")) score = 90;
        else if (locator.contains("aria-")) score = 85;
        else if (locator.length() < 30) score = 80;
        else if (locator.contains("[")) score = 75;
        
        return score;
    }
}
