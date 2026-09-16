import { isSafeInternalUrl } from './safe-url.util';

describe('isSafeInternalUrl', () => {
  it('should reject empty values', () => {
    expect(isSafeInternalUrl('')).toBeFalse();
    expect(isSafeInternalUrl(null)).toBeFalse();
    expect(isSafeInternalUrl(undefined)).toBeFalse();
  });

  it('should accept valid internal paths', () => {
    expect(isSafeInternalUrl('/applications')).toBeTrue();
    expect(isSafeInternalUrl('/applications/123/details')).toBeTrue();
  });

  it('should reject URLs not starting with a slash', () => {
    expect(isSafeInternalUrl('applications')).toBeFalse();
  });

  it('should reject protocol-relative URLs', () => {
    expect(isSafeInternalUrl('//evil.com')).toBeFalse();
    expect(isSafeInternalUrl('///evil.com')).toBeFalse();
  });

  it('should reject absolute URLs', () => {
    expect(isSafeInternalUrl('https://evil.com')).toBeFalse();
    expect(isSafeInternalUrl('http://evil.com')).toBeFalse();
  });

  it('should reject javascript and data URIs', () => {
    expect(isSafeInternalUrl('javascript:alert(1)')).toBeFalse();
    expect(isSafeInternalUrl('data:text/html,<html>')).toBeFalse();
  });
});
