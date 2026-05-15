import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  setMaxVelocity(reactTag: number, maxVelocity: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('CappedScrollView');
