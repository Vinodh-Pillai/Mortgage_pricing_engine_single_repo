import { ProductManagementScreen } from './ProductManagementScreen';
import type { ScreenVisualState } from '../contract/ScreenProps';

export default { title: 'PII-25/Functionality Pages/Product Management' };

const states: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function AllVisualStates() {
  return <>{states.map((state) => <div key={state} style={{ marginBottom: 24 }}><ProductManagementScreen visualState={state} /></div>)}</>;
}

export function DesktopDark() { return <ProductManagementScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><ProductManagementScreen /></div>; }
