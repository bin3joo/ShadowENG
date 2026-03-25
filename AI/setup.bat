@echo off
chcp 65001 >nul 2>&1
echo ===================================================
echo   StyleEcho AI - Environment Setup Script (Windows)
echo   Recommended Python version: 3.10 ~ 3.13
echo ===================================================
echo.

REM ── Python version check (3.10 ~ 3.13) ──
python -c "import sys; v=sys.version_info; exit(0 if 10<=v.minor<=13 else 1)" 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Python 3.10 ~ 3.13 required.
    python --version 2>nul
    exit /b 1
)
for /f %%i in ('python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"') do set PYVER=%%i
echo [OK] Python %PYVER% detected
echo.

echo [1/4] Installing system dependencies (ffmpeg)...
where conda >nul 2>&1
if %errorlevel% equ 0 (
    call conda install -c conda-forge ffmpeg -y
) else (
    where ffmpeg >nul 2>&1
    if %errorlevel% equ 0 (
        echo [OK] ffmpeg already available on PATH.
    ) else (
        echo [WARN] conda not found and ffmpeg not on PATH.
        echo        Please install ffmpeg manually: https://ffmpeg.org/download.html
    )
)
echo.

echo [2/4] Installing base dependencies...
pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo [ERROR] Base dependency installation failed!
    exit /b %errorlevel%
)
echo.

echo [3/4] Installing strict/conflict dependencies without overwriting PyTorch...
pip install -r requirements_no_deps.txt --no-deps
if %errorlevel% neq 0 (
    echo [ERROR] No-deps installation failed!
    exit /b %errorlevel%
)
echo.

echo [4/4] Downloading NLTK data...
python -c "import nltk; nltk.download('punkt_tab', quiet=True)"
echo.

echo ===================================================
echo   Setup Completed Successfully!
echo ===================================================
