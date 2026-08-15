import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Sprint 0 skeleton root component. No business features, no API clients,
 * no auth wiring. See 25_CLAUDE_CODE_EXECUTION_GUIDE.md for later sprints.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal('Brika');
}
