import '@fontsource-variable/geist/wght.css';
import './styles/cd-tokens.generated.css';
import './styles/app.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

const root = document.getElementById('root');
if (root === null) throw new Error('The page has no #root element.');
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Opened on its own by `npm run dev`, the forms play against a stand-in for the tasklist with sample data.
if (import.meta.env.DEV && window.parent === window) {
  void import('./dev/preview');
}
