package com.smartlocator.service;

import com.smartlocator.model.ElementMetadata;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class BrowserService {

    private WebDriver driver;
    private String currentBrowserType = "chromium";

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up Selenium resources...");
        closeBrowser();
    }

    public void openUrl(String url, String browserType) {
        try {
            // Close existing browser if different type requested
            if (driver != null && !currentBrowserType.equals(browserType)) {
                closeBrowser();
            }

            // Launch browser if not already running
            if (driver == null) {
                currentBrowserType = browserType;
                
                switch (browserType.toLowerCase()) {
                    case "firefox" -> {
                        WebDriverManager.firefoxdriver().setup();
                        FirefoxOptions firefoxOptions = new FirefoxOptions();
                        driver = new FirefoxDriver(firefoxOptions);
                    }
                    case "webkit", "safari" -> {
                        WebDriverManager.safaridriver().setup();
                        SafariOptions safariOptions = new SafariOptions();
                        driver = new SafariDriver(safariOptions);
                    }
                    default -> {
                        WebDriverManager.chromedriver().setup();
                        ChromeOptions chromeOptions = new ChromeOptions();
                        chromeOptions.addArguments("--start-maximized");
                        driver = new ChromeDriver(chromeOptions);
                    }
                }
                
                // Maximize window
                driver.manage().window().maximize();
                
                // Set implicit wait
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            }

            // Navigate to URL
            driver.get(url);
            log.info("Navigated to: {}", url);

            // Wait for page to load
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(webDriver -> ((JavascriptExecutor) webDriver)
                            .executeScript("return document.readyState").equals("complete"));

            // Inject element highlighter script
            injectHighlighterScript();

        } catch (Exception e) {
            log.error("Error opening URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to open URL: " + e.getMessage());
        }
    }

    public ElementMetadata captureElement(String selector) {
        if (driver == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            WebElement element = driver.findElement(By.cssSelector(selector));
            
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
        if (driver == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            // Use JavaScript to get element at coordinates
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "var element = document.elementFromPoint(arguments[0], arguments[1]);" +
                    "if (!element) return null;" +
                    "var rect = element.getBoundingClientRect();" +
                    "return {" +
                    "  selector: element.tagName.toLowerCase() + " +
                    "            (element.id ? '#' + element.id : '') + " +
                    "            (element.className ? '.' + element.className.split(' ').join('.') : '')" +
                    "};",
                    x, y
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

    private ElementMetadata extractMetadata(WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Execute JavaScript to extract all metadata
            Object metadata = js.executeScript(
                    "var element = arguments[0];" +
                    "var getXPath = function(el) {" +
                    "  if (el.id) return '//*[@id=\"' + el.id + '\"]';" +
                    "  var parts = [];" +
                    "  while (el && el.nodeType === Node.ELEMENT_NODE) {" +
                    "    var count = 0;" +
                    "    var index = 0;" +
                    "    for (var sibling = el.parentNode?.firstChild; sibling; sibling = sibling.nextSibling) {" +
                    "      if (sibling.nodeType === Node.ELEMENT_NODE && sibling.tagName === el.tagName) {" +
                    "        count++;" +
                    "        if (sibling === el) index = count;" +
                    "      }" +
                    "    }" +
                    "    var tagName = el.tagName.toLowerCase();" +
                    "    var part = count > 1 ? tagName + '[' + index + ']' : tagName;" +
                    "    parts.unshift(part);" +
                    "    el = el.parentNode;" +
                    "  }" +
                    "  return '//' + parts.join('/');" +
                    "};" +
                    "" +
                    "var getCssPath = function(el) {" +
                    "  if (el.id) return '#' + el.id;" +
                    "  var parts = [];" +
                    "  while (el && el.nodeType === Node.ELEMENT_NODE) {" +
                    "    var selector = el.tagName.toLowerCase();" +
                    "    if (el.id) {" +
                    "      selector = '#' + el.id;" +
                    "      parts.unshift(selector);" +
                    "      break;" +
                    "    } else if (el.className) {" +
                    "      selector += '.' + el.className.trim().split(/\\s+/).join('.');" +
                    "    }" +
                    "    parts.unshift(selector);" +
                    "    el = el.parentNode;" +
                    "  }" +
                    "  return parts.join(' > ');" +
                    "};" +
                    "" +
                    "var getNthIndex = function(el) {" +
                    "  var index = 1;" +
                    "  var sibling = el.previousElementSibling;" +
                    "  while (sibling) {" +
                    "    if (sibling.tagName === el.tagName) index++;" +
                    "    sibling = sibling.previousElementSibling;" +
                    "  }" +
                    "  return index;" +
                    "};" +
                    "" +
                    "var attrs = {};" +
                    "for (var i = 0; i < element.attributes.length; i++) {" +
                    "  var attr = element.attributes[i];" +
                    "  attrs[attr.name] = attr.value;" +
                    "}" +
                    "" +
                    "var getDomPath = function(el) {" +
                    "  var path = [];" +
                    "  var current = el;" +
                    "  while (current && current.tagName) {" +
                    "    var nodeInfo = {" +
                    "      tag: current.tagName.toLowerCase()," +
                    "      id: current.id || null," +
                    "      classes: Array.from(current.classList)," +
                    "      text: (current.childNodes.length === 1 && current.childNodes[0].nodeType === 3) ? current.textContent.trim() : null," +
                    "      isCurrent: (current === el)" +
                    "    };" +
                    "    path.unshift(nodeInfo);" +
                    "    if (current.tagName.toLowerCase() === 'body') break;" +
                    "    current = current.parentElement;" +
                    "  }" +
                    "  return path;" +
                    "};" +
                    "" +
                    "return {" +
                    "  tagName: element.tagName.toLowerCase()," +
                    "  id: element.id || null," +
                    "  classList: Array.from(element.classList)," +
                    "  attributes: attrs," +
                    "  innerText: element.innerText || ''," +
                    "  normalizedText: element.textContent?.trim().replace(/\\s+/g, ' ') || ''," +
                    "  parentTagName: element.parentElement?.tagName.toLowerCase() || null," +
                    "  nthIndex: getNthIndex(element)," +
                    "  cssPath: getCssPath(element)," +
                    "  xpathPath: getXPath(element)," +
                    "  outerHTML: element.outerHTML," +
                    "  domPath: getDomPath(element)" +
                    "};",
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
            List<WebElement> elements = driver.findElements(By.cssSelector(cssSelector));
            return elements.size() == 1;
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
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String script = 
            "if (window.__smartLocatorInjected) return;" +
            "window.__smartLocatorInjected = true;" +
            "window.__hoveredElement = null;" +
            "" +
            "var highlightBox = document.createElement('div');" +
            "highlightBox.id = '__smart-locator-highlight';" +
            "highlightBox.style.cssText = " +
            "  'position: absolute;' +" +
            "  'border: 2px solid #667eea;' +" +
            "  'background: rgba(102, 126, 234, 0.1);' +" +
            "  'pointer-events: none;' +" +
            "  'z-index: 999999;' +" +
            "  'display: none;' +" +
            "  'box-shadow: 0 0 10px rgba(102, 126, 234, 0.5);';" +
            "document.body.appendChild(highlightBox);" +
            "" +
            "var tooltip = document.createElement('div');" +
            "tooltip.id = '__smart-locator-tooltip';" +
            "tooltip.style.cssText = " +
            "  'position: absolute;' +" +
            "  'background: rgba(0, 0, 0, 0.9);' +" +
            "  'color: white;' +" +
            "  'padding: 8px 12px;' +" +
            "  'border-radius: 4px;' +" +
            "  'font-size: 12px;' +" +
            "  'font-family: monospace;' +" +
            "  'pointer-events: none;' +" +
            "  'z-index: 1000000;' +" +
            "  'display: none;' +" +
            "  'max-width: 300px;' +" +
            "  'word-wrap: break-word;';" +
            "document.body.appendChild(tooltip);" +
            "" +
            "var banner = document.createElement('div');" +
            "banner.id = '__smart-locator-banner';" +
            "banner.style.cssText = " +
            "  'position: fixed;' +" +
            "  'top: 10px;' +" +
            "  'right: 10px;' +" +
            "  'background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);' +" +
            "  'color: white;' +" +
            "  'padding: 12px 20px;' +" +
            "  'border-radius: 8px;' +" +
            "  'font-size: 14px;' +" +
            "  'font-family: Arial, sans-serif;' +" +
            "  'z-index: 1000001;' +" +
            "  'box-shadow: 0 4px 12px rgba(0,0,0,0.3);' +" +
            "  'cursor: pointer;';" +
            "banner.innerHTML = 'Hover + Ctrl+Click to capture element<br><small style=\"opacity:0.8\">Press ESC to exit capture mode</small>';" +
            "document.body.appendChild(banner);" +
            "" +
            "var captureMode = true;" +
            "" +
            "banner.addEventListener('click', function() {" +
            "  captureMode = !captureMode;" +
            "  banner.innerHTML = captureMode ? " +
            "    'Hover + Ctrl+Click to capture element<br><small style=\"opacity:0.8\">Press ESC to exit capture mode</small>' : " +
            "    'Capture mode paused<br><small style=\"opacity:0.8\">Click to resume</small>';" +
            "  banner.style.background = captureMode ? " +
            "    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' : " +
            "    'linear-gradient(135deg, #666 0%, #888 100%)';" +
            "  if (!captureMode) {" +
            "    highlightBox.style.display = 'none';" +
            "    tooltip.style.display = 'none';" +
            "  }" +
            "});" +
            "" +
            "document.addEventListener('keydown', function(e) {" +
            "  if (e.key === 'Escape') {" +
            "    banner.remove();" +
            "    highlightBox.remove();" +
            "    tooltip.remove();" +
            "    captureMode = false;" +
            "  }" +
            "});" +
            "" +
            "document.addEventListener('mouseover', function(e) {" +
            "  if (!captureMode) return;" +
            "  e.stopPropagation();" +
            "  window.__hoveredElement = e.target;" +
            "  " +
            "  var rect = e.target.getBoundingClientRect();" +
            "  highlightBox.style.top = (rect.top + window.scrollY) + 'px';" +
            "  highlightBox.style.left = (rect.left + window.scrollX) + 'px';" +
            "  highlightBox.style.width = rect.width + 'px';" +
            "  highlightBox.style.height = rect.height + 'px';" +
            "  highlightBox.style.display = 'block';" +
            "  " +
            "  var tagName = e.target.tagName.toLowerCase();" +
            "  var idInfo = e.target.id ? '#' + e.target.id : '';" +
            "  var classInfo = e.target.className ? '.' + e.target.className.split(' ').join('.') : '';" +
            "  tooltip.textContent = tagName + idInfo + classInfo;" +
            "  tooltip.style.top = (rect.bottom + window.scrollY + 5) + 'px';" +
            "  tooltip.style.left = (rect.left + window.scrollX) + 'px';" +
            "  tooltip.style.display = 'block';" +
            "});" +
            "" +
            "document.addEventListener('mouseout', function(e) {" +
            "  if (!captureMode) return;" +
            "});" +
            "" +
            "document.addEventListener('click', function(e) {" +
            "  if (!captureMode || !window.__hoveredElement) return;" +
            "  if (!e.ctrlKey && !e.metaKey) return;" +
            "  e.preventDefault();" +
            "  e.stopPropagation();" +
            "  window.__selectedElement = window.__hoveredElement;" +
            "}, true);";
        
        js.executeScript(script);
    }

    public void closeBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    public void highlightElement(String selector) {
        if (driver == null) {
            throw new IllegalStateException("Browser is not open");
        }
        
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = 
                "var selector = arguments[0];" +
                "document.querySelectorAll('.copilot-highlight-temp').forEach(function(el) {" +
                "  el.classList.remove('copilot-highlight-temp');" +
                "  el.style.outline = '';" +
                "  el.style.outlineOffset = '';" +
                "});" +
                "" +
                "var element = document.querySelector(selector);" +
                "if (element) {" +
                "  element.classList.add('copilot-highlight-temp');" +
                "  element.style.outline = '3px solid #ff6b6b';" +
                "  element.style.outlineOffset = '2px';" +
                "  element.scrollIntoView({ behavior: 'smooth', block: 'center' });" +
                "  " +
                "  setTimeout(function() {" +
                "    element.classList.remove('copilot-highlight-temp');" +
                "    element.style.outline = '';" +
                "    element.style.outlineOffset = '';" +
                "  }, 3000);" +
                "}";
            js.executeScript(script, selector);
        } catch (Exception e) {
            log.error("Error highlighting element with selector: {}", selector, e);
            throw new RuntimeException("Failed to highlight element: " + e.getMessage());
        }
    }

    public ElementMetadata captureElementBySelector(String selector) {
        if (driver == null) {
            throw new IllegalStateException("Browser is not open");
        }
        
        try {
            // Try CSS selector first
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            
            // If CSS fails, try XPath
            if (elements.isEmpty()) {
                elements = driver.findElements(By.xpath(selector));
            }
            
            if (elements.isEmpty()) {
                throw new IllegalStateException("Element not found with selector: " + selector);
            }
            
            // Use the first matching element
            WebElement element = elements.get(0);
            
            // Use existing extractMetadata method
            return extractMetadata(element);
            
        } catch (Exception e) {
            log.error("Error capturing element by selector: {}", selector, e);
            throw new IllegalStateException("Failed to capture element: " + e.getMessage());
        }
    }

    public boolean isBrowserOpen() {
        return driver != null;
    }

    public String getCurrentUrl() {
        return driver != null ? driver.getCurrentUrl() : null;
    }

    public int countElements(String locator) {
        if (driver == null) {
            return 0;
        }

        try {
            // Skip counting for Playwright API methods
            if (locator.startsWith("page.")) {
                return -1;
            }
            
            // Skip counting for dynamic XPath patterns
            if (locator.contains("%s")) {
                return -2;
            }
            
            // Try CSS selector first
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(locator));
                return elements.size();
            } catch (Exception e) {
                // If CSS fails, try XPath
                try {
                    List<WebElement> elements = driver.findElements(By.xpath(locator));
                    return elements.size();
                } catch (Exception ex) {
                    log.warn("Error counting elements for locator '{}': {}", locator, ex.getMessage());
                    return 0;
                }
            }
        } catch (Exception e) {
            log.warn("Error counting elements for locator '{}': {}", locator, e.getMessage());
            return 0;
        }
    }

    public String generateUniqueXPath(String originalXPath) {
        if (driver == null) {
            return null;
        }

        try {
            // Check if the original XPath has multiple matches
            int count = countElements(originalXPath);
            if (count <= 1) {
                return null;
            }
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                "var xpath = arguments[0];" +
                "var xpathResult = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);" +
                "if (xpathResult.snapshotLength === 0) return null;" +
                "" +
                "var targetElement = xpathResult.snapshotItem(0);" +
                "" +
                "function findUniqueParent(el) {" +
                "  var current = el.parentElement;" +
                "  while (current && current !== document.body) {" +
                "    if (current.id) {" +
                "      return {" +
                "        type: 'id'," +
                "        value: current.id," +
                "        tag: current.tagName.toLowerCase()" +
                "      };" +
                "    }" +
                "    " +
                "    var dataAttrs = Array.from(current.attributes)" +
                "      .filter(function(attr) { return attr.name.startsWith('data-') && attr.value; });" +
                "    if (dataAttrs.length > 0) {" +
                "      for (var i = 0; i < dataAttrs.length; i++) {" +
                "        var attr = dataAttrs[i];" +
                "        var selector = '//' + current.tagName.toLowerCase() + '[@' + attr.name + '=\"' + attr.value + '\"]';" +
                "        var result = document.evaluate(selector, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);" +
                "        if (result.snapshotLength === 1) {" +
                "          return {" +
                "            type: 'attribute'," +
                "            name: attr.name," +
                "            value: attr.value," +
                "            tag: current.tagName.toLowerCase()" +
                "          };" +
                "        }" +
                "      }" +
                "    }" +
                "    " +
                "    if (current.classList.length > 0) {" +
                "      var classes = Array.from(current.classList);" +
                "      for (var i = 1; i <= Math.min(classes.length, 2); i++) {" +
                "        var classCombinations = classes.slice(0, i);" +
                "        var classCondition = classCombinations.map(function(c) { return \"contains(@class, '\" + c + \"')\"; }).join(' and ');" +
                "        var selector = '//' + current.tagName.toLowerCase() + '[' + classCondition + ']';" +
                "        var result = document.evaluate(selector, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);" +
                "        if (result.snapshotLength === 1) {" +
                "          return {" +
                "            type: 'class'," +
                "            value: classCombinations.join(' ')," +
                "            tag: current.tagName.toLowerCase()" +
                "          };" +
                "        }" +
                "      }" +
                "    }" +
                "    " +
                "    current = current.parentElement;" +
                "  }" +
                "  return null;" +
                "}" +
                "" +
                "function getRelativePath(el, parent) {" +
                "  var parts = [];" +
                "  var current = el;" +
                "  " +
                "  while (current && current !== parent && current !== document.body) {" +
                "    var tag = current.tagName.toLowerCase();" +
                "    " +
                "    var siblings = Array.from(current.parentElement.children)" +
                "      .filter(function(sibling) { return sibling.tagName === current.tagName; });" +
                "    if (siblings.length > 1) {" +
                "      var position = siblings.indexOf(current) + 1;" +
                "      tag += '[' + position + ']';" +
                "    }" +
                "    " +
                "    parts.unshift(tag);" +
                "    current = current.parentElement;" +
                "  }" +
                "  " +
                "  return parts.join('/');" +
                "}" +
                "" +
                "var uniqueParentInfo = findUniqueParent(targetElement);" +
                "if (!uniqueParentInfo) return null;" +
                "" +
                "var uniqueXPath = '';" +
                "if (uniqueParentInfo.type === 'id') {" +
                "  uniqueXPath = '//' + uniqueParentInfo.tag + '[@id=\"' + uniqueParentInfo.value + '\"]';" +
                "} else if (uniqueParentInfo.type === 'attribute') {" +
                "  uniqueXPath = '//' + uniqueParentInfo.tag + '[@' + uniqueParentInfo.name + '=\"' + uniqueParentInfo.value + '\"]';" +
                "} else if (uniqueParentInfo.type === 'class') {" +
                "  var classes = uniqueParentInfo.value.split(' ');" +
                "  var classCondition = classes.map(function(c) { return \"contains(@class, '\" + c + \"')\"; }).join(' and ');" +
                "  uniqueXPath = '//' + uniqueParentInfo.tag + '[' + classCondition + ']';" +
                "}" +
                "" +
                "var parentResult = document.evaluate(uniqueXPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);" +
                "var uniqueParent = parentResult.singleNodeValue;" +
                "" +
                "if (!uniqueParent) return null;" +
                "" +
                "var relativePath = getRelativePath(targetElement, uniqueParent);" +
                "" +
                "return uniqueXPath + (relativePath ? '/' + relativePath : '');",
                originalXPath
            );

            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.warn("Error generating unique XPath for '{}': {}", originalXPath, e.getMessage());
            return null;
        }
    }

    public byte[] takeScreenshot() {
        if (driver != null) {
            try {
                TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
                return screenshotDriver.getScreenshotAs(OutputType.BYTES);
            } catch (Exception e) {
                log.error("Error taking screenshot: {}", e.getMessage(), e);
                return new byte[0];
            }
        }
        return new byte[0];
    }

    public ElementMetadata captureSelectedElement() {
        if (driver == null) {
            throw new IllegalStateException("No page is open. Please open a URL first.");
        }

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Check if an element was selected
            Object selected = js.executeScript("return window.__selectedElement");
            if (selected == null) {
                return null;
            }

            // Get the element
            WebElement element = (WebElement) js.executeScript("return window.__selectedElement");
            
            if (element == null) {
                return null;
            }

            // Extract metadata
            ElementMetadata metadata = extractMetadata(element);

            // Clear the selected element
            js.executeScript("window.__selectedElement = null;");

            return metadata;
        } catch (Exception e) {
            log.error("Error capturing selected element: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean hasSelectedElement() {
        if (driver == null) {
            return false;
        }

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object selected = js.executeScript("return window.__selectedElement != null");
            return Boolean.TRUE.equals(selected);
        } catch (Exception e) {
            return false;
        }
    }
}
