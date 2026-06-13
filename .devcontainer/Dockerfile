# Dev container for host-side gates only (Gradle tests, Python scripts, pre-commit).
# USB ADB / Camera2 device verification is NOT available inside the container.
FROM mcr.microsoft.com/devcontainers/base:ubuntu

RUN apt-get update && apt-get install -y --no-install-recommends \
    powershell \
    && rm -rf /var/lib/apt/lists/*

# Android SDK is intentionally omitted — use host Android Studio for assembleDebug.
# See CONTRIBUTING.md and AGENTS.md for USB gate expectations.
