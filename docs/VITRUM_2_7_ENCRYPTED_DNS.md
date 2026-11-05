# VITRUM 2.7.0 — Encrypted DNS 853 Guard and Android Private DNS visibility

Base: published v2.6.0, main at 2026-11-05. This document describes a candidate implementation, not device-tested protection.

## Confirmed findings / references

- Existing VITRUM routing explicitly guards application DNS TCP/UDP 53, but with `defaultAction=direct` / geo bypass, TCP/UDP 853 may be sent directly.
- IETF RFC 7858 defines DNS-over-TLS on TCP 853. IETF RFC 9250 specifies DNS-over-QUIC by default on UDP 853. Transport identification by port alone is **not** evidence of actual DNS protocol or DNS leaks.
- RethinkDNS `Utilities.getPrivateDnsMode` inspects Android `LinkProperties.isPrivateDnsActive` and `privateDnsServerName` to separate strict and upgraded modes. Reimplement with public APIs only; do not copy copyrighted implementation.
- Android docs: `LinkProperties` Private DNS accessors available API 28+, `ConnectivityManager.getActiveNetwork` requires `ACCESS_NETWORK_STATE`; the active network visible to VITRUM's excluded process may not be the same path as a tunneled app.
- Orbot uses full IPv4/IPv6 VPN routes and its own Tor DNSPort; NetGuard explicitly handles route/DNS settings; sing-box has a separate DNS rule action. VITRUM keeps its Xray architecture.

## Functional changes

1. Add explicit ordered `port=853, network=tcp,udp` route to Xray `proxy` (or active auto-balancer) **after user-supplied rules**, but before bypassLan, exceptRu, presets and catch-all. Never hijack DoT/DoQ TLS; route intact packets via Xray.
2. Existing transport policy (TCP-only or UDP-only) and blockV6 remain higher priority and can block the matching family. Existing user direct/block/proxy port 853 rules remain higher priority; split tunneling remains unchanged.
3. Add a read-only offline DNS route audit for TCP/853 (DoT) and UDP/853 (DoQ) against known configured resolver IP literals. Return UNKNOWN when a prior geo/hostname rule may match. Never claim this audit measures system Private DNS's actual hostname/IP or actual packets.
4. Add manual Android Private DNS observation using `ConnectivityManager.getActiveNetwork()` and `LinkProperties` on Android 9+, classifying strict, opportunistically upgraded, off/not upgraded, unknown/unavailable; no hostname/IP saved or displayed. Note: the network seen by the VITRUM process may be excluded from VPN; cannot prove all-app DNS routes.
5. UI shows independent readouts and retains `DNS leak: UNKNOWN`. Do not send queries or create background monitors.
6. One normal Android permission `android.permission.ACCESS_NETWORK_STATE` is added solely to read active network properties; no location, traffic or secrets collected.

## Regression criteria

- Guard must precede default direct, geo and LAN bypass and be present in default proxy/direct/block and DoH modes.
- User explicit direct/block rule on 853 stays ahead of guard, and route audit identifies it.
- UDP/853 remains blocked in only-TCP mode; TCP/853 remains blocked in only-UDP mode; IPv6 ::/0 remains blocked under blockV6.
- Auto subscription balancer used instead of direct proxy if present; no changes to server credentials or VPN itself.
- API 26-27 Private DNS status: not supported; API 28+ no link: UNKNOWN, strict name: STRICT, upgraded active: UPGRADED, else NOT_ACTIVE. Never leak hostname in report.
- Real Xray config validation, unit tests, Android 16 UI smoke; official author signing verified before pre-release APK. No physical Android VPN leak safety claim.

## Risks

This changes routing of connections to port 853 that were previously direct or blocked only by default route, and may affect local resolvers/proxy UDP compatibility. Custom user rules or app exclusions can still route outside the proxy intentionally. Encrypted DNS over HTTPS 443 and alternative ports are not covered. If observed Private DNS mode is off, this is not evidence of a DNS leak; if strict/on, it is not proof of tunneling.
