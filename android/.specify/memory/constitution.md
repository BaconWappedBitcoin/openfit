# OpenFit Constitution

## Core Principles

### I. Library-First
Every feature MUST begin its existence as a standalone library. The wire protocol, the transport, and the
workout programming model are separate libraries with explicit boundaries. No feature may be implemented
directly inside the Android application code without first being abstracted into a reusable, independently
testable library component.

### II. CLI Interface Mandate
Every library MUST expose its functionality through a command-line interface:
- Accept text as input (stdin, arguments, or files)
- Produce text as output (stdout), errors on stderr
- Support JSON output for structured data exchange

This enforces observability. The protocol must be drivable and verifiable from a terminal without an Android
device attached, so the wire format can be tested in isolation and debugged against captured bytes.

### III. Test-First (NON-NEGOTIABLE)
All implementation MUST follow strict Test-Driven Development. No implementation code may be written before:
1. Unit tests are written
2. Tests are validated and approved by the user
3. Tests are confirmed to FAIL (Red phase)

The Red-Green-Refactor cycle is strictly enforced. Golden-vector tests (recorded bytes in, bytes out) are
mandatory for every framing, checksum, and command-encoding path.

### IV. Safety-First (Project-Defined)
This project drives a machine that a human stands on. Safety outranks feature completeness.
- The application MUST never start, accelerate, or incline a treadmill without an explicit, deliberate
  user action in the current session.
- The application MUST enforce and display a user-configurable speed ceiling and incline ceiling that
  cannot be exceeded by a remote or replayed command.
- An emergency stop MUST be reachable from the primary screen within one interaction, and MUST bring the
  machine to a safe state even if the transport layer is unresponsive.
- The application MUST NOT run unattended automation (for example, a scheduled workout that starts a belt
  with no person present) in the first release.
- Loss of the Bluetooth link MUST be treated as a stop condition: the application MUST surface the
  disconnect prominently and MUST NOT report a machine as "running" after the link drops.

### V. Evidence-Based Protocol (Project-Defined)
No claim about the wire format may enter the codebase without evidence.
- Every protocol constant (UUID, command id, field id, length, scale factor, checksum rule) MUST cite its
  source: either a captured byte log (Bluetooth capture of the console) or a public
  reverse-engineering reference.
- Constants that are guessed, inferred, or plausible-but-unverified MUST be marked as such in code and
  MUST NOT be presented to the user as known-good.
- Unverified behaviour MUST be isolated behind a feature flag that defaults to OFF.
- Captured byte sequences are the ground truth and MUST be stored as test fixtures.

### VI. Licensing and Attribution (Project-Defined)
- This project MUST NOT incorporate source code from GPL-3.0 or AGPL-3.0 licensed projects. The protocol
  implementation MUST be written from the documented wire format and observed behaviour only.
- The protocol is implemented from facts (field identifiers, byte layouts, command sequences). Facts are not
  copyrightable expression; the implementation is independently authored.
- Every external reference consulted MUST be recorded in the project documentation with its name, URL,
  license, and what was taken from it.
- No third-party vendor code, assets, branding, or
  account infrastructure is redistributed in this project.

### VII. Simplicity
- Maximum three modules in the initial implementation: protocol library, transport library, application.
  Additional modules require documented justification.
- No speculative extensibility. A second equipment protocol is not implemented until one is verified working.

### VIII. Anti-Abstraction and Framework Trust
- Use the Android platform's Bluetooth APIs directly rather than wrapping them.
- A single model representation per concept; no parallel "legacy" and "new" models.
- No configuration abstraction that exists only to support a hypothetical future device.

### IX. Integration-First Testing
Tests MUST use realistic conditions:
- Real captured byte vectors rather than hand-authored round-trip expectations
- An actual BLE peripheral (the user's treadmill) for transport tests
- Contract tests at the transport boundary are mandatory before any transport implementation is written

## Additional Constraints

### Constraint 1: No Cloud Dependency and No Vendor Account
The controller MUST work with the machine alone. No account, no remote server, no internet connection
and no login may be required for any core function. The application MUST remain fully functional in airplane
mode.

### Constraint 2: Transport and Protocol Separation
The protocol library MUST NOT depend on Android. It operates on byte arrays in and byte arrays out, so the
wire format can be exercised on a desktop JVM with recorded vectors.

### Constraint 3: Safety Ceilings Cannot Be Bypassed
Speed and incline ceilings are enforced at the lowest layer that writes to the machine, not only in the UI,
so that a bug in any screen cannot exceed them.

### Constraint 4: Activation and Unlock Handling
Where a machine requires an unlock before accepting control, the application MUST support both a
computed unlock (derived from device-reported values) and a manually supplied unlock, and MUST clearly
report which one is in use and what the machine's state is (locked, unlocked, armed).

## Development Workflow

1. **Specify** - a feature specification is authored and validated against the quality checklist.
2. **Plan** - an implementation plan is authored, including the protocol evidence table for any new wire
   format behaviour.
3. **Tasks** - work is broken into independently verifiable tasks.
4. **Implement** - tasks are executed in order. Each task follows the Red-Green-Refactor cycle.
5. **Converge** - the codebase is assessed against the specification and plan; remaining work is appended.
6. **Hardware Validation** - a feature that writes to the machine is not complete until it has been
   exercised against the user's physical treadmill with the user's consent.

### Quality Gates
- No protocol constant without an evidence citation (Article V).
- No implementation without a failing test first (Article III).
- Any new Bluetooth permission must be justified in the pull request description.
- Safety-related behaviour changes require re-running the hardware validation checklist.

## Governance

This constitution supersedes all other project practices. Every generated plan, task list, and implementation
MUST be checked against it.

Amendments require:
1. Explicit documentation of the rationale for the change
2. Review and approval by the project maintainer (the user)
3. A backwards-compatibility assessment for any rule that changes existing behaviour

Complexity that violates an article MUST be justified in a "Complexity Tracking" section of the plan rather
than silently accepted.

**Version**: 1.0.0 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22
