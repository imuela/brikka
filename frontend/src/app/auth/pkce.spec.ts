import { generateCodeChallenge, generateRandomString } from './pkce';

describe('pkce', () => {
  it('generateRandomString produces a URL-safe string with no padding', () => {
    const value = generateRandomString();
    expect(value).toMatch(/^[A-Za-z0-9\-_]+$/);
    expect(value).not.toContain('=');
  });

  it('generateRandomString produces different values on each call', () => {
    expect(generateRandomString()).not.toBe(generateRandomString());
  });

  it('generateCodeChallenge is deterministic for the same verifier', async () => {
    const verifier = 'a-fixed-test-verifier-value';
    const challengeA = await generateCodeChallenge(verifier);
    const challengeB = await generateCodeChallenge(verifier);
    expect(challengeA).toBe(challengeB);
  });

  it('generateCodeChallenge matches the RFC 7636 appendix B test vector', async () => {
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
    const challenge = await generateCodeChallenge(verifier);
    expect(challenge).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('generateCodeChallenge produces a URL-safe string with no padding', async () => {
    const challenge = await generateCodeChallenge(generateRandomString());
    expect(challenge).toMatch(/^[A-Za-z0-9\-_]+$/);
    expect(challenge).not.toContain('=');
  });
});
