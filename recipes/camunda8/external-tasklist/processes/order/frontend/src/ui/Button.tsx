import type { ButtonHTMLAttributes } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost';
}

export function Button({ variant = 'secondary', type = 'button', className, ...props }: ButtonProps) {
  return <button type={type} className={['button', `button--${variant}`, className].filter(Boolean).join(' ')} {...props} />;
}
