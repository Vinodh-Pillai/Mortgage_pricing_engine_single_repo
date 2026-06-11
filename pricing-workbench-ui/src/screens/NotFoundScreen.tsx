import { getAllScreenModules } from './contract/registry';
import type { ScreenProps } from './contract/ScreenProps';
import { ScreenWrapper } from './contract/VisualState';

export function NotFoundScreen(props: ScreenProps) {
  const modules = getAllScreenModules();
  return (
    <ScreenWrapper
      screenId="not-found"
      title="Screen module not found"
      state={modules.length > 0 ? 'needs-attention' : 'empty'}
      dataRefs={['screen-registry']}
      guidance={[modules.length > 0 ? 'Choose an available workbench module from the registry.' : 'No screen modules are registered yet.']}
      onEvidenceCapture={props.onEvidenceCapture}
    >
      <p>The requested workbench path is not registered.</p>
      {modules.length > 0 ? (
        <ul aria-label="Available screen modules">
          {modules.map((module) => <li key={module.id}>{module.label} - {module.routePattern}</li>)}
        </ul>
      ) : null}
    </ScreenWrapper>
  );
}

export default NotFoundScreen;
