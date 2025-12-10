# Smart Locator Capture Tool

A powerful tool for generating high-quality web element locators using Spring Boot and Playwright-Java.

## Features

- ✅ **Multi-Browser Support**: Chromium, Firefox, WebKit
- ✅ **Smart Locator Generation**: XPath, CSS, ID, Playwright locators
- ✅ **AI-Powered Scoring**: Rule-based engine with weighted scoring
- ✅ **Capture History**: Track all generated locators
- ✅ **Web UI**: User-friendly interface
- ✅ **Portable**: Single JAR file, no Node.js required

## Requirements

- Java 17 or higher
- No other dependencies required!

## Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd java_pw_capture

# Build with Maven
./mvnw clean package

# Or use Maven if installed
mvn clean package
```

## Running the Application

### Option 1: Run with Maven (Recommended for Development)

```bash
mvn spring-boot:run
```

### Option 2: Share with Teammates

**Recommended: Share the entire project**

Send your teammates the whole project folder and they can run:

```bash
cd java_pw_capture
mvn spring-boot:run
```

This is the most reliable method and requires only:
- Java 17+
- Maven installed

**Alternative: Run from JAR (Has Known Issues)**

If you need to share just the JAR file:

1. Build the JAR: `mvn clean package -DskipTests`
2. Share `target/smart-locator.jar` with teammates
3. They need to install Playwright browsers first:
   ```bash
   # They need the project folder for this command
   cd java_pw_capture
   mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
   ```
4. Run: `java -jar smart-locator.jar`

⚠️ **Warning**: Running from JAR has Playwright driver extraction issues. The application starts but may fail when opening browsers. Use Option 1 (Maven) for best results.

The application will start on `http://localhost:8080`

## Usage

### 1. Open a Browser

1. Navigate to `http://localhost:8080`
2. Enter a URL (e.g., `https://example.com`)
3. Select browser type (Chromium/Firefox/WebKit)
4. Click "Open URL"

### 2. Capture Elements

1. Use browser DevTools to inspect elements
2. Copy the CSS selector or XPath
3. Paste into the "Element Selector" field
4. Click "Capture & Generate Locators"

### 3. View Results

- **Best Locator**: Highest-scored locator
- **All Candidates**: CSS, XPath, ID, Playwright locators
- **Score**: 0-100 based on stability, specificity, readability, performance
- **Reasons**: Explanation of the scoring

### 4. History

- View all captured locators
- Filter and search history
- Copy locators to clipboard
- Delete entries

## API Endpoints

### Browser Control
- `POST /api/open-url` - Open a URL in browser
- `POST /api/close-browser` - Close browser
- `GET /api/status` - Get browser status

### Element Capture
- `GET /api/capture-element?selector={selector}` - Capture element metadata
- `POST /api/generate-locators` - Generate locators from metadata
- `GET /api/capture-and-generate?selector={selector}` - Capture and generate in one call

### History
- `GET /api/history` - Get all history
- `GET /api/history/{id}` - Get specific entry
- `DELETE /api/history/{id}` - Delete entry
- `DELETE /api/history` - Clear all history

## Scoring Engine

The tool uses a weighted scoring system:

| Category | Weight | Description |
|----------|--------|-------------|
| Stability | 40% | How stable the locator is (won't change easily) |
| Specificity | 30% | How uniquely it identifies the element |
| Readability | 20% | How easy to read and understand |
| Performance | 10% | How fast in DOM queries |

### Scoring Rules

- Unique ID: 95-100
- `data-test` attribute: 90+
- `aria-*` / `role`: 85+
- Short CSS: 80+
- Smart XPath: 75+
- Text-based: 60+
- Index-based: <40
- Absolute XPath: <15

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# Database location
spring.datasource.url=jdbc:h2:file:./data/smartlocator

# Logging level
logging.level.com.smartlocator=INFO
```

## Architecture

```
com.smartlocator/
├── model/           # Data models
├── dto/             # Request/Response objects
├── entity/          # JPA entities
├── repository/      # Data access layer
├── service/         # Business logic
│   ├── BrowserService
│   ├── MetadataExtractionService
│   ├── LocatorGenerationService
│   ├── LocatorScoringService
│   └── HistoryService
├── controller/      # REST controllers
└── SmartLocatorApplication.java
```

## Technologies

- **Spring Boot 3.2.0** - Application framework
- **Playwright-Java 1.40.0** - Browser automation
- **H2 Database** - History storage
- **Thymeleaf** - Web UI templating
- **Lombok** - Code generation
- **Jackson** - JSON processing

## License

MIT License

## Author

Smart Locator Capture Tool Team
