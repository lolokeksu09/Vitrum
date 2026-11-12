# Vitrum TV — phone pairing and profile transfer security

**Status:** design-review gate; **no approved protocol or implemented pairing exists**.

## User story

The TV displays a QR code or short code. The owner uses a phone to transfer a single server profile or HTTPS subscription to the TV without entering a long secret on a remote. Transfer must require clear TV-side consent, and a third party nearby or on the same network must not be able to steal or replace the profile.

The QR itself **must not contain** the VPN share link, subscription URL, token, password or full configuration.

## Threat model

Assets: confidential subscription URL/token, server credentials, profile selection, app routing mode, device identity, stable connection state.

Adversaries: another device on the local Wi-Fi, a guest viewing the TV QR, a malicious QR scanner/browser, a compromised or spoofed transfer server, a replaying network observer, and accidental error/log/analytics exposure.

Risk examples:
- Passive QR screenshot reused after the owner left the room.
- Attacker guesses a 4–6-digit PIN and uploads a malicious server.
- HTTP endpoint on the TV allows unauthorized LAN commands.
- MITM substitutes a profile, enabling traffic interception.
- Reconnection after pairing retries an old sensitive payload; stale session is not deleted.
- Credential leaks via URL query strings, browser history, clipboard, accessibility overlays or crash reports.

## Hard requirements

1. **Cryptographic channel:** encryption and authenticated pairing binding the phone to the **exact TV session**, not just a short PIN. QR/code alone is not a sufficient trust boundary if repeatedly reusable.
2. **TV-side explicit approval** of the transfer, with human-readable non-secret sender/profile summary. No silent network-originated profile replacement.
3. **Ephemeral pairing:** high-entropy random session identifiers and key material, short expiration, bounded attempts, one-time use and revocation when the TV leaves Setup.
4. **No exposed secret:** QR only contains ephemeral pairing information. Never display/import/share credentials in plaintext status, logs, screen recordings, analytics or errors.
5. **Safe protocol:** no raw HTTP credential upload and no unauthenticated network listener; do not enable universal LAN access or bypass certificate validation. The repository security rule against new loopback service ports also applies.
6. **Input validation:** protocol-specific limits, HTTPS-only subscription URLs, bounded payload size, strict decoding and non-silent rejection of unsupported formats.
7. **Atomic encrypted persistence:** validate, encrypt and commit the new profile; on failure preserve the old profile and pairing state must not claim success.
8. **Replay resistance:** no reusing accepted payloads after session expiry, cancel or app restart.
9. **Offline/transport failure:** clear localized error with a retry that creates a new session, not a hidden retry of an expired credential.
10. **No account, tracker or remote service dependency** without an explicit separately reviewed product decision and privacy disclosure.

## Transport candidates (none approved yet)

| Option | Benefit | Security/UX consequence |
|---|---|---|
| Direct local pairing with dedicated phone companion | No hosted data relay; strong mutual authentication possible | Requires companion and correct discovery/TLS/key confirmation; phone and TV LAN connectivity not guaranteed |
| HTTPS relay with end-to-end encrypted payload | Browser-friendly phone-side experience across LAN boundaries | Requires operating a trusted service and proving opaque E2EE storage, expiration and traffic privacy; contradicts local-only default unless approved |
| D-pad / OS-supported manual secure import | Zero extra network listener and minimal platform dependencies | Poor long-link usability; acceptable as recovery/developer path only |

**Recommendation for design exploration:** start with a local authenticated companion pairing **prototype** and a manual fallback. Do **not** implement either over plain HTTP or commit to a cloud backend before security review. A web-only phone flow via self-signed local HTTPS has browser trust obstacles and must not quietly disable hostname/certificate checks.

## Protocol ADR required before implementation

An architecture decision record must specify:
- Trusted endpoint identities, who generates keys, the cryptographic handshake and QR payload fields.
- How the phone confirms the correct TV, how the TV authorizes receipt, and how both detect MITM/replay.
- Discovery method and network binding; port exposure; transport encryption; Android API 30 behavior.
- TTL, cancellation, force-stop behavior, rate limits, maximum payload size and data deletion semantics.
- Threat-model tests for passive capture, active substitution, duplicate use and unavailable/offline devices.
- Real BOXY + smartphone user journey with D-pad-only on-TV confirmation.

**Until this ADR is approved, the feature may appear only as a non-interactive roadmap item in design documentation, not as a fake working button in a release.**

## Acceptance tests

- Two phones race to claim the same pairing request; only the explicitly approved session can complete.
- Captured QR replay after expiry fails.
- Invalid/oversized/wrong-origin input cannot replace a stored valid profile.
- TV user cancels; subsequent phone request fails.
- Phone/TV network interruption invalidates or resumes **only** as specified by the approved protocol.
- Secrets absent from QR, system logcat, UI strings, exception messages and exported diagnostics.
- App and VPN operate with pairing service fully stopped when Setup screen closes.

## Reference

[Android TV inputs](https://developer.android.com/training/tv/get-started/navigation) ·
[Android Keystore](https://developer.android.com/privacy-and-security/keystore) ·
[Android VPN guidance](https://developer.android.com/develop/connectivity/vpn).
