import { LockManagementScreen } from './LockManagementScreen';
import type { ScreenVisualState } from '../contract/ScreenProps';

export default { title: 'PII-25/Functionality Pages/Lock Management' };

const states: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function AllVisualStates() {
  return <>{states.map((state) => <div key={state} style={{ marginBottom: 24 }}><LockManagementScreen visualState={state} /></div>)}</>;
}

export function DesktopDark() { return <LockManagementScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><LockManagementScreen /></div>; }
