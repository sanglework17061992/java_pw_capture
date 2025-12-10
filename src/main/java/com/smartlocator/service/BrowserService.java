package com.smartlocator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.smartlocator.model.ElementMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

@Service
@Slf4j
public class BrowserService {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private String currentBrowserType = "chromium";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        log.info("Initializing Playwright...");
        playwright = Playwright.create();
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up Playwright resources...");
        closeBrowser();
        if (playwright != null) {
            playwright.close();
        }
    }

    public void openUrl(String url, String browserType) {
        try {
            // Close existing browser if different type requested
            if (browser != null && !currentBrowserType.equals(browserType)) {
                closeBrowser();
            }

            // Launch browser if not already running
            if (browser == null) {
                currentBrowserType = browserType;
                BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(50);

                browser = switch (browserType.toLowerCase()) {
                    case "firefox" -> playwright.firefox().launch(options);
                    case "webkit" -> playwright.webkit().launch(options);
                    default -> playwright.chromium().launch(options);
                };

                context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080));
                page = context.newPage();
                
                // Maximize the browser window
                page.setViewportSize(1920, 1080);
                
                // Add listener to re-inject script on navigation
                page.onLoad(p -> {
                    log.info("Page loaded, re-injecting highlighter script");
                    injectHighlighterScript();
                });
            }

            // Navigate to URL
            page.navigate(url);
            log.info("Navigated to: {}", url);

            // Inject element highlighter script
            injectHighlighterScript();

        } catch (Exception e) {
            log.error("Error opening URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to open URL: " + e.getMessage());
        }
    }

    public ElementMetadata captureElement(String selector) {
        if (page == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            Locator locator = page.locator(selector);
            ElementHandle element = locator.elementHandle();

            if (element == null) {
                throw new RuntimeException("Element not found: " + selector);
            }

            return extractMetadata(element);
        } catch (Exception e) {
            log.error("Error capturing element: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to capture element: " + e.getMessage());
        }
    }

    public ElementMetadata captureElementByCoordinates(int x, int y) {
        if (page == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            // Use JavaScript to get element at coordinates
            Object result = page.evaluate(
                    "({ x, y }) => {" +
                            "  const element = document.elementFromPoint(x, y);" +
                            "  if (!element) return null;" +
                            "  const rect = element.getBoundingClientRect();" +
                            "  return {" +
                            "    selector: element.tagName.toLowerCase() + " +
                            "              (element.id ? '#' + element.id : '') + " +
                            "              (element.className ? '.' + element.className.split(' ').join('.') : '')" +
                            "  };" +
                            "}",
                    Map.of("x", x, "y", y)
            );

            if (result == null) {
                throw new RuntimeException("No element found at coordinates");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            String selector = (String) resultMap.get("selector");

            return captureElement(selector);
        } catch (Exception e) {
            log.error("Error capturing element by coordinates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to capture element: " + e.getMessage());
        }
    }

    private ElementMetadata extractMetadata(ElementHandle element) {
        try {
            // Execute JavaScript to extract all metadata
            Object metadata = page.evaluate(
                    "element => {" +
                            "  const getXPath = (el) => {" +
                            "    if (el.id) return `//*[@id='${el.id}']`;" +
                            "    const parts = [];" +
                            "    while (el && el.nodeType === Node.ELEMENT_NODE) {" +
                            "      let count = 0;" +
                            "      let index = 0;" +
                            "      for (let sibling = el.parentNode?.firstChild; sibling; sibling = sibling.nextSibling) {" +
                            "        if (sibling.nodeType === Node.ELEMENT_NODE && sibling.tagName === el.tagName) {" +
                            "          count++;" +
                            "          if (sibling === el) index = count;" +
                            "        }" +
                            "      }" +
                            "      const tagName = el.tagName.toLowerCase();" +
                            "      const part = count > 1 ? `${tagName}[${index}]` : tagName;" +
                            "      parts.unshift(part);" +
                            "      el = el.parentNode;" +
                            "    }" +
                            "    return '//' + parts.join('/');" +
                            "  };" +
                            "" +
                            "  const getCssPath = (el) => {" +
                            "    if (el.id) return `#${el.id}`;" +
                            "    const parts = [];" +
                            "    while (el && el.nodeType === Node.ELEMENT_NODE) {" +
                            "      let selector = el.tagName.toLowerCase();" +
                            "      if (el.id) {" +
                            "        selector = `#${el.id}`;" +
                            "        parts.unshift(selector);" +
                            "        break;" +
                            "      } else if (el.className) {" +
                            "        selector += '.' + el.className.trim().split(/\\s+/).join('.');" +
                            "      }" +
                            "      parts.unshift(selector);" +
                            "      el = el.parentNode;" +
                            "    }" +
                            "    return parts.join(' > ');" +
                            "  };" +
                            "" +
                            "  const getNthIndex = (el) => {" +
                            "    let index = 1;" +
                            "    let sibling = el.previousElementSibling;" +
                            "    while (sibling) {" +
                            "      if (sibling.tagName === el.tagName) index++;" +
                            "      sibling = sibling.previousElementSibling;" +
                            "    }" +
                            "    return index;" +
                            "  };" +
                            "" +
                            "  const attrs = {};" +
                            "  for (let attr of element.attributes) {" +
                            "    attrs[attr.name] = attr.value;" +
                            "  }" +
                            "" +
                            "  const getDomPath = (el) => {" +
                            "    const path = [];" +
                            "    let current = el;" +
                            "    while (current && current.tagName) {" +
                            "      const nodeInfo = {" +
                            "        tag: current.tagName.toLowerCase()," +
                            "        id: current.id || null," +
                            "        classes: Array.from(current.classList)," +
                            "        text: (current.childNodes.length === 1 && current.childNodes[0].nodeType === 3) ? current.textContent.trim() : null," +
                            "        isCurrent: (current === el)" +
                            "      };" +
                            "      path.unshift(nodeInfo);" +
                            "      if (current.tagName.toLowerCase() === 'body') break;" +
                            "      current = current.parentElement;" +
                            "    }" +
                            "    return path;" +
                            "  };" +
                            "" +
                            "  return {" +
                            "    tagName: element.tagName.toLowerCase()," +
                            "    id: element.id || null," +
                            "    classList: Array.from(element.classList)," +
                            "    attributes: attrs," +
                            "    innerText: element.innerText || ''," +
                            "    normalizedText: element.textContent?.trim().replace(/\\\\s+/g, ' ') || ''," +
                            "    parentTagName: element.parentElement?.tagName.toLowerCase() || null," +
                            "    nthIndex: getNthIndex(element)," +
                            "    cssPath: getCssPath(element)," +
                            "    xpathPath: getXPath(element)," +
                            "    outerHTML: element.outerHTML," +
                            "    domPath: getDomPath(element)" +
                            "  };" +
                            "}",
                    element
            );

            // Convert to ElementMetadata
            @SuppressWarnings("unchecked")
            Map<String, Object> metadataMap = (Map<String, Object>) metadata;

            return ElementMetadata.builder()
                    .tagName((String) metadataMap.get("tagName"))
                    .id((String) metadataMap.get("id"))
                    .classList(convertToStringList(metadataMap.get("classList")))
                    .attributes(convertToStringMap(metadataMap.get("attributes")))
                    .innerText((String) metadataMap.get("innerText"))
                    .normalizedText((String) metadataMap.get("normalizedText"))
                    .parentTagName((String) metadataMap.get("parentTagName"))
                    .nthIndex(((Number) metadataMap.get("nthIndex")).intValue())
                    .cssPath((String) metadataMap.get("cssPath"))
                    .xpathPath((String) metadataMap.get("xpathPath"))
                    .outerHTML((String) metadataMap.get("outerHTML"))
                    .isUnique(checkUniqueness((String) metadataMap.get("cssPath")))
                    .domPath(convertToListOfMaps(metadataMap.get("domPath")))
                    .build();

        } catch (Exception e) {
            log.error("Error extracting metadata: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract metadata: " + e.getMessage());
        }
    }

    private boolean checkUniqueness(String cssSelector) {
        try {
            return page.locator(cssSelector).count() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> convertToStringList(Object obj) {
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> convertToStringMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, String>) obj;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToListOfMaps(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }
        return new ArrayList<>();
    }

    private void injectHighlighterScript() {
        String script = """
            () => {
              if (window.__smartLocatorInjected) return;
              window.__smartLocatorInjected = true;
              window.__hoveredElement = null;
              
              // Create highlight box
              let highlightBox = document.createElement('div');
              highlightBox.id = '__smart-locator-highlight';
              highlightBox.style.cssText = `
                position: absolute;
                border: 2px solid #667eea;
                background: rgba(102, 126, 234, 0.1);
                pointer-events: none;
                z-index: 999999;
                display: none;
                box-shadow: 0 0 10px rgba(102, 126, 234, 0.5);
              `;
              document.body.appendChild(highlightBox);
              
              // Create info tooltip
              let tooltip = document.createElement('div');
              tooltip.id = '__smart-locator-tooltip';
              tooltip.style.cssText = `
                position: absolute;
                background: rgba(0, 0, 0, 0.9);
                color: white;
                padding: 8px 12px;
                border-radius: 4px;
                font-size: 12px;
                font-family: monospace;
                pointer-events: none;
                z-index: 1000000;
                display: none;
                max-width: 300px;
                word-wrap: break-word;
              `;
              document.body.appendChild(tooltip);
              
              // Create instructions banner
              let banner = document.createElement('div');
              banner.id = '__smart-locator-banner';
              banner.style.cssText = `
                position: fixed;
                top: 10px;
                right: 10px;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 12px 20px;
                border-radius: 8px;
                font-size: 14px;
                font-family: Arial, sans-serif;
                z-index: 1000001;
                box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                cursor: pointer;
              `;
              banner.innerHTML = 'Hover + Ctrl+Click to capture element<br><small style="opacity:0.8">Press ESC to exit capture mode</small>';
              document.body.appendChild(banner);
              
              let captureMode = true;
              
              banner.addEventListener('click', () => {
                captureMode = !captureMode;
                banner.innerHTML = captureMode ? 
                  'Hover + Ctrl+Click to capture element<br><small style="opacity:0.8">Press ESC to exit capture mode</small>' : 
                  'Capture mode paused<br><small style="opacity:0.8">Click to resume</small>';
                banner.style.background = captureMode ? 
                  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' : 
                  'linear-gradient(135deg, #666 0%, #888 100%)';
                if (!captureMode) {
                  highlightBox.style.display = 'none';
                  tooltip.style.display = 'none';
                }
              });
              
              document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                  banner.remove();
                  highlightBox.remove();
                  tooltip.remove();
                  captureMode = false;
                }
              });
              
              document.addEventListener('mouseover', (e) => {
                if (!captureMode) return;
                e.stopPropagation();
                window.__hoveredElement = e.target;
                
                const rect = e.target.getBoundingClientRect();
                highlightBox.style.top = (rect.top + window.scrollY) + 'px';
                highlightBox.style.left = (rect.left + window.scrollX) + 'px';
                highlightBox.style.width = rect.width + 'px';
                highlightBox.style.height = rect.height + 'px';
                highlightBox.style.display = 'block';
                
                // Show element info below the element
                let tagName = e.target.tagName.toLowerCase();
                let idInfo = e.target.id ? '#' + e.target.id : '';
                let classInfo = e.target.className ? '.' + e.target.className.split(' ').join('.') : '';
                tooltip.textContent = tagName + idInfo + classInfo;
                // Position tooltip below the element
                tooltip.style.top = (rect.bottom + window.scrollY + 5) + 'px';
                tooltip.style.left = (rect.left + window.scrollX) + 'px';
                tooltip.style.display = 'block';
              });
              
              document.addEventListener('mouseout', (e) => {
                if (!captureMode) return;
              });
              
              document.addEventListener('click', (e) => {
                if (!captureMode || !window.__hoveredElement) return;
                // Only capture if Ctrl (or Cmd on Mac) is pressed
                if (!e.ctrlKey && !e.metaKey) return;
                e.preventDefault();
                e.stopPropagation();
                window.__selectedElement = window.__hoveredElement;
              }, true);
            }
            """;
        page.evaluate(script);
    }

    public void closeBrowser() {
        if (page != null) {
            page.close();
            page = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    public void highlightElement(String selector) {
        if (page == null) {
            throw new IllegalStateException("Browser is not open");
        }
        
        try {
            // JavaScript to highlight the element
            String script = """
                (selector) => {
                    // Remove any existing highlights
                    document.querySelectorAll('.copilot-highlight-temp').forEach(el => {
                        el.classList.remove('copilot-highlight-temp');
                        el.style.outline = '';
                        el.style.outlineOffset = '';
                    });
                    
                    // Find and highlight the new element
                    const element = document.querySelector(selector);
                    if (element) {
                        element.classList.add('copilot-highlight-temp');
                        element.style.outline = '3px solid #ff6b6b';
                        element.style.outlineOffset = '2px';
                        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        
                        // Remove highlight after 3 seconds
                        setTimeout(() => {
                            element.classList.remove('copilot-highlight-temp');
                            element.style.outline = '';
                            element.style.outlineOffset = '';
                        }, 3000);
                    }
                }
                """;
            page.evaluate(script, selector);
        } catch (Exception e) {
            log.error("Error highlighting element with selector: {}", selector, e);
            throw new RuntimeException("Failed to highlight element: " + e.getMessage());
        }
    }

    public ElementMetadata captureElementBySelector(String selector) {
        if (page == null) {
            throw new IllegalStateException("Browser is not open");
        }
        
        try {
            // First, find the element using the selector
            Locator locator = page.locator(selector).first();
            int count = locator.count();
            
            if (count == 0) {
                throw new IllegalStateException("Element not found with selector: " + selector);
            }
            
            // Get the element handle
            ElementHandle elementHandle = locator.elementHandle();
            
            // Use existing extractMetadata method
            return extractMetadata(elementHandle);
            
        } catch (Exception e) {
            log.error("Error capturing element by selector: {}", selector, e);
            throw new IllegalStateException("Failed to capture element: " + e.getMessage());
        }
    }

    public boolean isBrowserOpen() {
        return browser != null && page != null;
    }

    public String getCurrentUrl() {
        return page != null ? page.url() : null;
    }

    public int countElements(String locator) {
        if (page == null) {
            return 0;
        }

        try {
            // Skip counting for Playwright API methods (they're not CSS/XPath selectors)
            if (locator.startsWith("page.")) {
                return -1; // Special value to indicate "not countable"
            }
            
            // Skip counting for dynamic XPath patterns (they contain %s placeholders)
            if (locator.contains("%s")) {
                return -2; // Special value to indicate "dynamic pattern"
            }
            
            // Try to count elements using the locator
            Locator elementLocator = page.locator(locator);
            return elementLocator.count();
        } catch (Exception e) {
            log.warn("Error counting elements for locator '{}': {}", locator, e.getMessage());
            return 0;
        }
    }

    public String generateUniqueXPath(String originalXPath) {
        if (page == null) {
            return null;
        }

        try {
            // Check if the original XPath has multiple matches
            int count = countElements(originalXPath);
            if (count <= 1) {
                return null; // Already unique or not found
            }
            
            // Execute JavaScript to find a unique parent and generate a unique XPath
            Object result = page.evaluate("""
                (xpath) => {
                    // Get all matching elements
                    const xpathResult = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                    if (xpathResult.snapshotLength === 0) return null;
                    
                    const targetElement = xpathResult.snapshotItem(0);
                    
                    // Function to find a unique parent
                    function findUniqueParent(el) {
                        let current = el.parentElement;
                        while (current && current !== document.body) {
                            // Check for unique ID
                            if (current.id) {
                                return {
                                    type: 'id',
                                    value: current.id,
                                    tag: current.tagName.toLowerCase()
                                };
                            }
                            
                            // Check for unique data attributes
                            const dataAttrs = Array.from(current.attributes)
                                .filter(attr => attr.name.startsWith('data-') && attr.value);
                            if (dataAttrs.length > 0) {
                                for (const attr of dataAttrs) {
                                    const selector = `//${current.tagName.toLowerCase()}[@${attr.name}='${attr.value}']`;
                                    const result = document.evaluate(selector, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                                    if (result.snapshotLength === 1) {
                                        return {
                                            type: 'attribute',
                                            name: attr.name,
                                            value: attr.value,
                                            tag: current.tagName.toLowerCase()
                                        };
                                    }
                                }
                            }
                            
                            // Check for unique class combination
                            if (current.classList.length > 0) {
                                const classes = Array.from(current.classList);
                                for (let i = 1; i <= Math.min(classes.length, 2); i++) {
                                    const classCombinations = classes.slice(0, i);
                                    const classCondition = classCombinations.map(c => `contains(@class, '${c}')`).join(' and ');
                                    const selector = `//${current.tagName.toLowerCase()}[${classCondition}]`;
                                    const result = document.evaluate(selector, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                                    if (result.snapshotLength === 1) {
                                        return {
                                            type: 'class',
                                            value: classCombinations.join(' '),
                                            tag: current.tagName.toLowerCase()
                                        };
                                    }
                                }
                            }
                            
                            current = current.parentElement;
                        }
                        return null;
                    }
                    
                    // Find the path from target element to the unique parent
                    function getRelativePath(el, parent) {
                        const parts = [];
                        let current = el;
                        
                        while (current && current !== parent && current !== document.body) {
                            let tag = current.tagName.toLowerCase();
                            
                            // Get position among siblings of same tag
                            const siblings = Array.from(current.parentElement.children)
                                .filter(sibling => sibling.tagName === current.tagName);
                            if (siblings.length > 1) {
                                const position = siblings.indexOf(current) + 1;
                                tag += `[${position}]`;
                            }
                            
                            parts.unshift(tag);
                            current = current.parentElement;
                        }
                        
                        return parts.join('/');
                    }
                    
                    const uniqueParentInfo = findUniqueParent(targetElement);
                    if (!uniqueParentInfo) return null;
                    
                    // Build unique XPath
                    let uniqueXPath = '';
                    if (uniqueParentInfo.type === 'id') {
                        uniqueXPath = `//${uniqueParentInfo.tag}[@id='${uniqueParentInfo.value}']`;
                    } else if (uniqueParentInfo.type === 'attribute') {
                        uniqueXPath = `//${uniqueParentInfo.tag}[@${uniqueParentInfo.name}='${uniqueParentInfo.value}']`;
                    } else if (uniqueParentInfo.type === 'class') {
                        const classes = uniqueParentInfo.value.split(' ');
                        const classCondition = classes.map(c => `contains(@class, '${c}')`).join(' and ');
                        uniqueXPath = `//${uniqueParentInfo.tag}[${classCondition}]`;
                    }
                    
                    // Get the unique parent element
                    const parentResult = document.evaluate(uniqueXPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                    const uniqueParent = parentResult.singleNodeValue;
                    
                    if (!uniqueParent) return null;
                    
                    // Get relative path from unique parent to target
                    const relativePath = getRelativePath(targetElement, uniqueParent);
                    
                    // Combine to create the final unique XPath
                    return uniqueXPath + (relativePath ? '/' + relativePath : '');
                }
            """, originalXPath);

            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.warn("Error generating unique XPath for '{}': {}", originalXPath, e.getMessage());
            return null;
        }
    }

    public byte[] takeScreenshot() {
        if (page != null) {
            return page.screenshot();
        }
        return new byte[0];
    }

    /**
     * Get metadata of the currently selected element (clicked in capture mode)
     */
    public ElementMetadata captureSelectedElement() {
        if (page == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            // Check if an element was selected
            Object selected = page.evaluate("() => window.__selectedElement");
            if (selected == null) {
                return null; // No element selected yet
            }

            // Get the element handle
            ElementHandle element = (ElementHandle) page.evaluateHandle("() => window.__selectedElement");
            
            if (element == null) {
                return null;
            }

            // Extract metadata
            ElementMetadata metadata = extractMetadata(element);

            // Clear the selected element
            page.evaluate("() => { window.__selectedElement = null; }");

            return metadata;
        } catch (Exception e) {
            log.error("Error capturing selected element: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if an element has been selected in the browser
     */
    public boolean hasSelectedElement() {
        if (page == null) {
            return false;
        }

        try {
            Object selected = page.evaluate("() => window.__selectedElement != null");
            return Boolean.TRUE.equals(selected);
        } catch (Exception e) {
            return false;
        }
    }
}
