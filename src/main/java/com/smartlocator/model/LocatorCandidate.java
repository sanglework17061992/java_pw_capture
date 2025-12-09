package com.smartlocator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocatorCandidate {
    private String type; // "id", "css", "xpath", "playwright"
    private String locator;
    private double score;
    private String reason;
}
