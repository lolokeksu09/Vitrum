# VITRUM 2.5.0 — DNS & IPv6 Protection

> **Archived implementation plan.** This is an intermediate DNS/IPv6 work snapshot for the 2.5.0 line as of 2026-11-04. Statements such as "not yet released" apply only to that date and do not describe the current release. See [Vitrum 2.0.0 release notes](releases/v2.0.0.md) for the consolidated current release.

As of 2026-11-04, implementation was merged into `main` through PR #64; version 2.5.0 and its release workflow were added through PR #65. JVM tests, Debug/Clone builds, Linux Xray 26.3.27 `run -test` for three configs without Android TUN ingress, and Android 16 UI smoke passed. **The official 2.5.0 APK had not yet been released:** the previous publishing run was blocked by an older TUN check; the release workflow needed to be rerun after the fix. No ARM64 device test had been performed.

## Reviewed projects and architecture
- [Orbot](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java): Android `VpnService.Builder` configures IPv4/IPv6 routes and a DNS address in TUN. [TorConfig.kt](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/tor/TorConfig.kt) configures `DNSPort` and `TransPort`. Architectural reference only; do not copy code into Xray.
- [RethinkDNS](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/service/BraveVPNService.kt): DNS routes and system `addDnsServer` / IPv4/IPv6 catch-all differ from DNS-only mode, where individual IP families may bypass.
- [NetGuard](https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java): explicit VPN routes and DNS servers. Its packet handler must not be transplanted directly into Xray.
- [sing-box](https://github.com/SagerNet/sing-box): separate proxy engine with its own DNS rules; VITRUM retains Xray and the existing TUN.

VITRUM already configures `0.0.0.0/0`, `::/0`, and system DNS in `RayVpnService.kt`. In `ConfigBuilder.kt`, DNS through Xray on port 53 is explicitly intercepted only when `doh || blockV6 || fakeDns`. In other modes, global `defaultAction=direct`, geo rules, or bypass mode could affect DNS TCP/UDP 53. DNS requests from excluded apps are outside the VPN and are not controlled by VITRUM.

## Changes and invariants
1. **DNS Route Guard:** when `doh || blockV6 || fakeDns`, retain existing DNS 53 redirection to Xray `dns-out`. Otherwise add an explicit TCP/UDP 53 → `proxy` rule (or primary auto-subscription balancer). Place it after user rules but **before** `bypassLan`, `exceptRu`, presets, and `defaultAction`. Explicit user DNS routing remains possible.
2. **Ordering and compatibility:** do not change profile/storage formats, package `app.rayclient`, VpnService Builder, TUN, Xray, signing key, or existing app exclusions. Do not create listening TCP ports.
3. **Diagnostics:** show configured DNS policy (settings/routes) separately from actual IPv4/IPv6 reachability through the diagnostic Xray socket. DNS leak status remains `UNKNOWN` without an independent observer. `blockV6` on `tun-in` does not automatically make IPv6 unavailable on `probe-in`.
4. **Tests:** TCP/UDP 53 under default proxy/direct/block; DoH/FakeDNS/IPv6 block; user-rule and `bypassLan`/regional bypass ordering; auto-balancer; fixed IP families; no false DNS leak-free assertion.
5. **Release:** CI (`testDebugUnitTest`, `assembleDebug`, `assembleClone`, UI smoke); Xray `run -test` for relevant configs where available; signed maintainer APK only after successful release build. Pre-release until real Android tests.

## Security boundaries and limitations
- Routing DNS 53 through proxy in modes with `defaultAction=direct` is a **behavior change**. LAN resolvers may become unreachable through the proxy unless an explicit direct exception is configured. The UI must explain this.
- Apps using DoH/DoT (443/853), Android Private DNS resolvers, domain/IP rules, excluded apps, and other VPNs require separate analysis. Intercepting port 53 alone does not guarantee no DNS leaks.
- IPv6 reachability through `probe-in` does not confirm app routes; an IPv6 timeout proves neither presence nor absence of a leak.
- An aggregated DNS Monitor cannot be implemented honestly without a valid Xray/TUN event source. Do not add invented counters or background network requests.
- Actual Android routing and leaks can only be confirmed by independent device testing.

## Acceptance
- Configs with `defaultAction=direct` and no special DNS mode contain an explicit TCP/UDP 53 → proxy rule above catch-all direct.
- Existing `doh || blockV6 || fakeDns` behavior continues to route port 53 to `dns-out`; `blockV6` does not route `::/0` directly.
- An explicit user DNS 53 rule takes priority. Never claim the policy is "100% secure" from config inspection alone.
- Official build retains the same signing identity and is verified in GitHub Actions.
