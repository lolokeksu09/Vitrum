# VITRUM 2.7.0 — Encrypted DNS 853 Guard and Android Private DNS Visibility

> **Archived technical plan.** This document records DNS/853 design and Android Private DNS observations for the intermediate 2.7.0 release line. It is historical context, not a claim of complete network protection or a current backlog. Current diagnostic limitations are described in [Vitrum 2.0.0](releases/v2.0.0.md).

Base: published v2.6.0, `main` as of 2026-11-05. This document describes a candidate implementation, not device-tested protection.

## Confirmed findings and references
- Existing VITRUM routing explicitly guards application DNS TCP/UDP 53, but with `defaultAction=direct` / geo bypass, TCP/UDP 853 may be sent directly.
- IETF RFC 7858 defines DNS-over-TLS on TCP 853. IETF RFC 9250 specifies DNS-over-QUIC by default on UDP 853. Port-based transport identification alone is **not** evidence of the DNS protocol or a DNS leak.
- RethinkDNS `Utilities.getPrivateDnsMode` inspects Android `LinkProperties.isPrivateDnsActive` and `privateDnsServerName` to distinguish strict and upgraded modes. Reimplement using public APIs only; do not copy copyrighted code.
- Android documentation: Private DNS accessors on `LinkProperties` are available from API 28; `ConnectivityManager.getActiveNetwork` requires `ACCESS_NETWORK_STATE`. The active network visible to VITRUM's excluded process may differ from the route used by a tunneled app.
- Orbot uses full IPv4/IPv6 VPN routes and its own Tor DNSPort; NetGuard explicitly handles route/DNS settings; sing-box has a separate DNS rule action. VITRUM retains its Xray architecture.

## Functional changes recorded in the plan
1. Add an ordered `port=853, network=tcp,udp` route to Xray `proxy` (or the active auto-balancer) **after user-supplied rules**, but before bypassLan, exceptRu, presets, and catch-all. Do not hijack DoT/DoQ TLS; route intact packets through Xray.
2. Existing transport policy (TCP-only or UDP-only) and blockV6 remain higher priority and may block the matching family. Existing user direct/block/proxy port 853 rules remain higher priority; split tunneling is unchanged.
3. Add a read-only offline DNS route audit for TCP/853 (DoT) and UDP/853 (DoQ) against configured resolver IP literals. Return UNKNOWN when a prior geo/hostname rule may match. Do not claim this measures system Private DNS hostnames/IPs or actual packets.
4. Add manual Android Private DNS observation using `ConnectivityManager.getActiveNetwork()` and `LinkProperties` on Android 9+, classifying strict, opportunistically upgraded, off/not upgraded, unknown, or unavailable. Do not save/display hostname or IP. The network seen by the VITRUM process may be excluded from VPN; this cannot prove all-app DNS routes.
5. Show independent UI readouts and retain `DNS leak: UNKNOWN`. Do not send queries or create background monitors.
6. Add only the normal `android.permission.ACCESS_NETWORK_STATE` permission to read active network properties; collect no location, traffic, or secrets.

## Regression criteria
- Guard precedes default direct, geo, and LAN bypass and is present in default proxy/direct/block and DoH modes.
- An explicit user direct/block rule on 853 remains ahead of the guard, and route audit identifies it.
- UDP/853 remains blocked in TCP-only mode; TCP/853 remains blocked in UDP-only mode; IPv6 `::/0` remains blocked under blockV6.
- Use the auto-subscription balancer instead of direct proxy when present; do not change server credentials or VPN itself.
- API 26–27 Private DNS: unsupported; API 28+ no link: UNKNOWN; strict name: STRICT; upgraded active: UPGRADED; otherwise NOT_ACTIVE. Never expose hostname in reports.
- Validate real Xray configs, unit tests, and Android 16 UI smoke; verify official maintainer signing before pre-release APK. Do not claim physical Android VPN leak safety.

## Risks

This changes routing for port 853 connections that were previously direct or only blocked by the default route, and may affect local resolvers or proxy UDP compatibility. Custom user rules or app exclusions may intentionally route outside the proxy. Encrypted DNS over HTTPS on 443 and alternative ports are not covered. If observed Private DNS is off, that is not evidence of a DNS leak; if strict/on, that is not proof of tunneling.
