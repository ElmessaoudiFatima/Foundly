import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the Foundly landing page', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('Foundly');
    expect(compiled.querySelector('h1')?.textContent).toContain('Créez votre entreprise');
  });

  it('should update the simulator for the selected legal form', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.selectedForm = 'sa';
    app.selectedRegion = 'casa';
    app.runSimulation();

    expect(app.simulation.delay).toBe('15 – 30 jours ouvrés');
    expect(app.simulation.steps).toBe('10 étapes');
  });

  it('should toggle FAQ answers', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.toggleFaq(0);
    expect(app.openFaq).toBe(0);
    app.toggleFaq(0);
    expect(app.openFaq).toBeNull();
  });
});
