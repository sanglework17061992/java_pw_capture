# Quick Start Guide - Smart Locator Capture Tool

## Prerequisites
- ✅ Java 17 or higher installed
- ✅ Internet connection (for first run to download Playwright browsers)

## Quick Start

### Step 1: Build the Project

**Linux/Mac:**
```bash
./build.sh
```

**Windows:**
```cmd
build.bat
```

This will:
- Clean previous builds
- Compile the Java code
- Download dependencies
- Create `target/smart-locator.jar`

Build time: ~2-5 minutes (first time)

### Step 2: Run the Application

**Linux/Mac:**
```bash
./run.sh
```

**Windows:**
```cmd
run.bat
```

**Or directly:**
```bash
java -jar target/smart-locator.jar
```

The application will start on: **http://localhost:8080**

### Step 3: Use the Tool

1. **Open Browser**
   - Go to http://localhost:8080
   - Enter a URL (e.g., https://www.google.com)
   - Select browser type (Chromium recommended)
   - Click "🚀 Open URL"

2. **Capture Elements**
   - In the opened browser, right-click on any element
   - Select "Inspect" to open DevTools
   - Right-click on the element in DevTools
   - Copy → Copy selector (or Copy XPath)
   - Paste into "Element Selector" field in the tool
   - Click "✨ Capture & Generate Locators"

3. **View Results**
   - See all generated locators (CSS, XPath, ID, Playwright)
   - View the score (0-100)
   - Read scoring reasons
   - Copy best locator to clipboard

4. **History**
   - Click "History" tab to see all captured locators
   - Search, filter, and manage history
   - Copy previous locators

## Example Usage

### Example 1: Google Search Box
```
URL: https://www.google.com
Selector: input[name="q"]

Results:
✅ Best Locator: input[name='q']
📊 Score: 85.5/100
💡 Reasons:
   - Stable attribute detected (name)
   - Short and readable
   - Fast DOM query
```

### Example 2: Button with Data-Test
```
URL: https://example.com
Selector: button[data-test="login-btn"]

Results:
✅ Best Locator: //button[@data-test='login-btn']
📊 Score: 92.0/100
💡 Reasons:
   - Stable test attribute (data-test)
   - Relative XPath
   - Unique identifier
```

## API Usage (for Automation)

You can also use the REST API directly:

### Open URL
```bash
curl -X POST http://localhost:8080/api/open-url \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","browserType":"chromium"}'
```

### Capture Element
```bash
curl "http://localhost:8080/api/capture-and-generate?selector=input%5Bname%3D%27q%27%5D"
```

### Get History
```bash
curl http://localhost:8080/api/history
```

## Troubleshooting

### Issue: "Java not found"
**Solution:** Install Java 17 or higher
- Download from: https://adoptium.net/
- Or use: `sudo apt install openjdk-17-jdk` (Linux)

### Issue: "Build failed"
**Solution:** Check Maven installation
```bash
./mvnw clean install
```

### Issue: "Playwright browsers not found"
**Solution:** Playwright downloads browsers automatically on first run. Wait for download to complete.

### Issue: "Port 8080 already in use"
**Solution:** Change port in `src/main/resources/application.properties`:
```properties
server.port=8081
```

### Issue: "Browser doesn't open"
**Solution:** Make sure you have a display server (not headless environment)

## Advanced Configuration

### Custom Port
Edit `application.properties`:
```properties
server.port=9090
```

### Headless Mode
In `BrowserService.java`, change:
```java
.setHeadless(true)
```

### Database Location
Edit `application.properties`:
```properties
spring.datasource.url=jdbc:h2:file:/custom/path/smartlocator
```

## Project Structure

```
java_pw_capture/
├── src/main/java/com/smartlocator/
│   ├── controller/          # REST endpoints
│   ├── service/             # Business logic
│   ├── model/               # Data models
│   ├── entity/              # Database entities
│   ├── repository/          # Data access
│   └── SmartLocatorApplication.java
├── src/main/resources/
│   ├── templates/           # HTML files
│   └── application.properties
├── build.sh / build.bat     # Build scripts
├── run.sh / run.bat         # Run scripts
└── pom.xml                  # Maven configuration
```

## Support

For issues or questions:
1. Check the logs in the console
2. Review `README.md`
3. Check H2 console: http://localhost:8080/h2-console

## Next Steps

1. ✅ Build and run the application
2. ✅ Try capturing some elements
3. ✅ Explore the history feature
4. ✅ Experiment with different websites
5. ✅ Integrate with your automation framework

Happy locator capturing! 🎯
