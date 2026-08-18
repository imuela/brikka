import { StatusLabelPipe } from './status-label.pipe';

describe('StatusLabelPipe', () => {
  const pipe = new StatusLabelPipe();
  const labels = { ACTIVE: 'Activo', PENDING: 'Pendiente' };

  it('returns the mapped Spanish label for a known value', () => {
    expect(pipe.transform('ACTIVE', labels)).toBe('Activo');
  });

  it('falls back to the raw value for an unmapped value', () => {
    expect(pipe.transform('UNKNOWN_STATUS', labels)).toBe('UNKNOWN_STATUS');
  });

  it('returns an em dash for null, undefined and empty string', () => {
    expect(pipe.transform(null, labels)).toBe('—');
    expect(pipe.transform(undefined, labels)).toBe('—');
    expect(pipe.transform('', labels)).toBe('—');
  });
});
