@echo off
echo ===================================================
echo   StyleEcho AI - Environment Setup Script (Windows)
echo ===================================================
echo.

echo [1/3] Installing system boundaries (ffmpeg)...
call conda install -c conda-forge ffmpeg -y
if %errorlevel% neq 0 (
    echo [ERROR] FFmpeg installation failed!
    exit /b %errorlevel%
)
echo.

echo [2/3] Installing base dependencies...
pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo [ERROR] Base dependency installation failed!
    exit /b %errorlevel%
)
echo.

echo [3/3] Installing strict/conflict dependencies without overwriting PyTorch...
pip install -r requirements_no_deps.txt --no-deps
if %errorlevel% neq 0 (
    echo [ERROR] No-deps installation failed!
    exit /b %errorlevel%
)
echo.

echo ===================================================
echo   Setup Completed Successfully!
echo ===================================================
