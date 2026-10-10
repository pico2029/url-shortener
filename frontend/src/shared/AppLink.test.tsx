import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, expect, it } from 'vitest';
import { AppLink } from './AppLink';

beforeEach(() => window.history.replaceState(null, '', '/register'));

it('intercetta il clic interno e aggiorna il percorso', () => {
  render(<AppLink href="/login">Log in</AppLink>);
  const link = screen.getByRole('link');
  expect(fireEvent.click(link, { button: 0 })).toBe(false);
  expect(window.location.pathname).toBe('/login');
});

it('non intercetta i clic che richiedono un’altra scheda', () => {
  render(<AppLink href="/login" target="_blank">Log in</AppLink>);
  expect(fireEvent.click(screen.getByRole('link'), { button: 0 })).toBe(true);
  expect(window.location.pathname).toBe('/register');
});
