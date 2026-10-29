import React from 'react';
import ReactDOM from 'react-dom/client';
import '@fontsource-variable/nunito';
import '../styles/tokens.css';
import '../styles/components.css';
import '../styles/app.css';
import './catalog.css';
import Catalog from './Catalog';

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><Catalog /></React.StrictMode>);
