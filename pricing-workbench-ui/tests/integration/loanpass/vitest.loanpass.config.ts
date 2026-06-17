import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  root: '.',
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: [
      'tests/integration/loanpass/*.test.{ts,tsx}',
      'tests/performance/loanpass-form.perf.test.tsx',
    ],
  },
});
