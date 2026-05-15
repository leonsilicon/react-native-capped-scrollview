import {
  forwardRef,
  useEffect,
  useRef,
  useImperativeHandle,
  type ComponentRef,
  type Ref,
} from 'react';
import { ScrollView, type ScrollViewProps } from 'react-native';
import NativeCappedScrollView from './NativeCappedScrollView';

export type CappedScrollViewRef = ComponentRef<typeof ScrollView>;

export type CappedScrollViewProps = ScrollViewProps & {
  /**
   * Fling velocity cap as a normalized fraction in [0, 1], or `null` to
   * disable the cap mechanism entirely.
   *
   * The reference maximum is 8000 dp/s on Android and 8000 pt/s on iOS
   * (same logical unit), so the same `maxVelocity` value produces the same
   * visual scroll speed on both platforms.
   *
   * - `null` / `undefined` (default) — no capper installed; behaves exactly
   *   like a plain RN `ScrollView`.
   * - `1` — capper installed at the reference maximum. Human-paced flings
   *   pass through untouched.
   * - `0` — list cannot fling at all.
   * - `0 < x < 1` — peak fling velocity is clamped to `x × 8000` (dp/s on
   *   Android, pt/s on iOS). Fling distance scales linearly with `x`.
   */
  maxVelocity?: number | null;
};

// Sentinel sent to native when consumers pass `null`/omit the prop, meaning
// "no capper installed at all." Negative values are unreachable from the
// public [0, 1] range, so this is unambiguous on the native side.
const NO_CAP_SENTINEL = -1;

export const CappedScrollView = forwardRef(function CappedScrollView(
  { maxVelocity, ...rest }: CappedScrollViewProps,
  ref: Ref<CappedScrollViewRef>
) {
  const innerRef = useRef<CappedScrollViewRef | null>(null);

  useImperativeHandle(ref, () => innerRef.current as CappedScrollViewRef);

  useEffect(() => {
    const handle = innerRef.current?.getScrollableNode?.();
    if (handle == null) {
      return;
    }
    const value = maxVelocity == null ? NO_CAP_SENTINEL : maxVelocity;
    NativeCappedScrollView.setMaxVelocity(handle, value);
  }, [maxVelocity]);

  return <ScrollView ref={innerRef} {...rest} />;
});
