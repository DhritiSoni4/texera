import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserSqlComponent } from './user-sql.component';
import { Router } from '@angular/router';

class RouterStub {
  navigate = jasmine.createSpy('navigate');
}

describe('UserSqlComponent', () => {
  let component: UserSqlComponent;
  let fixture: ComponentFixture<UserSqlComponent>;
  let router: RouterStub;

  beforeEach(async () => {
    router = new RouterStub();

    await TestBed.configureTestingModule({
      declarations: [UserSqlComponent],
      providers: [{ provide: Router, useValue: router }]
    }).compileComponents();

    fixture = TestBed.createComponent(UserSqlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });


});
