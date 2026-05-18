# react-native-capped-scrollview

A drop-in replacement for React Native's `ScrollView` that lets you cap how
fast the user can fling the list. Useful for surfaces where you want to keep
the visual scroll speed inside a comfortable, readable range — long-form
content, kiosk-style UIs, accessibility modes — without disabling momentum
entirely.

<p align="center">
  <img src="https://raw.githubusercontent.com/leonsilicon/react-native-capped-scrollview/main/scrollview-capped.gif" width="320" alt="Side-by-side demo: capped column on the left flings at a controlled speed while the plain ScrollView on the right coasts naturally." />
</p>

## Installation

```sh
npm install react-native-capped-scrollview
# or
yarn add react-native-capped-scrollview
# or
pnpm add react-native-capped-scrollview
```

Then on iOS run `pod install` inside the `ios/` directory (or `npx expo prebuild` if you're on Expo). Android picks the library up automatically via autolinking.

Requires the New Architecture (Fabric + TurboModules). Tested against React Native 0.83; should work on any RN ≥ 0.80.

### Web (react-native-web)

The library ships a web stub so it won't crash bundlers targeting `react-native-web`. On web, `CappedScrollView` renders a plain `ScrollView` and the `maxVelocity` prop is accepted but ignored — there's no browser API to intercept fling/inertia velocity without replacing native scrolling wholesale, which would break accessibility and the "drop-in `ScrollView`" contract. Use the prop on iOS/Android; treat it as a no-op on web.

## Usage

```tsx
import { CappedScrollView } from 'react-native-capped-scrollview';

export function MyList() {
  return (
    <CappedScrollView maxVelocity={0.25} style={{ flex: 1 }}>
      {items.map((item) => (
        <Row key={item.id} item={item} />
      ))}
    </CappedScrollView>
  );
}
```

`CappedScrollView` accepts every prop the built-in `ScrollView` accepts (it renders one under the hood) and adds a single new prop, `maxVelocity`.

The GIF above is the example app from `example/` — a `CappedScrollView` on the left and a plain `ScrollView` on the right being flung with the same gesture. Run it with `yarn example ios` or `yarn example android`.

## API

### `maxVelocity?: number | null`

Normalized fling-velocity cap.

| Value          | Behaviour                                                                                                                                             |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `null`/omitted | No capper installed. Behaves exactly like a plain `ScrollView`. **Default.**                                                                          |
| `0`            | List cannot fling. Drag still works; releasing the finger stops the scroll dead.                                                                      |
| `1`            | Capper installed but set to the reference maximum. Human-paced flings pass through untouched; only faster-than-human programmatic flings get clamped. |
| `0 < x < 1`    | Peak fling velocity is clamped to `x × 8000 dp/s` on Android (`x × 8000 pt/s` on iOS). The two units are identical, so the cap on each platform corresponds to the same logical scroll speed.       |

The reference max is **8000 dp/s** on Android (scaled to physical pixels via `displayMetrics.density`) and **8000 pt/s** on iOS. Because a `dp` and a `pt` are the same logical unit, the same `maxVelocity` value applies the same peak-velocity cap on both platforms.

> [!NOTE]
> The cap controls *peak velocity*, not fling distance. iOS's `UIScrollView` decelerates exponentially while Android's `OverScroller` uses a spline whose distance is roughly `velocity^1.7`, so a single `maxVelocity` value produces a similar starting speed on both platforms but Android's fling will cover proportionally less distance than iOS's. If you need pixel-for-pixel cross-platform parity, drive the scroll yourself via `scrollTo` with an animation library.

The distinction between `null` and `1` is intentional: at `1` the cap mechanism is wired up (so future runtime changes via state are instant), whereas `null` skips the install entirely (the scroll view is byte-for-byte identical to RN's default).

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

1. Computes `cap = fraction × 8000 pt/s` (converted to pt/ms to match UIScrollView's units).
2. If the user's fling is already within `cap`, leaves the target untouched.
3. Otherwise multiplies `(target − current)` by `cap / peak`, which produces a fling whose peak velocity is exactly `cap` and whose distance is linearly proportional to how much the velocity was clamped.

When `maxVelocity` becomes `null`, the proxy is removed via `[splitter removeDelegate:]` — the scroll view returns to its exact default state.

### Android

A TurboModule resolves the React tag via `UIManagerHelper.getUIManagerForReactTag(...).resolveView(tag)` (with a Fabric UIManager fallback), retrying on the main thread for up to ~1 s for the same Fabric mount-timing reason.

Once the `ReactScrollView` is found, the module replaces its `OverScroller` (Android's deceleration physics object — `mScroller` on the framework `ScrollView` class, plus RN's cached reference on `ReactScrollView`) with a subclass that overrides both `fling(...)` signatures. Both fields are written via reflection because they're declared `private`.

The replacement scroller:

1. Computes `cap = fraction × 8000 dp/s × displayMetrics.density` (matching the iOS reference of 8000 pt/s — same numeric value, same logical unit).
2. If the requested fling already fits the cap, forwards the fling unmodified.
3. Otherwise scales both `velocityX` and `velocityY` by `cap / peak` and calls `super.fling(...)`. The framework's deceleration physics then run from the clamped start velocity, producing a fling whose peak velocity is exactly `cap`. Distance is governed by `OverScroller`'s spline (≈ `velocity^1.7`), so fast flings end up coasting somewhat less than the equivalent iOS fling.

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
