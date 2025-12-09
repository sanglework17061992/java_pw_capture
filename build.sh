#!/bin/bash

echo "=========================================="
echo "🎯 Smart Locator Capture Tool - Build Script"
echo "=========================================="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Java 17 or higher is required"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi

echo "✅ Java version check passed"

# Check if Maven wrapper exists
if [ ! -f "./mvnw" ]; then
    echo "📦 Maven wrapper not found, using system Maven"
    MVN_CMD="mvn"
else
    echo "📦 Using Maven wrapper"
    MVN_CMD="./mvnw"
    chmod +x ./mvnw
fi

# Clean and package
echo "🔨 Building project..."
$MVN_CMD clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "=========================================="
    echo "✅ Build successful!"
    echo "📦 JAR file: target/smart-locator.jar"
    echo "=========================================="
    echo ""
    echo "To run the application:"
    echo "  java -jar target/smart-locator.jar"
    echo ""
    echo "Then open your browser to:"
    echo "  http://localhost:8080"
    echo "=========================================="
else
    echo "=========================================="
    echo "❌ Build failed!"
    echo "=========================================="
    exit 1
fi
