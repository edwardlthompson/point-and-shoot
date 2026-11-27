"""Sanitize the environment passed to agent-run child processes."""
from __future__ import annotations

import os
from pathlib import Path


def windows_tool_dirs() -> list[Path]:
    if os.name != "nt":
        return []
    return [
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "GitHub CLI",
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "nodejs",
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Git" / "bin",
        Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "GitHub CLI",
    ]


def unix_tool_dirs(home: Path | None = None) -> list[Path]:
    """User-local tool prefixes when system PATH lacks git/java/adb (apt-locked hosts)."""
    if os.name == "nt":
        return []
    home = home if home is not None else Path.home()
    dirs: list[Path] = [
        home / ".local" / "bin",
        home / ".local" / "node" / "bin",
        home / "Android" / "Sdk" / "platform-tools",
        home / ".local" / "android" / "platform-tools",
    ]
    mamba_root = home / ".local" / "micromamba" / "envs" / "tools" / "bin"
    dirs.append(mamba_root)
    # Optional env override for non-default micromamba env name
    extra = os.environ.get("BOOTSTRAP_TOOLS_BIN", "").strip()
    if extra:
        dirs.insert(0, Path(extra).expanduser())
    return dirs


def child_env(base: dict[str, str] | None = None) -> dict[str, str]:
    env = dict(base if base is not None else os.environ)
    env.pop("PYTHONPATH", None)
    home = Path(env.get("HOME") or Path.home())
    extras = [p for p in windows_tool_dirs() + unix_tool_dirs(home=home) if p.is_dir()]
    if extras:
        prefix = os.pathsep.join(str(p) for p in extras)
        env["PATH"] = prefix + os.pathsep + env.get("PATH", "")
    # Prefer user-local JDK/SDK when unset (micromamba / Android Sdk layout)
    if not env.get("JAVA_HOME"):
        java_home = home / ".local" / "micromamba" / "envs" / "tools"
        if (java_home / "bin" / "java").is_file() or (java_home / "bin" / "java.exe").is_file():
            env["JAVA_HOME"] = str(java_home)
    if not env.get("ANDROID_HOME") and not env.get("ANDROID_SDK_ROOT"):
        for sdk in (home / "Android" / "Sdk", home / ".local" / "android"):
            if (sdk / "platform-tools").is_dir():
                env["ANDROID_HOME"] = str(sdk)
                env["ANDROID_SDK_ROOT"] = str(sdk)
                break
    return env
