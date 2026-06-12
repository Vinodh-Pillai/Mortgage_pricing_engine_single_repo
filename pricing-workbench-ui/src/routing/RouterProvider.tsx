import type { ReactNode } from 'react';
import { BrowserRouter } from 'react-router-dom';

type RouterProviderProps = {
  children: ReactNode;
  basename?: string;
};

export function RouterProvider({ children, basename }: RouterProviderProps) {
  return <BrowserRouter basename={basename}>{children}</BrowserRouter>;
}

export default RouterProvider;
