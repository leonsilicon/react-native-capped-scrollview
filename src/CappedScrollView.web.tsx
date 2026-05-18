import { ScrollView, type ScrollViewProps } from 'react-native';
import type { ComponentRef, Ref } from 'react';

export type CappedScrollViewRef = ComponentRef<typeof ScrollView>;

export type CappedScrollViewProps = ScrollViewProps & {
  ref?: Ref<CappedScrollViewRef>;
  /**
   * Ignored on web — the native fling-velocity cap has no web equivalent.
   * Accepted for API parity so consumers don't need platform branches.
   */
  maxVelocity?: number | null;
};

export function CappedScrollView({
  ref,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  maxVelocity,
  ...rest
}: CappedScrollViewProps) {
  return <ScrollView ref={ref} {...rest} />;
}
