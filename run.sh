#!/bin/bash

echo "=========================================="
echo "🚀 Starting Smart Locator Capture Tool"
echo "=========================================="

# Check if JAR exists
if [ ! -f "target/smart-locator.jar" ]; then
    echo "❌ Error: smart-locator.jar not found"
    echo "Please build the project first:"
    echo "  ./build.sh"
    exit 1
fi

# Run the application
echo "Starting application..."
java -jar target/smart-locator.jar
