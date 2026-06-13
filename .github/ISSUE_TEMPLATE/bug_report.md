---
name: Bug report
description: Report a crash, capture failure, or incorrect camera behavior
title: "fix: "
labels: ["bug"]
body:
  - type: markdown
    attributes:
      value: |
        Search existing issues first. For **security** problems use [SECURITY.md](../SECURITY.md) private reporting — do not file public security bugs here.

  - type: textarea
    id: description
    attributes:
      label: What happened?
      description: Steps to reproduce, expected vs actual behavior
      placeholder: "1. Open preview on CPH2583… 2. Tap H still… 3. …"
    validations:
      required: true

  - type: input
    id: version
    attributes:
      label: App version
      placeholder: "e.g. 0.14.0-beta.7 (versionCode …)"
    validations:
      required: true

  - type: input
    id: device
    attributes:
      label: Device / ROM
      placeholder: "e.g. OnePlus 12 CPH2583, LineageOS 23"
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: Logs / artifacts
      description: Logcat tags (`PNS.CaptureStill`, `PNS.ChromeUx`), or `hfr-runs/` path if from repo scripts
    validations:
      required: false

  - type: checkboxes
    id: checklist
    attributes:
      label: Contributor checklist
      options:
        - label: I read CONTRIBUTING.md and did not attach secrets or keystore material
          required: true
