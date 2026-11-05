# VITRUM 2.6.0 — DNS Route Audit and Literal DNS Validation

Base: VITRUM 2.5.0 (main). Scope: user's approved follow-up patch, no app ID or signing change.

## Findings from upstream code

- RethinkDNS [Utilities.kt](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/util/Utilities.kt) distinguishes strict/automatic Android Private DNS with LinkProperties. [BraveVPNService.kt](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/service/BraveVPNService.kt) separates DNS routing from default routes and handles IPv4/IPv6 separately. Avoid copying its licensed implementation.
- Orbot [OrbotVpnManager.java](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java) explicitly configures IPv4/IPv6 routes and a DNS server in the VPN, while Tor [TorConfig.kt](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/tor/TorConfig.kt) has a dedicated DNSPort. This is architectural context, not an equivalent Xray API.
- NetGuard [ServiceSinkhole.java](https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java) handles VPN routing and DNS servers explicitly.
- sing-box [route actions](https://github.com/SagerNet/sing-box/blob/testing/docs/configuration/route/rule_action.md) distinguishes DNS interception from general routing; VITRUM remains Xray.
- Android official [VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder) documents that allowFamily without routes can let a family fall through to the underlying network. Do not add allowFamily without routing, and do not claim a socket probe proves TUN traffic.

## Confirmed VITRUM gaps

1. `Dns.kt` currently validates custom DNS using `\\d{1,3}` IPv4 or a lenient hexadecimal/colon pattern; this accepts impossible IPv4 octets, incomplete IPv6 and malformed host syntax. Invalid choices are silently replaced with preset 0, without a visible explanation.
2. `DnsRoutePolicy.describe` describes a generic DNS port 53 guard. However, custom `Rule` entries for IP/CIDR, ports or domains may precede the guard, including `direct` or `block`; the existing description does not explain actual first-match precedence.
3. `RouteExplain` already implements conservative ordered-rule evaluation. It can be reused for **configuration-only** DNS port 53 auditing after extracting the pure TUN routing evaluation, without simulating a fake app or making network requests.

## Implementation

- `DnsAddressPolicy.kt`: only numeric IPv4/IPv6 literals, no hostname resolution; reject ambiguous malformed strings, dedupe, preserve existing fallback when no valid custom address. `Dns.kt` uses the policy and Settings shows invalid-address count and fallback warning.
- `RouteExplain.kt`: expose a pure `evaluateTunRoute` that evaluates rules for traffic already included in the VPN; keep `evaluate` app-exclusion behavior unchanged.
- `DnsRouteAudit.kt`: inspect existing ConfigBuilder rules for each configured DNS resolver on both UDP/53 and TCP/53. Conservative aggregation: report direct, blocked, proxy, internal Xray DNS, mixed or unknown. No IP in saved/reportable result.
- Shield: explicit manual local audit button and clearly labeled configuration-only results; DNS A/AAAA leak-proof state stays UNKNOWN. No queries, permissions or telemetry.

## Verification

1. Unit tests: impossible IPv4, incomplete IPv6, invalid bracket/zone, literals valid, dedupe and fallback; direct IP/CIDR and port 53 before guard, DNS 53 guarded before default direct, mixed IPv4/IPv6, private geo UNKNOWN, excluded apps preserved.
2. CI: testDebugUnitTest, assembleDebug/Clone, generated Xray config validation, Android 16 UI smoke. Any failure blocks merge.
3. Release: new version only after CI; author-signed pre-release, original package and certificate retained. **No claim of physical ARM64 traffic or DNS/IPv6 leak safety until real device tests.**

## Behavior and risks

Previously accepted but malformed custom DNS values are no longer sent to Xray; remaining valid entries are kept, or unchanged preset fallback is used if all are invalid. Stored text is preserved for correction. The audit is read-only and may return UNKNOWN when geodata/sniffing could affect rules; it never proves actual TUN routing or DNS leak-free operation. Apps excluded from the VPN remain outside VITRUM's control. Android strict Private DNS / DoH/DoT 443/853 remain unmeasured and must be clearly labeled.
