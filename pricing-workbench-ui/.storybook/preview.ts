import '../src/styles.css';

export const globalTypes = {
  theme: {
    name: 'Theme',
    defaultValue: 'dark',
    toolbar: { icon: 'circlehollow', items: ['dark', 'light'] },
  },
};

export const decorators = [
  (Story: () => JSX.Element, context: { globals: { theme?: string } }) => {
    document.documentElement.dataset.theme = context.globals.theme === 'light' ? 'light' : 'dark';
    return Story();
  },
];

export const parameters = {
  controls: { expanded: true },
  a11y: { test: 'todo' },
  viewport: { defaultViewport: 'responsive' },
  visualRegression: { baseline: '.local-harness/evidence/PII-25-S07/storybook-baseline' },
};
