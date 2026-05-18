import type { TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  setMaxVelocity(reactTag: number, maxVelocity: number): void;
}

const NativeCappedScrollViewWeb: Pick<Spec, 'setMaxVelocity'> = {
  setMaxVelocity() {},
};

export default NativeCappedScrollViewWeb;
