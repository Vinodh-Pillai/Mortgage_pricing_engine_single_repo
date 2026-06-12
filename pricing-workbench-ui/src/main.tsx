import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { LocaleProvider } from './lib/i18n';
import { RouterProvider } from './routing/RouterProvider';
import './styles.css';

createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <LocaleProvider>
      <RouterProvider>
        <App />
      </RouterProvider>
    </LocaleProvider>
  </React.StrictMode>,
);
