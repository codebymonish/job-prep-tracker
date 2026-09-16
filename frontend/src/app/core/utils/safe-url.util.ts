export function isSafeInternalUrl(url: string | null | undefined): boolean {
  if (!url || !url.startsWith('/')) {
    return false;
  }
  
  if (url.startsWith('//')) {
    return false;
  }

  try {
    const baseUrl = 'http://internal';
    const parsedUrl = new URL(url, baseUrl);
    
    if (parsedUrl.hostname !== 'internal') {
      return false;
    }
    
    return true;
  } catch (e) {
    return false;
  }
}
