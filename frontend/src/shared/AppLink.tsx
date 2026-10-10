import type { AnchorHTMLAttributes, MouseEvent } from 'react';
import { navigate } from './navigation';

type AppLinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & { href: string };

export function AppLink({ href, onClick, ...props }: AppLinkProps) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    const link = event.currentTarget;
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey
      || event.shiftKey || event.altKey || (link.target && link.target !== '_self')
      || link.hasAttribute('download')) return;

    const destination = new URL(link.href);
    if (destination.origin !== window.location.origin || destination.hash) return;

    event.preventDefault();
    const path = destination.pathname + destination.search;
    if (path !== window.location.pathname + window.location.search) navigate(path);
  }

  return <a {...props} href={href} onClick={follow} />;
}
