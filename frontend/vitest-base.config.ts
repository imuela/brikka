import { defineConfig } from 'vitest/config';

// Sprint 35: found via repeated evidence, not a guess — three different, unrelated spec files
// (conversation-detail-dialog in Sprint 34, edit-financial-profile-dialog here) hit Vitest's
// default 5000ms per-test timeout only when the FULL ~450-test suite runs together, and every one
// of them passes in a few seconds when run alone. That pattern (always the full suite, never in
// isolation, a different test each time) is worker-pool scheduling contention under this
// project's test count, not a real hang — a genuine hang would fail the same way alone too. Fixing
// it once here, at the root, is the honest alternative to bumping one more random test's timeout
// each time a different one draws the short straw. Picked up automatically by the Angular CLI's
// Vitest-based `ng test` builder (`runnerConfig: true` in angular.json) — see
// node_modules/@angular/build/.../unit-test/runners/vitest/configuration.js for the exact lookup.
export default defineConfig({
  test: {
    testTimeout: 15000,
  },
});
