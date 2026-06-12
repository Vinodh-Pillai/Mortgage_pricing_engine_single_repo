const config = {
  stories: ['../src/design-system/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-essentials', '@storybook/addon-a11y', '@storybook/addon-viewport'],
  framework: { name: '@storybook/react-vite', options: {} },
};

export default config;
