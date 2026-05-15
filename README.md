# react-native-capped-scrollview

A drop-in replacement for React Native's `ScrollView` that lets you cap how
fast the user can fling the list. Useful for surfaces where you want to keep
the visual scroll speed inside a comfortable, readable range — long-form
content, kiosk-style UIs, accessibility modes — without disabling momentum
entirely.

## Installation

```sh
npm install react-native-capped-scrollview
```

Requires the New Architecture (Fabric + TurboModules). Tested against
React Native 0.83.

## Usage

```tsx
import { CappedScrollView } from 'react-native-capped-scrollview';

<CappedScrollView maxVelocity={0.25}>
  {/* same API as <ScrollView> */}
</CappedScrollView>;
```

`CappedScrollView` accepts every prop the built-in `ScrollView` accepts.
Internally it renders an RN `ScrollView` and attaches a native fling
interceptor when `maxVelocity` is set.

## API

### `maxVelocity?: number | null`

Normalized fling-velocity cap.

| Value           | Behaviour                                                                                                                                                                                          |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `null`/omitted  | No capper installed. Behaves exactly like a plain `ScrollView`. **Default.**                                                                                                                       |
| `0`             | List cannot fling at all. Drag still works; releasing the finger stops the scroll dead.                                                                                                            |
| `1`             | Capper installed but set to the platform's own maximum fling velocity. Functionally identical to natural scroll for human-paced flings; faster-than-natural programmatic flings get clamped.       |
| `0 < x < 1`     | Peak fling velocity is clamped to `x × platformMax`. The fling distance scales with `√x` so flings don't feel abruptly truncated — they coast a bit further than a simple velocity clamp would.   |

The **platform max** is:

- **Android** — `ViewConfiguration.get(context).getScaledMaximumFlingVelocity()`. This is the system constant Android already uses to clamp every fling on the platform; on a typical xhdpi device it's around 16 000 px/s (8000 dp/s scaled to 2× density).
- **iOS** — 8000 pt/s. iOS has no system-imposed maximum, so the library picks a value matched to Android's reference number for consistency. In practice a hard human swipe peaks around 3 000–6 000 pt/s, so `maxVelocity={1}` is mostly transparent and starts mattering for programmatic or unusually fast flings.

The distinction between `null` and `1` is intentional: at `1` the cap *mechanism* is wired up (so future runtime changes via state are instant), whereas `null` skips the install entirely (the scroll view is byte-for-byte identical to RN's default).

### Ref

The ref returned by `CappedScrollView` is the underlying RN `ScrollView` instance — so `scrollTo`, `scrollToEnd`, `getScrollableNode`, etc. all work as you'd expect.

```tsx
const ref = useRef<CappedScrollViewRef>(null);
// ...
ref.current?.scrollTo({ y: 0, animated: true });
```

## How it works

The library never re-implements `ScrollView`. It wraps RN's own `<ScrollView>` (so every prop, every event, sticky headers, refresh control, etc. continue to work) and attaches a small native hook to the underlying platform scroll view to intercept fling velocity.

### iOS

A TurboModule resolves the React tag (`getScrollableNode()`) to the underlying `RCTScrollViewComponentView` by walking the window hierarchy and matching on `UIView.tag`, which Fabric sets to the React tag at mount time. Resolution retries every ~33 ms for up to ~1 s, because in Fabric the scroll view's UIView may not exist at the moment `useEffect` runs.

Once found, the module attaches a `UIScrollViewDelegate` proxy to the scroll view's `scrollViewDelegateSplitter` — RN exposes this as the official extension point for adding delegates without replacing the primary one, so all of RN's own scroll event plumbing keeps working.

The proxy implements `scrollViewWillEndDragging:withVelocity:targetContentOffset:`. UIScrollView reports velocity in points-per-millisecond and accepts an `inout` target offset. The proxy:

1. Compares the requested peak velocity to `fraction × 8000 pt/s` (converted to pt/ms).
2. If the user's fling is already within the cap, leaves everything untouched (no added friction).
3. Otherwise scales the target offset by `√(cap / peak)` so the resulting fling distance feels natural rather than truncated to a tiny coast.

When `maxVelocity` becomes `null`, the proxy is removed via `[splitter removeDelegate:]` — the scroll view returns to its exact default state.

### Android

A TurboModule resolves the React tag via `UIManagerHelper.getUIManagerForReactTag(...).resolveView(tag)` (with a Fabric UIManager fallback), retrying on the main thread for up to ~1 s for the same Fabric mount-timing reason.

Once the `ReactScrollView` is found, the module replaces its `OverScroller` (Android's deceleration physics object — `mScroller` on the framework `ScrollView` class, plus RN's cached reference on `ReactScrollView`) with a subclass that overrides both `fling(...)` signatures. Both fields are written via reflection because they're declared `private`.

The replacement scroller:

1. On every fling, reads the user's current `decelerationRate` off `view.reactScrollViewScrollState.decelerationRate` so the user's `decelerationRate` prop is still honoured after the swap.
2. Computes `cap = fraction × ViewConfiguration.getScaledMaximumFlingVelocity()`.
3. If the requested fling already fits the cap, applies the base friction and forwards the fling unmodified.
4. Otherwise lowers friction by `√(cap / peak)` and clamps the start velocity to `cap`, then calls `super.fling(...)`. Lowering friction sub-linearly stretches the fling duration enough that the lower-velocity fling still covers a reasonable distance instead of stopping abruptly.

When `maxVelocity` becomes `null`, the module restores the original `OverScroller` it captured at install time, so the scroll view returns to its exact default state.

### JS glue

`src/CappedScrollView.tsx` is a `forwardRef` wrapper around `<ScrollView>`. On `maxVelocity` change, it calls `innerRef.current?.getScrollableNode()` to get the native React tag and forwards `(tag, value)` to the TurboModule. `null` is encoded as the sentinel `-1`, which the native side interprets as "remove the cap from this scroll view."

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
