package com.smartlocator.service;

import com.smartlocator.model.ElementMetadata;
import com.smartlocator.model.LocatorCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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
        
        // Detect framework for better XPath generation
        String framework = metadataService.detectFramework(metadata);

        // XPath with ID (highest priority)
        if (metadata.getId() != null && !metadata.getId().isEmpty()) {
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(String.format("//%s[@id='%s']", tag, metadata.getId()))
                    .score(0)
                    .reason("XPath with ID")
                    .build());
            
            // Alternative: ID-only XPath
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(String.format("//*[@id='%s']", metadata.getId()))
                    .score(0)
                    .reason("XPath with ID (any tag)")
                    .build());
        }
        
        // Framework-specific XPath locators
        if (framework != null && metadata.getAttributes() != null) {
            candidates.addAll(generateFrameworkXPath(metadata, tag, framework));
        }

        // XPath with stable attribute
        String stableAttr = metadataService.getMostStableAttribute(metadata);
        if (stableAttr != null) {
            String attrValue = metadata.getAttributes().get(stableAttr);
            String xpath = String.format("//%s[@%s=%s]", 
                    tag, stableAttr, metadataService.escapeXPath(attrValue));
            String reason = framework != null ? 
                String.format("XPath with %s attribute (%s)", stableAttr, framework) :
                "XPath with stable attribute";
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(xpath)
                    .score(0)
                    .reason(reason)
                    .build());
        }

        // XPath with class (only first stable class)
        if (metadata.getClassList() != null && !metadata.getClassList().isEmpty()) {
            // Use only the first meaningful class
            String firstClass = metadata.getClassList().get(0);
            if (!firstClass.isEmpty()) {
                candidates.add(LocatorCandidate.builder()
                        .type("xpath")
                        .locator(String.format("//%s[contains(@class, '%s')]", tag, firstClass))
                        .score(0)
                        .reason("XPath with class")
                        .build());
            }
            
            // Combined class XPath (only if multiple meaningful classes)
            if (metadata.getClassList().size() > 1) {
                String classConditions = metadata.getClassList().stream()
                    .limit(2) // Only use first 2 classes
                    .map(c -> String.format("contains(@class, '%s')", c))
                    .collect(Collectors.joining(" and "));
                candidates.add(LocatorCandidate.builder()
                        .type("xpath")
                        .locator(String.format("//%s[%s]", tag, classConditions))
                        .score(0)
                        .reason("XPath with multiple classes")
                        .build());
            }
        }

        // XPath with text content (only if text is meaningful)
        if (metadataService.hasMeaningfulText(metadata)) {
            String text = metadataService.normalizeText(metadata.getInnerText());
            if (!text.isEmpty() && text.length() >= 3 && text.length() < 50) {
                candidates.add(LocatorCandidate.builder()
                        .type("xpath")
                        .locator(String.format("//%s[text()='%s']", tag, text))
                        .score(0)
                        .reason("XPath with text")
                        .build());
            }
        }

        // XPath with position (only add if no other good options)
        if (metadata.getNthIndex() > 0 && metadata.getParentTagName() != null && candidates.isEmpty()) {
            String xpath = String.format("//%s/%s[%d]", 
                    metadata.getParentTagName(), tag, metadata.getNthIndex());
            candidates.add(LocatorCandidate.builder()
                    .type("xpath")
                    .locator(xpath)
                    .score(0)
                    .reason("XPath with position")
                    .build());
        }
        
        // XPath with full hierarchy (grandparent -> parent -> current)
        candidates.addAll(generateHierarchicalXPath(metadata));
        
        // Dynamic XPath for repeating patterns (lists, tables, menus)
        candidates.addAll(generateDynamicXPath(metadata));

        return candidates;
    }
    
    /**
     * Generate XPath with full hierarchy (grandparent -> parent -> current)
     */
    private List<LocatorCandidate> generateHierarchicalXPath(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        
        if (metadata.getDomPath() == null || metadata.getDomPath().isEmpty()) {
            return candidates;
        }
        
        List<Map<String, Object>> domPath = metadata.getDomPath();
        int size = domPath.size();
        
        // Need at least grandparent, parent, and current (3 levels)
        if (size < 3) {
            return candidates;
        }
        
        // Get last 3 elements: grandparent, parent, current
        Map<String, Object> grandparent = domPath.get(size - 3);
        Map<String, Object> parent = domPath.get(size - 2);
        Map<String, Object> current = domPath.get(size - 1);
        
        // Build hierarchical XPath
        StringBuilder xpathBuilder = new StringBuilder("//");
        
        // Grandparent
        xpathBuilder.append(getNodeSelector(grandparent, false));
        xpathBuilder.append("/");
        
        // Parent
        xpathBuilder.append(getNodeSelector(parent, false));
        xpathBuilder.append("/");
        
        // Current
        xpathBuilder.append(getNodeSelector(current, true));
        
        candidates.add(LocatorCandidate.builder()
                .type("xpath")
                .locator(xpathBuilder.toString())
                .score(0)
                .reason("XPath with hierarchy (grandparent → parent → current)")
                .build());
        
        return candidates;
    }
    
    /**
     * Helper method to generate selector for a DOM node
     */
    private String getNodeSelector(Map<String, Object> node, boolean isCurrent) {
        String tag = (String) node.get("tag");
        if (tag == null || tag.isEmpty()) {
            tag = "*"; // fallback to any element
        }
        
        String id = (String) node.get("id");
        @SuppressWarnings("unchecked")
        List<String> classes = (List<String>) node.get("classes");
        
        // If node has ID, use it
        if (id != null && !id.isEmpty()) {
            return tag + "[@id='" + id + "']";
        }
        
        // If node has classes, use first one
        if (classes != null && !classes.isEmpty() && !classes.get(0).isEmpty()) {
            return tag + "[contains(@class, '" + classes.get(0) + "')]";
        }
        
        // Otherwise just use tag name
        return tag;
    }
    
    /**
     * Generate dynamic XPath for repeating patterns (lists, tables, menus)
     * These use %s placeholders for parameterized testing
     */
    private List<LocatorCandidate> generateDynamicXPath(ElementMetadata metadata) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        
        if (metadata.getDomPath() == null || metadata.getDomPath().isEmpty()) {
            return candidates;
        }
        
        String tag = metadata.getTagName();
        Map<String, String> attributes = metadata.getAttributes();
        
        // Check if element is part of a list/menu pattern (li > a structure)
        if ("a".equals(tag) && metadata.getParentTagName() != null && "li".equals(metadata.getParentTagName())) {
            // Menu item pattern: //ul[@data-menu='...']/li/a[text()='%s']
            List<Map<String, Object>> domPath = metadata.getDomPath();
            for (int i = domPath.size() - 1; i >= 0; i--) {
                Map<String, Object> node = domPath.get(i);
                String nodeTag = (String) node.get("tag");
                if ("ul".equals(nodeTag) || "nav".equals(nodeTag)) {
                    String nodeId = (String) node.get("id");
                    @SuppressWarnings("unchecked")
                    List<String> nodeClasses = (List<String>) node.get("classes");
                    
                    if (nodeId != null && !nodeId.isEmpty()) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//%s[@id='%s']/li/a[text()='%%s']", nodeTag, nodeId))
                                .score(0)
                                .reason("Dynamic XPath for menu items (use String.format with menu text)")
                                .build());
                    } else if (nodeClasses != null && !nodeClasses.isEmpty()) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//%s[contains(@class, '%s')]/li/a[text()='%%s']", nodeTag, nodeClasses.get(0)))
                                .score(0)
                                .reason("Dynamic XPath for menu items (use String.format with menu text)")
                                .build());
                    }
                    break;
                }
            }
            
            // Data attribute pattern for menu: //a[@data-page='%s']
            if (attributes != null) {
                for (String attr : attributes.keySet()) {
                    if (attr.startsWith("data-")) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//a[@%s='%%s']", attr))
                                .score(0)
                                .reason("Dynamic XPath for menu by data attribute (use String.format with attribute value)")
                                .build());
                        break; // Only add one data-attribute pattern
                    }
                }
            }
        }
        
        // Check if element is in a table row or is a table button
        if (metadata.getParentTagName() != null && ("tr".equals(metadata.getParentTagName()) || "td".equals(metadata.getParentTagName()))) {
            List<Map<String, Object>> domPath = metadata.getDomPath();
            
            // Find the table element and tr element
            String tableId = null;
            boolean isInTable = false;
            
            for (int i = domPath.size() - 1; i >= 0; i--) {
                Map<String, Object> node = domPath.get(i);
                String nodeTag = (String) node.get("tag");
                if ("table".equals(nodeTag)) {
                    tableId = (String) node.get("id");
                    isInTable = true;
                    break;
                }
            }
            
            if (isInTable && tableId != null && !tableId.isEmpty()) {
                // Check if this is an action button (Edit/Delete) inside a td
                if ("button".equals(tag) && attributes != null) {
                    String buttonClass = attributes.get("class");
                    String dataAction = attributes.get("data-action");
                    
                    // Generate dynamic XPath for action buttons using other column values
                    if (buttonClass != null && (buttonClass.contains("edit") || buttonClass.contains("delete"))) {
                        // Pattern 1: By user ID - //table[@id='...']/tbody/tr[@data-user-id='%s']//button[@data-action='edit']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[@data-user-id='%%s']//button[@data-action='%s']", 
                                        tableId, dataAction != null ? dataAction : (buttonClass.contains("edit") ? "edit" : "delete")))
                                .score(0)
                                .reason("Dynamic XPath for table action button by row ID (use String.format with row ID)")
                                .build());
                        
                        // Pattern 2: By name column - //table[@id='...']/tbody/tr[td[@class='user-name' and text()='%s']]//button[@class='edit-btn']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[td[contains(@class, 'user-name') and text()='%%s']]//button[contains(@class, '%s')]", 
                                        tableId, buttonClass.contains("edit") ? "edit" : "delete"))
                                .score(0)
                                .reason("Dynamic XPath for table action button by name column (use String.format with user name)")
                                .build());
                        
                        // Pattern 3: By email column - //table[@id='...']/tbody/tr[td[@class='user-email' and text()='%s']]//button[@class='edit-btn']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[td[contains(@class, 'user-email') and text()='%%s']]//button[contains(@class, '%s')]", 
                                        tableId, buttonClass.contains("edit") ? "edit" : "delete"))
                                .score(0)
                                .reason("Dynamic XPath for table action button by email column (use String.format with user email)")
                                .build());
                        
                        // Pattern 4: By any cell value (generic) - //table[@id='...']/tbody/tr[td[text()='%s']]//button[@class='edit-btn']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[td[text()='%%s']]//button[contains(@class, '%s')]", 
                                        tableId, buttonClass.contains("edit") ? "edit" : "delete"))
                                .score(0)
                                .reason("Dynamic XPath for table action button by any cell text (use String.format with any cell value)")
                                .build());
                        
                        // Pattern 5: By position - //table[@id='...']/tbody/tr[position()=%s]//button[@class='edit-btn']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[%%s]//button[contains(@class, '%s')]", 
                                        tableId, buttonClass.contains("edit") ? "edit" : "delete"))
                                .score(0)
                                .reason("Dynamic XPath for table action button by row position (use String.format with row number)")
                                .build());
                    }
                }
                
                // Pattern for regular table cells
                if ("td".equals(tag)) {
                    // Pattern: //table[@id='...']/tbody/tr[@data-user-id='%s']/td[@class='user-name']
                    if (attributes != null && attributes.containsKey("class")) {
                        String className = attributes.get("class");
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[@data-user-id='%%s']/td[@class='%s']", tableId, className))
                                .score(0)
                                .reason("Dynamic XPath for table cell by row ID (use String.format with row ID)")
                                .build());
                    }
                    
                    // Pattern by text content: //table[@id='...']/tbody/tr[td[text()='%s']]/td[position()=%d]
                    int cellIndex = metadata.getNthIndex();
                    if (cellIndex > 0) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//table[@id='%s']/tbody/tr[td[text()='%%s']]/td[%d]", tableId, cellIndex))
                                .score(0)
                                .reason("Dynamic XPath for table cell by row text (use String.format with cell text)")
                                .build());
                    }
                }
            }
        }
        
        // Check if element is part of a product list pattern
        if ("button".equals(tag) && attributes != null && attributes.containsKey("data-product")) {
            List<Map<String, Object>> domPath = metadata.getDomPath();
            
            for (int i = domPath.size() - 1; i >= 0; i--) {
                Map<String, Object> node = domPath.get(i);
                String nodeTag = (String) node.get("tag");
                if ("li".equals(nodeTag)) {
                    @SuppressWarnings("unchecked")
                    List<String> nodeClasses = (List<String>) node.get("classes");
                    if (nodeClasses != null && nodeClasses.contains("product-item")) {
                        // Pattern: //li[@data-product-id='%s']/button[@class='add-to-cart']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator("//li[@data-product-id='%s']/button[@class='add-to-cart']")
                                .score(0)
                                .reason("Dynamic XPath for product button by product ID (use String.format with product ID)")
                                .build());
                        
                        // Pattern by product name: //li[h3[text()='%s']]/button[@class='add-to-cart']
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator("//li[h3[@class='product-name' and text()='%s']]/button[@class='add-to-cart']")
                                .score(0)
                                .reason("Dynamic XPath for product button by product name (use String.format with product name)")
                                .build());
                        break;
                    }
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Generate framework-specific XPath locators
     */
    private List<LocatorCandidate> generateFrameworkXPath(ElementMetadata metadata, String tag, String framework) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        
        if ("Angular".equals(framework)) {
            // Angular-specific attributes
            for (String attr : metadata.getAttributes().keySet()) {
                if (attr.startsWith("ng-") || attr.startsWith("data-ng-")) {
                    String value = metadata.getAttributes().get(attr);
                    if (value != null && !value.isEmpty()) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//%s[@%s='%s']", tag, attr, value))
                                .score(0)
                                .reason("XPath with Angular " + attr)
                                .build());
                    }
                }
            }
        } else if ("Vue".equals(framework)) {
            // Vue-specific attributes
            for (String attr : metadata.getAttributes().keySet()) {
                if (attr.startsWith("v-") || attr.startsWith(":") || attr.startsWith("@")) {
                    String value = metadata.getAttributes().get(attr);
                    if (value != null && !value.isEmpty()) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//%s[@%s='%s']", tag, attr, value))
                                .score(0)
                                .reason("XPath with Vue " + attr)
                                .build());
                    }
                }
            }
        } else if ("React".equals(framework)) {
            // React-specific attributes (data-testid is common in React)
            for (String attr : metadata.getAttributes().keySet()) {
                if (attr.startsWith("data-react") || attr.equals("data-testid")) {
                    String value = metadata.getAttributes().get(attr);
                    if (value != null && !value.isEmpty()) {
                        candidates.add(LocatorCandidate.builder()
                                .type("xpath")
                                .locator(String.format("//%s[@%s='%s']", tag, attr, value))
                                .score(0)
                                .reason("XPath with React " + attr)
                                .build());
                    }
                }
            }
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

        // If it's an <a> tag treated as button, also add link role as alternative
        if ("button".equals(role) && "a".equals(metadata.getTagName())) {
            candidates.add(LocatorCandidate.builder()
                    .type("playwright")
                    .locator("page.getByRole(AriaRole.LINK)")
                    .score(0)
                    .reason("Playwright getByRole as link (alternative)")
                    .build());
        }

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
                // Check if <a> tag is used as a button (common pattern in modern web apps)
                if (metadata.getAttributes() != null) {
                    String className = metadata.getAttributes().get("class");
                    String href = metadata.getAttributes().get("href");
                    // If it has button-related classes or no/empty href, treat as button
                    if ((className != null && (className.contains("btn") || 
                                               className.contains("button") || 
                                               className.contains("Button"))) ||
                        (href != null && (href.equals("#") || href.equals("javascript:void(0)")))) {
                        return "button";
                    }
                }
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
