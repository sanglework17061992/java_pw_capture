package com.smartlocator.service;

import com.smartlocator.model.ElementMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class MetadataExtractionService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern HASH_PATTERN = Pattern.compile(
            "[0-9a-f]{32,}",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Check if an ID appears stable (not UUID or hash)
     */
    public boolean isStableId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        
        // Check for UUID pattern
        if (UUID_PATTERN.matcher(id).find()) {
            return false;
        }
        
        // Check for hash pattern
        if (HASH_PATTERN.matcher(id).matches()) {
            return false;
        }
        
        // Check for random-looking strings
        if (id.matches(".*\\d{10,}.*")) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if an attribute is considered stable for locators
     */
    public boolean isStableAttribute(String attrName) {
        return attrName.startsWith("data-test") ||
               attrName.startsWith("data-qa") ||
               attrName.startsWith("data-cy") ||
               attrName.startsWith("aria-") ||
               attrName.equals("role") ||
               attrName.equals("name") ||
               attrName.equals("type") ||
               attrName.equals("placeholder");
    }

    /**
     * Get the most stable attribute from metadata
     */
    public String getMostStableAttribute(ElementMetadata metadata) {
        if (metadata.getAttributes() == null) {
            return null;
        }

        // Priority order for stable attributes
        String[] priorityAttrs = {
            "data-test-id", "data-testid", "data-test", "data-qa", "data-cy",
            "aria-label", "aria-labelledby", "role", "name", "type", "placeholder"
        };

        for (String attr : priorityAttrs) {
            if (metadata.getAttributes().containsKey(attr)) {
                String value = metadata.getAttributes().get(attr);
                if (value != null && !value.isEmpty()) {
                    return attr;
                }
            }
        }

        return null;
    }

    /**
     * Normalize text content for matching
     */
    public String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * Check if element has meaningful text
     */
    public boolean hasMeaningfulText(ElementMetadata metadata) {
        String text = normalizeText(metadata.getInnerText());
        return text.length() > 0 && text.length() < 100;
    }

    /**
     * Escape special characters for CSS selector
     */
    public String escapeCssSelector(String value) {
        if (value == null) {
            return "";
        }
        // Escape special CSS characters
        return value.replaceAll("([\\s!\"#$%&'()*+,./:;<=>?@\\[\\\\\\]^`{|}~])", "\\\\$1");
    }

    /**
     * Escape special characters for XPath
     */
    public String escapeXPath(String value) {
        if (value == null) {
            return "";
        }
        
        // If no quotes, wrap in single quotes
        if (!value.contains("'") && !value.contains("\"")) {
            return "'" + value + "'";
        }
        
        // If contains single quote but no double quote, use double quotes
        if (value.contains("'") && !value.contains("\"")) {
            return "\"" + value + "\"";
        }
        
        // If contains both, use concat
        if (value.contains("'") && value.contains("\"")) {
            StringBuilder result = new StringBuilder("concat(");
            String[] parts = value.split("'");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    result.append(", \"'\", ");
                }
                result.append("'").append(parts[i]).append("'");
            }
            result.append(")");
            return result.toString();
        }
        
        return "'" + value + "'";
    }

    /**
     * Check if element is form input
     */
    public boolean isFormElement(ElementMetadata metadata) {
        String tag = metadata.getTagName();
        return "input".equals(tag) || 
               "textarea".equals(tag) || 
               "select".equals(tag) || 
               "button".equals(tag);
    }

    /**
     * Get element type description
     */
    public String getElementType(ElementMetadata metadata) {
        String tag = metadata.getTagName();
        
        if ("input".equals(tag)) {
            String type = metadata.getAttributes().getOrDefault("type", "text");
            return "input[type=" + type + "]";
        }
        
        return tag;
    }
}
