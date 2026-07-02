import { LockManagementScreen } from './LockManagementScreen';

export default { title: 'PII-25/Functionality Pages/Lock Management' };

export function AllVisualStates() {
  return <LockManagementScreen />;
}

export function DesktopDark() { return <LockManagementScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><LockManagementScreen /></div>; }
