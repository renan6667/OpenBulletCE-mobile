# PC config compatibility

The Android port treats desktop OpenBullet Cookie Edition configs as a compatibility contract.

## Container format

The desktop project serializes a config as two sections:

- `[SETTINGS]` — JSON representing `ConfigSettings`
- `[SCRIPT]` — the original LoliScript text

The Android codec preserves the complete settings JSON, including keys the Android app does not understand yet.

## Round-trip rule

A config imported from PC must be exportable back to PC without silently removing unknown settings or untouched script text. If the config was not edited, the Android codec returns the original imported text unchanged.

If Android cannot execute a desktop-only block, the block must remain in the script and the UI should report it as unsupported instead of deleting it.

## Compatibility states

- **Native** — understood and editable on Android.
- **Preserved** — retained losslessly but not currently editable/executable on Android.
- **Unsupported** — recognized as desktop-only; preserved and reported to the user.

The port must prefer preservation over lossy conversion.


## File extension

Cookie Edition desktop saves configs as `.lce`. The Android export flow therefore suggests `.lce` as well.

## Runtime boundary

Importing a desktop config does not automatically execute its LoliScript. Desktop-only Selenium/browser actions, embedded scripts, captcha-solving blocks and Cloudflare-bypass blocks are preserved for round-trip compatibility and reported by the UI instead of being silently removed.
