import React from 'react';
import ReactDOM from 'react-dom/client';
import '@fontsource-variable/nunito';
import './styles/tokens.css';
import './styles/app.css';
import App from './App';
import { StoreProvider } from './store';

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><StoreProvider><App /></StoreProvider></React.StrictMode>);
