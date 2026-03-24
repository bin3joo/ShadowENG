#!/bin/bash
set -e

echo "==================================================="
echo "  StyleEcho AI - Environment Setup Script (Mac/Linux)"
echo "==================================================="
echo ""

echo "[1/4] Installing system boundaries (ffmpeg)..."
conda install -c conda-forge ffmpeg -y

echo ""
echo "[2/4] Installing base dependencies..."
pip install -r requirements.txt

echo ""
echo "[3/4] Installing strict/conflict dependencies without overwriting PyTorch..."
pip install -r requirements_no_deps.txt --no-deps
echo ""
echo "[4/4] Installing onnxruntime-gpu for cuda 12..."
pip install onnxruntime-gpu --extra-index-url https://aiinfra.pkgs.visualstudio.com/PublicPackages/_packaging/onnxruntime-cuda-12/pypi/simple/

echo ""
echo "==================================================="
echo "  Setup Completed Successfully!"
echo "==================================================="
