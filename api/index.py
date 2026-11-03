import sys
from pathlib import Path

# Add parent directory to path so we can import main
sys.path.insert(0, str(Path(__file__).parent.parent))

from main import app  # noqa: E402,F401  (re-exported for the Vercel Python runtime)

# Vercel expects a variable named 'app' or 'application'
# This file serves as the entry point for Vercel's Python runtime
