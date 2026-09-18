# Security and authorized-use policy

OpenBullet CE Mobile is developed as an Android port for local environments and systems where the tester has explicit authorization.

## Supported scope

Contributions may improve:

- Android UI and lifecycle behavior
- PC `.lce` import/export compatibility
- non-executing LoliScript parsing and compatibility reporting
- local storage and manager UX
- explicitly authorized HTTP/HTTPS testing
- tests, build reproducibility and defensive validation

## Out of scope

This port does not accept features whose main purpose is:

- credential stuffing against third-party services
- automatic rate-limit or ban evasion
- CAPTCHA or anti-bot bypass
- Cloudflare bypass
- stealth/proxy rotation intended to defeat service protections
- automatic validation of third-party sessions/cookies
- arbitrary execution of scripts imported from desktop configs

Unsupported desktop config content should be preserved for round-trip compatibility where possible, not silently removed.

## Secrets and reports

Do not commit access tokens, session cookies, private credentials, API keys or real production secrets. Use synthetic/local test data when filing issues or adding tests.
