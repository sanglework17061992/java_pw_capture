package com.smartlocator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocatorResult {
    private String bestLocator;
    private Map<String, String> candidates;
    private double score;
    private List<String> reasons;
    private ElementMetadata metadata;
}
