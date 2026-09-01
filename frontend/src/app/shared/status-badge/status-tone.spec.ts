import { statusTone } from './status-tone';

describe('statusTone', () => {
  it('maps the closed RAG level set to semantic tones (BRIKKA V2 I2)', () => {
    expect(statusTone('GREEN')).toBe('success');
    expect(statusTone('AMBER')).toBe('warning');
    expect(statusTone('RED')).toBe('error');
    // "no signal" must not look like a warning.
    expect(statusTone('NOT_EVALUATED')).toBe('neutral');
  });

  it('still resolves the existing lexical patterns', () => {
    expect(statusTone('APPROVED')).toBe('success');
    expect(statusTone('REJECTED')).toBe('error');
    expect(statusTone('PENDING')).toBe('warning');
    expect(statusTone(null)).toBe('neutral');
  });
});
