// Match Java String.strip(): Character.isWhitespace, which excludes non-breaking spaces.
export function normalizeAlias(value: string): string {
  return value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g, '');
}

export function validAlias(value: string): boolean {
  return value === '' || /^[A-Za-z0-9_-]{3,32}$/.test(value);
}
