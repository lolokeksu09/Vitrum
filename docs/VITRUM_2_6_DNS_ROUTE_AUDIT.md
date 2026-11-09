# VITRUM 2.6.0 — DNS Route Audit and Literal DNS Validation

> **Archived technical plan.** This document records the DNS Route Audit design for the intermediate 2.6.0 release line. It is not a current open-task list. See [Vitrum 2.0.0](releases/v2.0.0.md) for the consolidated primary-release description.

Base: VITRUM 2.5.0 (`main`). Scope: maintainer-approved follow-up patch; no application ID or signing change.

## Findings from upstream code
- RethinkDNS [Utilities.kt](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/util/Utilities.kt) distinguishes strict/automatic Android Private DNS with LinkProperties. [BraveVPNService.kt](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/service/BraveVPNService.kt) separates DNS routing from default routes and handles IPv4/IPv6 separately. Do not copy its licensed implementation.
- Orbot [OrbotVpnManager.java](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java) explicitly configures IPv4/IPv6 routes and a DNS server in the VPN; Tor [TorConfig.kt](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/tor/TorConfig.kt) has a dedicated DNSPort. This is architectural context, not an equivalent Xray API.
- NetGuard [ServiceSinkhole.java](https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java) explicitly handles VPN routes and DNS servers.
- sing-box [route actions](https://github.com/SagerNet/sing-box/blob/testing/docs/configuration/route/rule_action.md) separates DNS interception from general routing; VITRUM remains based on Xray.
- Android [VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder) documents that allowFamily without routes can let an IP family fall through to the underlying network. Do not add allowFamily without routing or claim that a socket probe proves TUN traffic.

## Confirmed VITRUM gaps recorded at the time
1. `Dns.kt` validated custom DNS using `\\d{1,3}` for IPv4 or a lenient hexadecimal/colon pattern. This accepted impossible IPv4 octets, incomplete IPv6, and malformed host syntax. Invalid choices were silently replaced with preset 0 without a visible explanation.
2. `DnsRoutePolicy.describe` described a generic DNS port 53 guard. However, custom `Rule` entries for IP/CIDR, ports, or domains could precede the guard, including `direct` or `block`; the description did not explain actual first-match precedence.
3. `RouteExplain` already implemented conservative ordered-rule evaluation. It could be reused for **configuration-only** DNS port 53 auditing after extracting pure TUN route evaluation, without simulating a fake app or making network requests.

## Intended implementation
- `DnsAddressPolicy.kt`: accept numeric IPv4/IPv6 literals only, without hostname resolution; reject malformed ambiguous strings, deduplicate, and preserve the existing fallback when no custom address is valid. Settings should show invalid-address count and fallback warning.
- `RouteExplain.kt`: expose pure `evaluateTunRoute` for traffic already included in VPN; preserve existing app-exclusion behavior in `evaluate`.
- `DnsRouteAudit.kt`: inspect ConfigBuilder rules for each configured DNS resolver over UDP/53 and TCP/53. Conservative result categories: direct, blocked, proxy, internal Xray DNS, mixed, or unknown. Do not store/report IP addresses.
- Shield: explicit manual local audit and clearly labeled configuration-only results; DNS A/AAAA leak-proof state remains UNKNOWN. No queries, permissions, or telemetry.

## Verification criteria
1. Unit tests: impossible IPv4, incomplete IPv6, invalid bracket/zone, valid literals, deduplication and fallback; direct IP/CIDR and port 53 before guard; DNS 53 guard before default direct; mixed IPv4/IPv6; private geo UNKNOWN; app exclusions preserved.
2. CI: `testDebugUnitTest`, Debug/Clone compilation, generated Xray config validation, Android 16 UI smoke.
3. Release: new version only after CI; maintainer-signed pre-release; original package and certificate retained. **Do not claim physical ARM64 traffic or DNS/IPv6 leak safety until real device tests.**

## Behavior and risks

Malformed custom DNS values that were previously accepted must not be sent to Xray; retain valid entries or use the existing preset fallback if all are invalid. Preserve stored text so the user can correct it. The audit is read-only and may return UNKNOWN when geodata/sniffing affects rule evaluation. It never proves actual TUN routing or DNS leak-free operation. Apps excluded from VPN remain outside VITRUM control. Android strict Private DNS and DoH/DoT on 443/853 remain unmeasured and must be labeled accordingly.
