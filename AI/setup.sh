#!/bin/bash
set -e

echo "==================================================="
echo "  StyleEcho AI - Environment Setup Script (Mac/Linux)"
echo "==================================================="
echo ""

echo "[1/3] Installing system boundaries (ffmpeg)..."
conda install -c conda-forge ffmpeg -y

echo ""
echo "[2/3] Installing base dependencies..."
pip install -r requirements.txt

echo ""
echo "[3/3] Installing strict/conflict dependencies without overwriting PyTorch..."
pip install -r requirements_no_deps.txt --no-deps

echo ""
echo "==================================================="
echo "  Setup Completed Successfully!"
echo "==================================================="
