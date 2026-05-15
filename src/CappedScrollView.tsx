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
   *   null / undefined → no capper installed; behaves exactly like a plain
   *     RN ScrollView. (default)
   *   1 → capper is installed and set to the platform-native maximum:
   *     Android uses ViewConfiguration.getScaledMaximumFlingVelocity()
   *     (~8000 dp/s, scaled to physical px); iOS uses ~6000 pt/s (a typical
   *     practical human-fling ceiling).
   *   0 → list cannot fling at all.
   *   Intermediate values cap proportionally: at 0.25, the peak velocity is
   *     limited to a quarter of the platform max.
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
