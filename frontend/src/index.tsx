import React from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { bootstrap } from './bootstrap';
import { App } from './App';

const services = bootstrap();
createRoot(document.getElementById('root')!).render(<App services={services} />);
