#!/usr/bin/env python3
"""Run repo scripts without putting .sh paths in agent shell command strings."""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path, PureWindowsPath

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
LIB = SCRIPTS / "lib"
if str(LIB) not in sys.path:
    sys.path.insert(0, str(LIB))

from agent_run_env import child_env  # noqa: E402


def script_argv(script: Path) -> str:
    """Relative script path for bash when cwd is ROOT (Windows-safe)."""
    return script.relative_to(ROOT).as_posix()


def list_scripts() -> list[str]:
    names: set[str] = set()
    for path in SCRIPTS.glob("*.sh"):
        names.add(path.stem)
    for path in SCRIPTS.glob("*.ps1"):
        names.add(path.stem)
    return sorted(names)


def resolve_script(name: str) -> Path | None:
    sh = SCRIPTS / f"{name}.sh"
    if sh.is_file():
        return sh
    ps1 = SCRIPTS / f"{name}.ps1"
    if ps1.is_file():
        return ps1
    return None


def resolve_bash() -> str | None:
    """Prefer Git Bash on Windows; avoid WSL1 System32\\bash.exe (breaks npm)."""
    if os.name == "nt":
        # PureWindowsPath is constructible on Linux so the WSL1 skip test can run in CI.
        candidates = [
            PureWindowsPath(os.environ.get("ProgramFiles", r"C:\Program Files"))
            / "Git"
            / "bin"
            / "bash.exe",
            PureWindowsPath(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)"))
            / "Git"
            / "bin"
            / "bash.exe",
            PureWindowsPath(os.environ.get("LOCALAPPDATA", "") or r"C:\missing-local")
            / "Programs"
            / "Git"
            / "bin"
            / "bash.exe",
        ]
        for path in candidates:
            if os.path.isfile(os.fspath(path)):
                return os.fspath(path)
        which = shutil.which("bash")
        if which and "system32" not in which.replace("/", "\\").lower():
            return which
        return None
    return shutil.which("bash")


def run_script(name: str, args: list[str]) -> int:
    script = resolve_script(name)
    if script is None:
        print(f"ERROR: unknown script '{name}'", file=sys.stderr)
        print("Available scripts:", ", ".join(list_scripts()), file=sys.stderr)
        return 1

    if script.suffix == ".ps1":
        proc = subprocess.run(
            ["powershell", "-NoProfile", "-File", str(script), *args],
            cwd=ROOT,
            env=child_env(),
        )
        return proc.returncode

    bash = resolve_bash()
    if not bash:
        print(
            "ERROR: bash not found on PATH. Install Git Bash or use PowerShell wrappers "
            f"(scripts/{name}.ps1) manually.",
            file=sys.stderr,
        )
        return 1

    proc = subprocess.run(
        [bash, script_argv(script), *args],
        cwd=ROOT,
        env=child_env(),
    )
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run repo scripts without .sh paths in agent shell commands.",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List available script names and exit",
    )
    parser.add_argument("name", nargs="?", help="Script basename (no extension)")
    parser.add_argument("args", nargs=argparse.REMAINDER, help="Arguments passed to the script")
    parsed = parser.parse_args()

    if parsed.list:
        for name in list_scripts():
            print(name)
        return 0

    if not parsed.name:
        parser.print_help()
        return 1

    script_args = parsed.args
    if script_args and script_args[0] == "--":
        script_args = script_args[1:]

    return run_script(parsed.name, script_args)


if __name__ == "__main__":
    sys.exit(main())
