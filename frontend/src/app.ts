import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DatePipe, JsonPipe } from '@angular/common';
interface Event { id: string; type: string; data: Record<string, unknown>; }
interface Receipt { receivedAt: string; event: Event; message: string; }
interface Delivery { url: string; timestamp: string; signature: string; body: string; attempts: {number: number; httpStatus: number; response: string}[]; }
@Component({
  selector: 'app-root', standalone: true, imports: [FormsModule, DatePipe, JsonPipe], templateUrl: './app.html'
})
export class AppComponent {
  private http = inject(HttpClient);
  types = ['order.created', 'payment.succeeded', 'shipment.delivered'];
  type = this.types[0]; id = crypto.randomUUID(); payload = '';
  invalidSignature = false; failFirst = false; autoRetry = true;
  busy = signal(false); error = signal(''); historyError = signal(''); receipts = signal<Receipt[]>([]); delivery = signal<Delivery | null>(null);
  constructor() { this.example(); this.refresh(); }
  example() {
    const examples: Record<string, unknown> = {
      'order.created': { orderId: 'ORD-1042', customer: 'Alex', total: 49.95 },
      'payment.succeeded': { paymentId: 'PAY-208', orderId: 'ORD-1042', amount: 49.95, currency: 'USD' },
      'shipment.delivered': { trackingNumber: 'SHIP-392', orderId: 'ORD-1042', deliveredTo: 'Front door' }
    };
    this.payload = JSON.stringify(examples[this.type], null, 2); this.newId();
  }
  newId() { this.id = crypto.randomUUID(); }
  refresh() {
    this.http.get<Receipt[]>('/api/events').subscribe({ next: rows => { this.receipts.set(rows); this.historyError.set(''); }, error: () => this.historyError.set('Cannot load events. Start the Spring Boot backend on port 8080, then refresh.') });
  }
  send() {
    this.error.set(''); this.delivery.set(null);
    let data: Record<string, unknown>;
    try { data = JSON.parse(this.payload); if (!data || Array.isArray(data) || typeof data !== 'object') throw new Error(); }
    catch { this.error.set('The payload must be a valid JSON object.'); return; }
    if (!this.id.trim() || this.id.length > 100) { this.error.set('Enter an event ID between 1 and 100 characters.'); return; }
    this.busy.set(true);
    this.http.post<Delivery>('/api/simulate', {event: {id: this.id, type: this.type, data}, invalidSignature: this.invalidSignature, failFirst: this.failFirst, autoRetry: this.autoRetry}).subscribe({
      next: result => { this.delivery.set(result); this.busy.set(false); this.refresh(); },
      error: (error: HttpErrorResponse) => { this.error.set(error.error?.message ?? 'Delivery failed. Check that the backend is running.'); this.busy.set(false); }
    });
  }
}
