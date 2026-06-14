const config = {
  stories: ['../src/design-system/**/*.stories.@(ts|tsx)', '../src/screens/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-a11y'],
  framework: { name: '@storybook/react-vite', options: {} },
};

export default config;
