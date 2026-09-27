import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IconComponent } from './icon.component';

describe('IconComponent', () => {
  let component: IconComponent;
  let fixture: ComponentFixture<IconComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(IconComponent);
    component = fixture.componentInstance;
  });

  it('should create the icon component', () => {
    expect(component).toBeTruthy();
  });

  it('should set default properties', () => {
    expect(component.size).toBe(18);
    expect(component.strokeWidth).toBe(2);
    expect(component.className).toBe('');
  });

  it('should render SVG element with correct size attributes', () => {
    component.name = 'server';
    component.size = 24;
    fixture.detectChanges();

    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg).toBeTruthy();
    expect(svg.getAttribute('width')).toBe('24');
    expect(svg.getAttribute('height')).toBe('24');
  });

  it('should render icon paths for known icon name', () => {
    component.name = 'dashboard';
    fixture.detectChanges();

    const rects = fixture.nativeElement.querySelectorAll('rect');
    expect(rects.length).toBeGreaterThan(0);
  });
});
