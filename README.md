# OpenBullet CE Mobile

Android port of **OpenBullet Cookie Edition**, focused on local environments and systems the tester owns or has explicit permission to test.

This is an Android-native port effort, not an attempt to hide or expand the original tool's abuse-oriented capabilities. Features that do not map safely or technically to Android are preserved for config compatibility when possible and reported as unsupported instead of being silently removed.

## Current direction

- Kotlin + Jetpack Compose Android application.
- PC config compatibility using the original `.lce` `[SETTINGS]` + `[SCRIPT]` container.
- Unknown desktop config fields are preserved during round trips.
- Imported LoliScript is inspected but is **not automatically executed**.
- Manual HTTP/HTTPS Runner requires an explicit authorized-host allowlist.
- Exact hosts and wildcard subdomains are separate authorization rules.
- Wordlists, cookie-set references, proxies, config library and Hits DB use local Android storage/document permissions.
- GitHub Actions runs config/security unit tests before building the debug APK.

See `docs/PC_CONFIG_COMPATIBILITY.md` and `docs/PORT_STATUS.md` for the compatibility contract and upstream-to-Android port matrix.

## Safety boundary

Use this project only on services you own or have explicit permission to test.

The Android port does not add automatic credential stuffing, rate-limit evasion, CAPTCHA-solving, Cloudflare bypass, arbitrary imported-script execution, or automatic session/cookie validation against third-party services. Desktop config text for unsupported features can still be preserved so a PC -> Android -> PC round trip does not destroy the original config.

## Desktop config round trip

The desktop format is retained:

```text
[SETTINGS]
{ ...ConfigSettings JSON... }

[SCRIPT]
...LoliScript...
```

An untouched imported config is exported using its original text. When supported fields are edited, unknown JSON settings and untouched script content are retained.

## Upstream

This project is a mobile port derived from the OpenBullet Cookie Edition codebase. The upstream project is licensed under the MIT License. This repository should retain the upstream copyright notice and license when distributing substantial portions or derivative work.
