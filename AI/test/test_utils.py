"""Test helper utilities for pipe.test."""

from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from typing import Callable

TEST_DIR = Path(__file__).resolve().parent
RESULT_DIR = TEST_DIR / "result"
RESULT_DIR.mkdir(parents=True, exist_ok=True)


def run_and_save_output(output_name: str, runner: Callable[[], None]) -> Path:
    """Run a test function, echo its output, and save it under result/."""
    buffer = StringIO()
    with redirect_stdout(buffer):
        runner()
    content = buffer.getvalue()
    print(content, end="")
    output_path = RESULT_DIR / output_name
    output_path.write_text(content, encoding="utf-8")
    return output_path
