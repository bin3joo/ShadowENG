#!/bin/bash
set -e

echo "==================================================="
echo "  StyleEcho AI - Environment Setup Script (Mac/Linux)"
echo "  Windows 사용자는 setup.bat 을 사용하세요."
echo "  Recommended Python version: 3.10 ~ 3.13"
echo "==================================================="
echo ""

# ── Python 버전 확인 (3.10 ~ 3.13) ──
PYTHON_VER=$(python3 -c 'import sys; print(f"{sys.version_info.minor}")' 2>/dev/null || python -c 'import sys; print(f"{sys.version_info.minor}")' 2>/dev/null)
if [ -z "$PYTHON_VER" ]; then
    echo "[ERROR] Python 3 를 찾을 수 없습니다."
    exit 1
fi
if [ "$PYTHON_VER" -lt 10 ] || [ "$PYTHON_VER" -gt 13 ]; then
    echo "[ERROR] Python 3.10 ~ 3.13 이 필요합니다. (현재: 3.$PYTHON_VER)"
    exit 1
fi
echo "[OK] Python 3.$PYTHON_VER 감지"

echo ""
echo "[1/4] Installing system dependencies (ffmpeg)..."
if command -v conda &> /dev/null; then
    conda install -c conda-forge ffmpeg -y
elif command -v apt-get &> /dev/null; then
    sudo apt-get install -y ffmpeg
elif command -v brew &> /dev/null; then
    brew install ffmpeg
else
    echo "[WARN] 패키지 매니저를 찾을 수 없습니다. ffmpeg 를 수동 설치하세요."
fi

echo ""
echo "[2/4] Installing base dependencies..."
pip install -r requirements.txt

echo ""
echo "[3/4] Installing strict/conflict dependencies without overwriting PyTorch..."
pip install -r requirements_no_deps.txt --no-deps

echo ""
echo "[4/4] Downloading NLTK data..."
python -c "import nltk; nltk.download('punkt_tab', quiet=True)" 2>/dev/null \
    || python3 -c "import nltk; nltk.download('punkt_tab', quiet=True)"

echo ""
echo "==================================================="
echo "  Setup Completed Successfully!"
echo "==================================================="