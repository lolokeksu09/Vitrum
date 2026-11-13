# Vitrum TV — TV-first interaction and visual contract

**Status:** proposed design baseline for the future product. No rendered implementation or device screenshots yet.

## 1. Design objective

**Calm, legible, operational.** The hero is truthful VPN state; the primary action is Connect or Disconnect; selected server is discoverable without deep navigation. This is a **10-foot interface** for a four-direction D-pad, not a phone UI enlarged to landscape. The existing Vitrum name and restrained violet accent are visual continuity, not an instruction to reuse phone layouts.

### Design anti-goals

- Fake glass, cyberpunk glow, animated orbs, neon borders on every card, constant motion or decorative statistics.
- Mouse cursors as a primary navigation concept; any control usable only by touch, swipes or hover.
- Three competing primary actions, full-screen settings wall, tiny badges or horizontally clipped names.
- Displaying raw URLs, credentials, UUIDs, hostnames from secret profiles or stack traces in TV notification/toast/QR surfaces.
- Confusing an active Xray/TUN process with verified routing, or labeling an unavailable check as passed.

## 2. Primary navigation model

Five possible top-level destinations **as product areas**, not an obligation to show five permanent tabs:
- **Home:** current state, primary action, active server and a compact route-evidence summary.
- **Servers:** available servers, selection and verified/non-verified latency distinctions.
- **Apps:** ALL / ONLY / EXCLUDE semantics and installed-app selection.
- **Setup:** add/replace profile; QR-assisted pairing only after secure transfer design is approved.
- **Settings:** Always-on information, diagnostics, language, About and troubleshooting.

**Initial recommended structure:** left-side compact navigation rail, large main content on the right. Home may show an abbreviated server selector; the full list is a dedicated screen. The main Connect button and selected-server card both remain reachable in <=3 deliberate D-pad moves from opening Home (acceptance target; verify on remote).

**Default focus:** primary Connect when disconnected; Disconnect when connected; Retry in an error; Add profile when no profile. Returning from Settings/Servers restores focus to the originating control if still available. A removed list row moves focus to nearest valid row or an explicit empty-state action.

**Back:** first dismisses dialog/keyboard, then returns to previous destination, then yields to the system home without disconnecting VPN. Never make Back silently drop the tunnel.

## 3. Focus contract

- Every actionable control has a visible focused state distinct from selection, connected and disabled states.
- Focus moves predictably with Left/Right/Up/Down, without jumping to off-screen controls or disappearing after a model refresh.
- Selected server and **currently connected server** are separate states, especially while switching.
- No automatic text-field focus or software keyboard when entering Setup.
- A list with 1, 10 and 256 items must scroll to focused item with no animation-induced loss of focus.
- D-pad Center/OK triggers once; rapid repeated OK must not schedule overlapping starts/stops, destructive actions or duplicate imports.
- Dialogs have reachable Cancel and Confirm, an obvious initial focus, and a Back exit. Destructive confirmation must not be the initial focus.
- System VPN permission dialog must be navigable by the actual BOXY remote; handle denial, resume and focus restoration. Firmware/system dialog limitations must be reported honestly rather than worked around with accessibility hacks.

## 4. Screen layout proposals

### Home (1920×1080 logical composition, not fixed pixel coordinates)

Safe-area first: preserve approximately **5% edge inset** on each side on overscanning displays; use dp/sp and measure device density rather than fixed pixel sizes.

```text
+--------------------------------------------------------------+
| Vitrum TV         Home   Servers   Apps   Setup   Settings     |
|                                                              |
| [ Status: Not connected                   ]                  |
| [ Selected profile / server              ] [ Recent state  ] |
| [ > CONNECT < ]                            [ Diagnostics   ] |
|                                                              |
| [ Last result: Not checked / verified test path only          ] |
|                                                              |
|   Help: OK Select · Back Return                              |
+--------------------------------------------------------------+
```

Text labels are illustrative. Final layout should prioritize visible focus and avoid stacking a complete feature dashboard on Home.

### Servers

- Clear selected indicator, connected indicator and focus treatment; never use only color.
- Show user-visible short names, protocol and contextual measurement labels (e.g., TCP port response, not “VPN ping”).
- Long labels ellipsize without shifting surrounding controls; full safe details may be displayed in a secondary panel. No exposed credentials.
- Load/probe indicators do not block moving focus. Retry is explicit for failed loads.

### Apps

- First choose ALL / ONLY / EXCLUDE, with an explanation of traffic bypass.
- Apps grid/list uses recognizable names and icons only where package visibility rules permit; package identification may be shown in details.
- Existing user choices are restored; empty ONLY mode cannot be activated.
- Applying changes to an active VPN requires explicit reconnect messaging rather than pretending the current TUN changes instantly.

### Setup

- Clear non-sensitive explanation of supported profile sources.
- **Phone setup** is the preferred entry once paired transfer passes security review.
- Fallback import must remain possible using a D-pad (e.g., OS-supported secure text input or a verified Android TV remote app), without requiring a mouse.
- Pairing token screen never includes credentials or server URLs, is cancellable and has an expiration countdown driven by real session state.

## 5. Brand and type

**Suggested palette (design tokens; not yet implemented):**

| Role | Dark TV proposal | Meaning |
|---|---|---|
| Canvas | `#0B0B11` | Low-glare backdrop |
| Surface | `#171722` | Focusable regions and panels |
| Raised | `#222232` | Focused content layer |
| Text primary | `#F4F2FB` | Strong contrast |
| Text secondary | `#B9B5CA` | Supporting text |
| Vitrum accent | `#A78BFA` | Focus/action |
| Positive | `#47D4AD` | Explicit positive state only |
| Warning | `#F0C474` | Unverified/limited state |
| Error | `#F08D8D` | Actionable failure |

Use semantic theme roles in code, not scattered hard-coded colors. Brand type can retain a restrained family with validated Cyrillic support; if brand font reduces TV legibility or memory profile, prefer platform Roboto. Use sp for scaling. Initial **design targets, not measured defaults**: hero state approximately 36–44 sp, primary button 22–26 sp, body 20–24 sp, secondary labels >=18 sp, primary focused hit region >=64 dp. Validate actual legibility at 2–3 meters and enlarged font settings.

- Use a clear border/tonal highlight/scale delta for focus, not only a violet dot.
- No always-on animation. Use short, cancellable transitions for state changes and honor reduced-motion settings where available.
- One unmistakable primary button; secondary decisions placed in a coherent, non-competing area.

## 6. Required states and copy

| Internal meaning | Russian UI | English UI |
|---|---|---|
| No selected profile | `Нет профиля` | `No profile` |
| Idle with profile | `Не подключено` | `Disconnected` |
| Permission required | `Требуется разрешение Android` | `Android VPN permission required` |
| Service startup | `Подключение…` | `Connecting…` |
| TUN + engine active | `VPN запущен` | `VPN started` |
| Diagnostic pending | `Проверяем соединение…` | `Checking connection…` |
| Bounded diagnostic passed | `Проверенный маршрут доступен` | `Test route is reachable` |
| Diagnostic unknown | `Защита трафика не проверена` | `Traffic protection not verified` |
| Reconnecting | `Переподключение…` | `Reconnecting…` |
| Stopping | `Отключение…` | `Disconnecting…` |
| Failure | `Не удалось подключиться` | `Could not connect` |

Avoid simplistic “Protected” badges unless the precise tested protection property is shown. Current route diagnostics alone are not a universal DNS leak test.

## 7. TV review requirements

Create emulator/device screenshots from the actual Android app, not a generated mock, for:
- Home: no profile, disconnected, connecting, started, error, reconnecting.
- Servers: 1, 10, 256 items, long Cyrillic/English labels, selected vs connected.
- Apps: ALL, ONLY, EXCLUDE and empty-selection error.
- Setup: waiting, expired pairing, declined system permission.
- Focus: visible on every actionable control; 1080p and 4K output where actually supported.
- TV remote tests only; a mouse may be used for diagnostics but never to mark a control reachable.

Record the exact APK, device/firmware and screenshot provenance. A written design or generative concept image is not UI verification.

## Platform references

[Android TV navigation](https://developer.android.com/training/tv/get-started/navigation),
[Android TV adaptive layouts](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv),
[Compose focus guidance](https://developer.android.com/develop/ui/compose/touch-input/focus).
