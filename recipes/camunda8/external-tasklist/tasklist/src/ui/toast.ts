import { createContext, useContext } from 'react';
import type { Tone } from './tones';

export interface ToastMessage {
  tone: Tone;
  message: string;
}

export type ShowToast = (toast: ToastMessage) => void;

export const ToastContext = createContext<ShowToast>(() => undefined);

/** Feedback on an action, shown bottom centre and closing by itself; never `alert()`. */
export function useToast(): ShowToast {
  return useContext(ToastContext);
}
