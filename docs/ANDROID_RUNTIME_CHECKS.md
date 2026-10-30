# VITRUM — Android runtime regression matrix

Manual verification checklist, **not a record of successful tests**. Do not mark any row as passed without device evidence.

Target: Android 14, 15 and 16; arm64; verified APK hash and commit.

| Scenario | Action | Expected |
| --- | --- | --- |
| Foreground-service rejection | Disable/restrict service launch using device-supported testing tools | No Xray after rejection; visible failure and stopped service |
| Background startup | Connect from tile, widget, schedule and boot where supported | VPN starts or a clear refusal, no crash |
| Network changes | Switch Wi-Fi to mobile and back while carrying traffic | Reconnect; kill switch does not leak |
| Xray crash | Terminate the Xray child in a controlled test | Recover or keep traffic blocked if kill switch is enabled |
| VPN permission revoked | Revoke VPN in system Settings | Cleanup, UI and notification consistent |
| Always-on and lockdown | Enable Android Always-on and block traffic without VPN, restart | System policy honored; excluded apps separately checked |
| AutoStore storage failure | Fail storage/Keystore write in instrumentation | Old list/selected server intact; error shown |
| Corrupt JSON cache | Corrupt test-only autosub.dat and restart | Never overwritten, no silent loss |
| Backup over 8 MB | Select oversized .vitrumbak | Rejected with bounded reading |
| Author-signed update | Check available official release | Only author-signed APK offered |
| Debug update | Check from ephemeral debug-fallback APK | No incompatible in-place update offered |
| Accessibility | Home, Servers, Routing, Shield, Settings at font 1.0, 1.3, 1.5, TalkBack, dark theme | Controls identifiable and not clipped |

Record Android version, device, APK checksum, commit, reproducible steps, expected/actual and redacted logcat. Never include credentials or subscription addresses.
