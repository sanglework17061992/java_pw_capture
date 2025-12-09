# 🎯 Hover-to-Capture Feature

## Overview
The Smart Locator Capture Tool now includes a **real-time hover-to-capture** mode that automatically generates smart locators when you hover over and click elements in the opened browser.

## How to Use

### Step 1: Open a URL
1. Go to http://localhost:8080
2. Enter a URL (e.g., `https://the-internet.herokuapp.com/`)
3. Select browser type (Chromium/Firefox/WebKit)
4. Click **"🚀 Open URL"**

### Step 2: Automatic Capture Mode
When the browser opens, you'll see:

- **Blue highlight box** that follows your mouse
- **Element info tooltip** showing tag name, ID, and classes
- **Purple banner** in top-right corner with instructions
- Status badge showing "Open (Capture Mode Active)"

### Step 3: Capture Elements
1. **Hover** over any element you want to capture
2. **Click** on it
3. The tool will automatically:
   - Capture the element metadata
   - Generate all possible locators (CSS, XPath, ID, Playwright)
   - Score each locator for stability, specificity, readability, performance
   - Display results in the web UI

### Step 4: Review Results
The captured locators appear instantly below with:
- ✨ **Best Locator** (highest-scored, recommended)
- All alternative locators (CSS, XPath, etc.)
- **Score** (0-100) with visual badge
- **Scoring reasons** explaining why this locator was chosen
- **Element metadata** (tag, ID, classes, attributes, text)
- **📋 Copy buttons** for each locator

## Controls

### Pause/Resume Capture Mode
- **Click the purple banner** in the top-right to pause/resume capture mode
- When paused, banner turns gray and hover effects stop

### Exit Capture Mode
- **Press ESC key** to completely exit capture mode
- This removes the overlay, banner, and highlight elements

### Manual Capture (Optional)
If you prefer the old method:
1. Use DevTools to get a CSS selector or XPath
2. Paste it in the "Manual Selector" input field
3. Click **"✨ Manual Capture"**

## Technical Details

### Real-Time Polling
- Frontend polls `/api/poll-selected-element` every 500ms
- Polling starts automatically when browser opens
- Stops when browser closes
- No page refresh needed - results appear instantly

### Browser Injection
When a URL opens, the tool injects JavaScript that:
- Creates highlight overlay with box shadow
- Shows element info tooltip
- Captures clicked elements
- Stores selection in `window.__selectedElement`

### Backend Processing
1. Detects element selection
2. Extracts comprehensive metadata
3. Generates multiple locator strategies
4. Applies AI-powered scoring algorithm
5. Returns best locator with alternatives

## Advantages

✅ **No manual selector entry** - just click elements  
✅ **Real-time feedback** - instant locator generation  
✅ **Visual guidance** - see exactly what you're capturing  
✅ **Non-intrusive** - pause or exit anytime  
✅ **Smart scoring** - AI recommends the most stable locator  
✅ **Multiple strategies** - CSS, XPath, ID, Playwright, data attributes

## Scoring Algorithm

The tool uses a weighted scoring model:

- **40% Stability**: Won't break easily with code changes
- **30% Specificity**: Uniquely identifies the element
- **20% Readability**: Easy to understand and maintain
- **10% Performance**: Fast DOM query execution

Priority attributes for stable locators:
- `data-test-id`, `data-testid`, `data-test`
- `data-qa`, `data-cy`
- `aria-label`, `aria-labelledby`
- `role`, `name`, `type`, `placeholder`

## Troubleshooting

### Browser doesn't open
- Check if port 8080 is available
- Verify Java 17+ is installed
- Check terminal for error messages

### Elements aren't highlighting
- Make sure banner shows "Capture mode active"
- Press ESC and reopen the URL
- Check browser console for JavaScript errors

### No results after clicking
- Wait 500ms for polling to detect selection
- Check network tab for API errors
- Verify backend is running (check terminal logs)

### Wrong element captured
- Hover more carefully over target element
- Use browser DevTools to identify correct element
- Use Manual Capture mode as fallback

## Examples

### Capturing a Button
```
Hover over: <button id="submit">Submit</button>
Click it
Results:
✨ Best: #submit (Score: 95/100)
- Stable ID attribute
- Unique in page
- Fast CSS selector
```

### Capturing a Complex Element
```
Hover over: <input data-testid="username-input" class="form-control">
Click it
Results:
✨ Best: [data-testid="username-input"] (Score: 98/100)
- Test-specific attribute (highest priority)
- Stable across refactorings
- Recommended for automation
```

## API Endpoints

- `GET /api/status` - Check if browser is open
- `GET /api/poll-selected-element` - Poll for clicked elements
- `POST /api/open-url` - Open URL in browser
- `POST /api/close-browser` - Close browser
- `GET /api/capture-and-generate` - Manual capture by selector

## Features Coming Soon

- WebSocket for instant push notifications (no polling delay)
- Multiple element capture at once
- Visual locator preview (highlight on hover in UI)
- Export captured locators to file (Java, Python, etc.)
- Screenshot annotation
- Locator validation (test if locator still works)

---

**Enjoy the hover-to-capture feature!** 🎯✨
