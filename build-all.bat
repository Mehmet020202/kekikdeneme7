@echo off
echo ========================================
echo cs-Kekik Build Script
echo ========================================
echo.

echo Building all modules...
call gradlew clean build

echo.
echo ========================================
echo Build completed!
echo Check the build/outputs/aar/ folders for .aar files
echo ========================================
pause
