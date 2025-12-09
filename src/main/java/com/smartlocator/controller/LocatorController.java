package com.smartlocator.controller;

import com.smartlocator.dto.OpenUrlRequest;
import com.smartlocator.model.ElementMetadata;
import com.smartlocator.model.LocatorCandidate;
import com.smartlocator.model.LocatorResult;
import com.smartlocator.service.BrowserService;
import com.smartlocator.service.LocatorGenerationService;
import com.smartlocator.service.LocatorScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LocatorController {

    private final BrowserService browserService;
    private final LocatorGenerationService locatorGenerationService;
    private final LocatorScoringService locatorScoringService;

    /**
     * Open a URL in the browser
     */
    @PostMapping("/open-url")
    public ResponseEntity<Map<String, Object>> openUrl(@RequestBody OpenUrlRequest request) {
        try {
            String browserType = request.getBrowserType() != null ? 
                    request.getBrowserType() : "chromium";
            
            browserService.openUrl(request.getUrl(), browserType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Browser opened successfully");
            response.put("url", request.getUrl());
            response.put("browserType", browserType);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error opening URL", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Capture element metadata by selector
     */
    @GetMapping("/capture-element")
    public ResponseEntity<?> captureElement(@RequestParam String selector) {
        try {
            if (!browserService.isBrowserOpen()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No browser is open. Please open a URL first.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            ElementMetadata metadata = browserService.captureElement(selector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("metadata", metadata);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error capturing element", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Capture element by coordinates
     */
    @GetMapping("/capture-element-by-coordinates")
    public ResponseEntity<?> captureElementByCoordinates(
            @RequestParam int x, 
            @RequestParam int y) {
        try {
            if (!browserService.isBrowserOpen()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No browser is open. Please open a URL first.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            ElementMetadata metadata = browserService.captureElementByCoordinates(x, y);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("metadata", metadata);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error capturing element by coordinates", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generate locators for element metadata
     */
    @PostMapping("/generate-locators")
    public ResponseEntity<?> generateLocators(@RequestBody ElementMetadata metadata) {
        try {
            // Generate all possible locators
            List<LocatorCandidate> candidates = locatorGenerationService.generateAllLocators(metadata);
            
            // Score and select best
            LocatorResult result = locatorScoringService.scoreAndSelectBest(candidates, metadata);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating locators", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Capture and generate locators in one call
     */
    @GetMapping("/capture-and-generate")
    public ResponseEntity<?> captureAndGenerate(@RequestParam String selector) {
        try {
            if (!browserService.isBrowserOpen()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No browser is open. Please open a URL first.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            // Capture element
            ElementMetadata metadata = browserService.captureElement(selector);
            
            // Generate locators
            List<LocatorCandidate> candidates = locatorGenerationService.generateAllLocators(metadata);
            
            // Score and select best
            LocatorResult result = locatorScoringService.scoreAndSelectBest(candidates, metadata);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in capture and generate", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get browser status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("browserOpen", browserService.isBrowserOpen());
        status.put("currentUrl", browserService.getCurrentUrl());
        return ResponseEntity.ok(status);
    }

    /**
     * Close browser
     */
    @PostMapping("/close-browser")
    public ResponseEntity<Map<String, Object>> closeBrowser() {
        try {
            browserService.closeBrowser();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Browser closed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error closing browser", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Take screenshot
     */
    @GetMapping("/screenshot")
    public ResponseEntity<?> takeScreenshot() {
        try {
            if (!browserService.isBrowserOpen()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No browser is open.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            byte[] screenshot = browserService.takeScreenshot();
            
            if (screenshot == null || screenshot.length == 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Failed to take screenshot");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "image/png")
                    .body(screenshot);
        } catch (Exception e) {
            log.error("Error taking screenshot", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Poll for selected element (hover + click capture)
     */
    @GetMapping("/poll-selected-element")
    public ResponseEntity<?> pollSelectedElement() {
        try {
            if (!browserService.isBrowserOpen()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("hasSelection", false);
                return ResponseEntity.ok(error);
            }

            if (!browserService.hasSelectedElement()) {
                Map<String, Object> response = new HashMap<>();
                response.put("hasSelection", false);
                return ResponseEntity.ok(response);
            }

            // Capture the selected element
            ElementMetadata metadata = browserService.captureSelectedElement();
            
            if (metadata == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("hasSelection", false);
                return ResponseEntity.ok(response);
            }

            // Generate locators
            List<LocatorCandidate> candidates = locatorGenerationService.generateAllLocators(metadata);
            
            // Score and select best
            LocatorResult result = locatorScoringService.scoreAndSelectBest(candidates, metadata);
            
            Map<String, Object> response = new HashMap<>();
            response.put("hasSelection", true);
            response.put("result", result);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error polling selected element", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("hasSelection", false);
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }
}
