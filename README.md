# cloud-itonami-isco-3324

Open Occupation Blueprint for **ISCO-08 3324**: Trade Brokers.

This repository designs a forkable OSS business for an independent trade brokerage practice: a document-handling and shipment-tracking robot manages trade documentation under a governor-gated actor, so the practice keeps its own deal records instead of renting a closed trade-platform SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-handling and shipment-tracking robot performs bill-of-lading printing, cargo-manifest filing and physical archival under an actor that proposes
actions and an independent **Trade Brokerage Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
deal confirmation above the client's registered trade-authorization ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
buyer/seller instruction + commodity spec + shipment terms
        |
        v
Trade Advisor -> Trade Brokerage Governor -> broker deal/confirm shipment, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3324`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
