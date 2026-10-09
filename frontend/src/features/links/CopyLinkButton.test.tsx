import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { CopyLinkButton } from './CopyLinkButton';

const url = 'http://localhost:8080/r/Ab123456';
const clipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

function clipboard(writeText?: ReturnType<typeof vi.fn>) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: writeText ? { writeText } : undefined });
}

afterEach(() => {
  if (clipboardDescriptor) Object.defineProperty(navigator, 'clipboard', clipboardDescriptor);
  else Reflect.deleteProperty(navigator, 'clipboard');
});

it('copies the full URL only once while pending and confirms after completion', async () => {
  let finish = () => {};
  const writeText = vi.fn(() => new Promise<void>(resolve => { finish = resolve; }));
  clipboard(writeText);
  render(<CopyLinkButton shortUrl={url} />);
  fireEvent.click(screen.getByRole('button', { name: 'Copy link' }));
  const button = screen.getByRole('button', { name: 'Copying…' });
  expect(button).toBeDisabled();
  expect(screen.getByRole('status')).toBeEmptyDOMElement();
  fireEvent.click(button);
  expect(writeText).toHaveBeenCalledExactlyOnceWith(url);
  await act(async () => finish());
  expect(screen.getByRole('status')).toHaveTextContent('Link copied.');
  expect(screen.getByRole('button')).toBeEnabled();
});

it('reports clipboard rejection and permits another attempt', async () => {
  const writeText = vi.fn().mockRejectedValueOnce(new Error('Denied')).mockResolvedValueOnce(undefined);
  clipboard(writeText);
  render(<CopyLinkButton shortUrl={url} />);
  fireEvent.click(screen.getByRole('button'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not copy the link. Select the address and copy it manually.');
  fireEvent.click(screen.getByRole('button'));
  expect(await screen.findByText('Link copied.')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('reports an absent clipboard API', async () => {
  clipboard();
  render(<CopyLinkButton shortUrl={url} />);
  fireEvent.click(screen.getByRole('button'));
  expect(await screen.findByRole('alert')).toHaveTextContent('copy it manually');
  expect(screen.getByRole('button')).toBeEnabled();
});

it('resets feedback when the URL changes and ignores old completions', async () => {
  let finish = () => {};
  const writeText = vi.fn().mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve; })).mockResolvedValue(undefined);
  clipboard(writeText);
  const { rerender } = render(<CopyLinkButton shortUrl={url} />);
  fireEvent.click(screen.getByRole('button'));
  rerender(<CopyLinkButton shortUrl={`${url}New`} />);
  await act(async () => finish());
  expect(screen.getByRole('status')).toBeEmptyDOMElement();
  expect(screen.getByRole('button')).toBeEnabled();
  fireEvent.click(screen.getByRole('button'));
  await screen.findByText('Link copied.');
  expect(writeText).toHaveBeenLastCalledWith(`${url}New`);
  rerender(<CopyLinkButton shortUrl={url} />);
  expect(screen.getByRole('status')).toBeEmptyDOMElement();
});

it('ignores a pending completion after unmount', async () => {
  let finish = () => {};
  clipboard(vi.fn(() => new Promise<void>(resolve => { finish = resolve; })));
  const { unmount } = render(<CopyLinkButton shortUrl={url} />);
  fireEvent.click(screen.getByRole('button'));
  unmount();
  await act(async () => finish());
  expect(screen.queryByText('Link copied.')).not.toBeInTheDocument();
});

it('supports keyboard activation without submitting the surrounding form', async () => {
  const user = userEvent.setup();
  const writeText = vi.fn().mockResolvedValue(undefined);
  clipboard(writeText);
  const submit = vi.fn(event => event.preventDefault());
  render(<form onSubmit={submit}><CopyLinkButton shortUrl={url} /></form>);
  await user.tab();
  expect(screen.getByRole('button')).toHaveFocus();
  await user.keyboard('{Enter}');
  await screen.findByText('Link copied.');
  await user.keyboard(' ');
  expect(writeText).toHaveBeenCalledTimes(2);
  expect(submit).not.toHaveBeenCalled();
});

it('provides Italian labels and feedback', async () => {
  clipboard(vi.fn().mockResolvedValue(undefined));
  render(<CopyLinkButton shortUrl={url} locale="it" />);
  fireEvent.click(screen.getByRole('button', { name: 'Copia link' }));
  expect(await screen.findByText('Link copiato.')).toBeInTheDocument();
});
