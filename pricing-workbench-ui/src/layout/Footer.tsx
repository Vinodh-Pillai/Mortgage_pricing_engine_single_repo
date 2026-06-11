import { useTranslation } from '../lib/i18n';

export function Footer() {
  const { t } = useTranslation('common');
  return (
    <footer className="layout-footer" role="contentinfo">
      <span>Pricing Workbench v0.1.0</span>
      <a href="#status-panel">{t('systemStatus')}</a>
      <a href="#help-panel">{t('help')}</a>
      <span aria-label="Shell status">{t('responsiveShellReady')}</span>
    </footer>
  );
}
