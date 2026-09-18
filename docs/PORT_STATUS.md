# Android Port Status

This document tracks the Android port against the upstream OpenBullet Cookie Edition desktop repository.

The goal is feature and file-format portability for local or explicitly authorized testing. Desktop-only functionality that does not map safely or technically to Android is preserved when possible instead of silently deleted.

## Legend

- **Native** — implemented as an Android-native equivalent.
- **Partial** — useful Android implementation exists, but desktop parity is not complete.
- **Preserved** — data/config syntax survives PC -> Android -> PC, but is not executed on Android.
- **Pending** — not ported yet.
- **Desktop-only** — no direct Android equivalent is planned; compatibility data should still be preserved where applicable.

## Application areas

| Desktop area | Android status | Notes |
| --- | --- | --- |
| Main navigation | Native | Compose navigation exposes Runner, Proxies, Wordlists, Cookies, Configs, Hits, Tools, Plugins, Settings and About. |
| Runner Manager | Partial | Authorized single-request runner exists. Multi-runner/session parity is pending. |
| Proxy Manager | Partial | Local proxy records and desktop-style parser exist. No automatic evasion/rotation behavior. |
| Wordlist Manager | Partial | Storage Access Framework import, persisted URI, background line counting, search and Name/Type/Purpose metadata editing exist. Environment-based type recognition is pending. |
| Cookie Manager | Partial | Persisted folder references exist. Cookie-file parsing/editing parity is pending. |
| Config Manager | Partial | Import/edit/export of desktop .lce containers, persisted config library, safe metadata/request/data editors and lossless unknown-field preservation exist. DataRules/CustomInputs editors are pending. |
| Stacker / LoliScript editor | Partial | Script text can be viewed/edited. Visual block editor and safe parser coverage are pending. |
| Hits DB | Partial | Local results store, search, config/type filters and duplicate removal exist. Export/copy parity is pending; automatic recheck is intentionally not ported. |
| Tools | Partial | Port-aware screens represent desktop List Generator, Selenium Tools and Database areas. Selenium is desktop-only; more Android-safe utilities are pending. |
| Plugins | Compatibility-only | UI explains the port boundary and .lce RequiredPlugins metadata remains preserved. Desktop .NET DLL execution is not portable to Android. |
| Settings | Partial | Authorized hosts, explicit wildcard rules and request timeout exist. Broader desktop UI settings parity is pending. |
| About | Native | Android-specific About screen exists. |
| Logging | Pending | Structured in-app logs are not yet equivalent to desktop logging. |

## Desktop config compatibility

Desktop Cookie Edition configs use:

```text
[SETTINGS]
<ConfigSettings JSON>

[SCRIPT]
<LoliScript>
```

Android uses the same container contract.

- Desktop extension: `.lce`.
- Unknown settings keys are retained.
- An untouched imported config is exported using its original text.
- Editing known keys does not intentionally remove unknown keys.
- Unsupported script lines stay in `[SCRIPT]` instead of being deleted.

## ConfigSettings coverage

The codec preserves the full JSON object even when Android has no editor for a field yet.

| Group | Preservation | Android editor/runtime |
| --- | --- | --- |
| General metadata (Name, Author, Version, etc.) | Preserved | Name/Author editable; more fields pending |
| Request settings | Preserved | Partial |
| Proxy settings | Preserved | Partial |
| Data rules | Preserved | Pending |
| Custom inputs | Preserved | Pending |
| Selenium settings | Preserved | Desktop-only/runtime disabled |
| Required plugins | Preserved | Reporting UI pending |

## LoliScript / block families

The port separates **format compatibility** from **runtime execution**. Imported scripts are not automatically executed.

| Upstream block family | Android plan |
| --- | --- |
| REQUEST | Pending safe/local implementation through authorized-target policy |
| PARSE | Pending |
| FUNCTION / utility / encoding / conversion / crypto helpers | Pending Android-safe equivalents |
| KEYCHECK | Pending |
| COOKIE container | Pending |
| TCP | Pending; must obey explicit target authorization |
| LS code / embedded script | Preserved only; arbitrary embedded code is not executed |
| Selenium NAVIGATE / BROWSERACTION / ELEMENTACTION / MOUSEACTION | Preserved; desktop-only |
| EXECUTEJS | Preserved; not executed on Android |
| CAPTCHA / RECAPTCHA / SolveCaptcha / ReportCaptcha | Preserved; not executed |
| BYPASSCF | Preserved; not executed |

## Storage architecture

Current persistence is intentionally simple:

- app preferences: SharedPreferences
- managers/hits: JSON stored in SharedPreferences
- documents: Android Storage Access Framework URIs
- desktop configs: imported/exported directly through ContentResolver

Migration to Room can be considered when manager data grows, without changing the `.lce` compatibility contract.

## Build

GitHub Actions runs JVM compatibility/security tests and then builds a debug APK on the Android port branch. A successful test + APK build is required before port-status items should be marked Native/Partial.

## Next parity work

1. Keep CI green after every change.
2. Finish config library and ConfigSettings editors without lossy conversion.
3. Add a safe LoliScript parser/compatibility inspector before adding runtime block support.
4. Port Android-safe utilities and logging.
5. Improve manager persistence/search/import/export.
6. Add tests for `.lce` round trips and malformed configs.
7. Only then expand authorized Runner behavior module-by-module.
