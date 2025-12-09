package com.smartlocator.service;

import com.smartlocator.model.ElementMetadata;
import com.smartlocator.model.LocatorCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocatorGenerationService {

    private final MetadataExtractionService metadataService;

    /**
     * Generate all possible locators for an element
     */
    public List<LocatorCandidate> generateAllLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();

        // Generate ID locator
        LocatorCandidate idLocator = generateIdLocator(metadata);
        if (idLocator != null) {
            candidates.add(idLocator);
        }

        // Generate attribute-based locators
        candidates.addAll(generateAttributeLocators(metadata));

        // Generate CSS locators
        candidates.addAll(generateCssLocators(metadata));

        // Generate XPath locators
        candidates.addAll(generateXPathLocators(metadata));

        // Generate Playwright locators
        candidates.addAll(generatePlaywrightLocators(metadata));

        // Generate text-based locators
        if (metadataService.hasMeaningfulText(metadata)) {
            candidates.addAll(generateTextBasedLocators(metadata));
        }

        return candidates;
    }

    /**
     * Generate ID-based locator
     */
    private LocatorCandidate generateIdLocator(ElementMetadata metadata) {
        String id = metadata.getId();
        
        if (id == null || id.isEmpty()) {
            return null;
        }

        if (!metadataService.isStableId(id)) {
            return null;
        }

        return LocatorCandidate.builder()
                .type("id")
                .locator("#" + id)
                .score(0)
                .reason("Unique and stable ID")
                .build();
    }

    /**
     * Generate attribute-based locators
     */
    private List<LocatorCandidate> generateAttributeLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        
        if (metadata.getAttributes() == null) {
            return candidates;
        }

        for (Map.Entry<String, String> attr : metadata.getAttributes().entrySet()) {
            String attrName = attr.getKey();
            String attrValue = attr.getValue();

            if (metadataService.isStableAttribute(attrName) && attrValue != null && !attrValue.isEmpty()) {
                // CSS version
                String cssLocator = String.format("%s[%s='%s']", 
                        metadata.getTagName(), attrName, attrValue);
                candidates.add(LocatorCandidate.builder()
                        .type("css")
                        .locator(cssLocator)
                        .score(0)
                        .reason("Stable attribute: " + attrName)
                        .build());

                // XPath version
                String xpathLocator = String.format("//%s[@%s=%s]", 
                        metadata.getTagName(), attrName, metadataService.escapeXPath(attrValue));
                candidates.add(LocatorCandidate.builder()
                        .type("xpath")
                        .locator(xpathLocator)
                        .score(0)
                        .reason("Stable attribute: " + attrName)
                        .build());
            }
        }

        return candidates;
    }

    /**
     * Generate CSS locators
     */
    private List<LocatorCandidate> generateCssLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        String tag = metadata.getTagName();

        // Simple tag selector
        candidates.add(LocatorCandidate.builder()
                .type("css")
                .locator(tag)
                .score(0)
                .reason("Simple tag selector")
                .build());

        // Tag with class
        if (metadata.getClassList() != null && !metadata.getClassList().isEmpty()) {
            String classSelector = tag + "." + String.join(".", metadata.getClassList());
            candidates.add(LocatorCandidate.builder()
                    .type("css")
                    .locator(classSelector)
                    .score(0)
                    .reason("Tag with class")
                    .build());

            // Individual classes
            for (String className : metadata.getClassList()) {
                candidates.add(LocatorCandidate.builder()
                        .type("css")
                        .locator("." + className)
                        .score(0)
                        .reason("Class selector")
                        .build());
            }
        }

        // Tag with type attribute (for inputs)
        if ("input".equals(tag) && metadata.getAttributes() != null) {
            String type = metadata.getAttributes().get("type");
            if (type != null) {
                candidates.add(LocatorCandidate.builder()
                        .type("css")
                        .locator(String.format("input[type='%s']", type))
                        .score(0)
                        .reason("Input with type")
                        .build());
            }
        }

        // Nth-of-type as fallback
        if (metadata.getNthIndex() > 0) {
            String nthSelector = String.format("%s:nth-of-type(%d)", tag, metadata.getNthIndex());
            candidates.add(LocatorCandidate.builder()
                    .type("css")
                    .locator(nthSelector)
                    .score(0)
                    .reason("Nth-of-type (fallback)")
                    .build());
        }

        return candidates;
    }

    /**
     * Generate Smart XPath locators
     */
    private List<LocatorCandidate> generateXPathLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        String tag = metadata.getTagName();

        // Simple tag xpath
        candidates.add(LocatorCandidate.builder()
                .type("xpath")
                .locator("//" + tag)
                .score(0)
                .reason("Simple tag XPath")
                .build());

        // XPath with stable attribute
        String stableAttr = metadataService.getMostStableAttribute(metadata);
        if (stableAttr != null) {
            String attrValue = metadata.getAttributes().get(stableAttr);
            String xpath = String.format("//%s[@%s=%s]", 
                    tag, stableAttr, metadataService.escapeXPath(attrValue));
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(xpath)
                    .score(0)
                    .reason("XPath with stable attribute")
                    .build());
        }

        // XPath with class
        if (metadata.getClassList() != null && !metadata.getClassList().isEmpty()) {
            for (String className : metadata.getClassList()) {
                String xpath = String.format("//%s[contains(@class, '%s')]", tag, className);
                candidates.add(LocatorCandidate.builder()
                        .type("xpath")
                        .locator(xpath)
                        .score(0)
                        .reason("XPath with class contains")
                        .build());
            }
        }

        // XPath with position (fallback)
        if (metadata.getNthIndex() > 0 && metadata.getParentTagName() != null) {
            String xpath = String.format("//%s/%s[%d]", 
                    metadata.getParentTagName(), tag, metadata.getNthIndex());
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(xpath)
                    .score(0)
                    .reason("XPath with position (fallback)")
                    .build());
        }

        return candidates;
    }

    /**
     * Generate Playwright-specific locators
     */
    private List<LocatorCandidate> generatePlaywrightLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();

        // ID locator
        if (metadata.getId() != null && metadataService.isStableId(metadata.getId())) {
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.locator(\"#%s\")", metadata.getId()))
                    .score(0)
                    .reason("Playwright ID locator")
                    .build());
        }

        // Data-test attribute
        String stableAttr = metadataService.getMostStableAttribute(metadata);
        if (stableAttr != null) {
            String attrValue = metadata.getAttributes().get(stableAttr);
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.locator(\"[%s='%s']\")", stableAttr, attrValue))
                    .score(0)
                    .reason("Playwright stable attribute locator")
                    .build());
        }

        // Generate getByRole locators
        candidates.addAll(generatePlaywrightRoleLocators(metadata));

        // Label locator
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("aria-label")) {
            String label = metadata.getAttributes().get("aria-label");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByLabel(\"%s\")", escapeQuotes(label)))
                    .score(0)
                    .reason("Playwright label locator")
                    .build());
        }

        // Placeholder locator
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("placeholder")) {
            String placeholder = metadata.getAttributes().get("placeholder");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByPlaceholder(\"%s\")", escapeQuotes(placeholder)))
                    .score(0)
                    .reason("Playwright placeholder locator")
                    .build());
        }

        // Title locator
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("title")) {
            String title = metadata.getAttributes().get("title");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByTitle(\"%s\")", escapeQuotes(title)))
                    .score(0)
                    .reason("Playwright title locator")
                    .build());
        }

        // Alt text locator (for images)
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("alt")) {
            String alt = metadata.getAttributes().get("alt");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByAltText(\"%s\")", escapeQuotes(alt)))
                    .score(0)
                    .reason("Playwright alt text locator")
                    .build());
        }

        // Test ID locator
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("data-testid")) {
            String testId = metadata.getAttributes().get("data-testid");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByTestId(\"%s\")", escapeQuotes(testId)))
                    .score(0)
                    .reason("Playwright test ID locator")
                    .build());
        }

        return candidates;
    }

    /**
     * Generate Playwright getByRole locators with options
     */
    private List<LocatorCandidate> generatePlaywrightRoleLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        String role = inferAriaRole(metadata);

        if (role == null) {
            return candidates;
        }

        // Basic role locator
        candidates.add(LocatorCandidate.builder()
                .type("playwright")
                .locator(String.format("page.getByRole(AriaRole.%s)", role.toUpperCase()))
                .score(0)
                .reason("Playwright getByRole locator")
                .build());

        // Role with name option (from text content)
        String text = metadataService.normalizeText(metadata.getInnerText());
        if (!text.isEmpty()) {
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByRole(AriaRole.%s, new Page.GetByRoleOptions().setName(\"%s\"))", 
                            role.toUpperCase(), escapeQuotes(text)))
                    .score(0)
                    .reason("Playwright getByRole with name")
                    .build());
        }

        // Role with aria-label
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("aria-label")) {
            String label = metadata.getAttributes().get("aria-label");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByRole(AriaRole.%s, new Page.GetByRoleOptions().setName(\"%s\"))", 
                            role.toUpperCase(), escapeQuotes(label)))
                    .score(0)
                    .reason("Playwright getByRole with aria-label")
                    .build());
        }

        // Role with checked state (for checkboxes/radio)
        if (("checkbox".equals(role) || "radio".equals(role)) && metadata.getAttributes() != null) {
            if (metadata.getAttributes().containsKey("checked")) {
                candidates.add(LocatorCandidate.builder()
                        .type("playwright")
                        .locator(String.format("page.getByRole(AriaRole.%s, new Page.GetByRoleOptions().setChecked(true))", 
                                role.toUpperCase()))
                        .score(0)
                        .reason("Playwright getByRole with checked state")
                        .build());
            }
        }

        // Role with pressed state (for buttons)
        if ("button".equals(role) && metadata.getAttributes() != null 
                && metadata.getAttributes().containsKey("aria-pressed")) {
            String pressed = metadata.getAttributes().get("aria-pressed");
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.getByRole(AriaRole.%s, new Page.GetByRoleOptions().setPressed(%s))", 
                            role.toUpperCase(), pressed))
                    .score(0)
                    .reason("Playwright getByRole with pressed state")
                    .build());
        }

        return candidates;
    }

    /**
     * Infer ARIA role from element tag and attributes
     */
    private String inferAriaRole(ElementMetadata metadata) {
        String tag = metadata.getTagName();
        
        // Check explicit role attribute first
        if (metadata.getAttributes() != null && metadata.getAttributes().containsKey("role")) {
            return metadata.getAttributes().get("role");
        }

        // Infer from tag name
        switch (tag) {
            case "button":
                return "button";
            case "a":
                return "link";
            case "input":
                String type = metadata.getAttributes() != null ? 
                        metadata.getAttributes().get("type") : null;
                if (type != null) {
                    switch (type) {
                        case "checkbox":
                            return "checkbox";
                        case "radio":
                            return "radio";
                        case "button":
                        case "submit":
                            return "button";
                        default:
                            return "textbox";
                    }
                }
                return "textbox";
            case "textarea":
                return "textbox";
            case "select":
                return "combobox";
            case "img":
                return "img";
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                return "heading";
            case "ul":
            case "ol":
                return "list";
            case "li":
                return "listitem";
            case "table":
                return "table";
            case "tr":
                return "row";
            case "td":
                return "cell";
            case "form":
                return "form";
            case "nav":
                return "navigation";
            case "main":
                return "main";
            case "article":
                return "article";
            case "aside":
                return "complementary";
            case "section":
                return "region";
            case "header":
                return "banner";
            case "footer":
                return "contentinfo";
            default:
                return null;
        }
    }

    /**
     * Escape quotes in strings for Java code
     */
    private String escapeQuotes(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "\\\"");
    }

    /**
     * Generate text-based locators
     */
    private List<LocatorCandidate> generateTextBasedLocators(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        String text = metadataService.normalizeText(metadata.getInnerText());

        if (text.isEmpty()) {
            return candidates;
        }

        // XPath with text
        String xpath = String.format("//%s[normalize-space()=%s]", 
                metadata.getTagName(), metadataService.escapeXPath(text));
        candidates.add(LocatorCandidate.builder()
                .type("xpath")
                .locator(xpath)
                .score(0)
                .reason("XPath with exact text")
                .build());

        // XPath with contains text
        String xpathContains = String.format("//%s[contains(normalize-space(), %s)]", 
                metadata.getTagName(), metadataService.escapeXPath(text));
        candidates.add(LocatorCandidate.builder()
                .type("xpath")
                .locator(xpathContains)
                .score(0)
                .reason("XPath with text contains")
                .build());

        // Playwright text locator
        if (metadata.getTagName().equals("button") || metadata.getTagName().equals("a")) {
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator(String.format("page.locator(\"%s:has-text('%s')\")", 
                            metadata.getTagName(), text))
                    .score(0)
                    .reason("Playwright text-based locator")
                    .build());
        }

        return candidates;
    }
}
