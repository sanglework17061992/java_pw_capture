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
public class ElementMetadata {
    private String tagName;
    private String id;
    private List<String> classList;
    private Map<String, String> attributes;
    private String innerText;
    private String normalizedText;
    private String parentTagName;
    private int nthIndex;
    private String cssPath;
    private String xpathPath;
    private boolean isUnique;
    private String outerHTML;
    private List<Map<String, Object>> domPath; // Full DOM hierarchy from body to current element
}
