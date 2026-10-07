import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <main>
      <h1>URL Shortener</h1>
      <p>Struttura del progetto pronta.</p>
      <p>Le funzionalità di gestione dei link saranno implementate nelle prossime fasi.</p>
    </main>
  </StrictMode>,
);
