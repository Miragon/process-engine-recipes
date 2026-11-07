import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { createQueryClient } from './api/queries';
import { Desk } from './desk/Desk';
import { ToastProvider } from './ui/Toasts';

export function App() {
  const [client] = useState(createQueryClient);
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <Desk />
      </ToastProvider>
    </QueryClientProvider>
  );
}
