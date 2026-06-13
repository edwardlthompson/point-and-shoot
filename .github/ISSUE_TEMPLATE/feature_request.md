---
name: Feature request
description: Suggest a new capability or improvement (not a bug)
title: "feat: "
labels: ["enhancement"]
body:
  - type: markdown
    attributes:
      value: |
        Fleet features should align with [`docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`](../docs/FLEET_DEVICE_CAPABILITY_MATRIX.md). Preview **chrome layout** changes require explicit maintainer approval.

  - type: textarea
    id: problem
    attributes:
      label: Problem or use case
      description: What problem does this solve? Who benefits?
    validations:
      required: true

  - type: textarea
    id: proposal
    attributes:
      label: Proposed solution
      description: High-level approach; link to BUILD_PLAN / parity intake if applicable
    validations:
      required: true

  - type: dropdown
    id: area
    attributes:
      label: Area
      options:
        - Capture / DNG
        - Video / encoding
        - Fleet / parity
        - Preview chrome / HUD
        - Settings / connectivity
        - Engineering / CI
        - Other
    validations:
      required: true

  - type: checkboxes
    id: scope
    attributes:
      label: Scope acknowledgment
      options:
        - label: I understand USB proof may be required before merge on camera pipeline changes
          required: false
