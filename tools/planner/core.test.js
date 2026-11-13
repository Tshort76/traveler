const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const C = require('./core');
const fixture = () => JSON.parse(fs.readFileSync(require('node:path').join(__dirname, '../../schema/examples/demo.trip.json')));

test('airport gaps become unbooked rows and filling them again does not duplicate them', () => {
  const t = fixture(), before = C.coverage(t).filter(x => !x.row).length;
  assert.ok(before > 0);
  assert.equal(C.addAirportJourneys(t), before);
  assert.ok(C.coverage(t).every(x => x.row));
  assert.equal(C.addAirportJourneys(t), 0);
  const arrival = C.coverage(t).find(x => x.flight.id === 'fly-out' && x.journey === 'arrival');
  assert.equal(arrival.row.date, '2027-10-18');
  assert.equal(arrival.row.status, 'research');
  assert.equal(t.transfers.find(f => f.id === 'fly-out').booking.status, 'booked');
});

test('manual transfer and research export as a commitment plus portable planning data', () => {
  const p = C.openProject(fixture());
  const key = C.add(p.trip, {type: 'transfer', title: 'Taxi from airport', date: '2027-10-18', bookBy: '2027-10-17', stayId: 'palermo', status: 'resolved', critical: true, nextAction: 'Use the stand', fields: {Pickup: 'Terminal'}, options: [{name: 'Taxi', estimate: 'Unknown'}]});
  const exported = C.exportTrip(p), row = C.rows(exported).find(r => r.key === key);
  assert.equal(row.status, 'resolved');
  assert.equal(row.bookBy, '2027-10-17');
  assert.equal(exported.commitments.find(c => key.endsWith(c.id)).booked, false);
  assert.deepEqual(row.fields, {Pickup: 'Terminal'});
  assert.equal(row.options[0].name, 'Taxi');
  assert.equal(exported.revision, p.trip.revision + 1);
  assert.equal(exported.validated, undefined);
});

test('phone booking facts combine with independent desktop research, with defaults omitted', () => {
  const p = C.openProject(fixture()); C.addAirportJourneys(p.trip);
  const baseline = C.exportTrip(p), local = C.clone(baseline), phone = C.clone(baseline);
  const target = local.planning.find(p => p.flightId);
  target.options = [{name: 'Shuttle', notes: 'Compare luggage allowance'}];
  const c = phone.commitments.find(c => 'commitment:' + c.id === target.id);
  c.booked = true; c.ref = 'DEMO-CONFIRMATION';
  for (const p of phone.planning) { if (p.status === 'research') delete p.status; if (p.critical) delete p.critical; }
  const result = C.merge(baseline, local, phone);
  assert.deepEqual(result.conflicts, []);
  assert.equal(result.trip.commitments.find(x => x.id === c.id).ref, 'DEMO-CONFIRMATION');
  assert.equal(result.trip.planning.find(x => x.id === target.id).options[0].name, 'Shuttle');
});

test('concurrent edits and delete-versus-edit require an explicit conflict choice', () => {
  const b = fixture(), l = C.clone(b), p = C.clone(b);
  l.transfers[0].depart = '20:00'; p.transfers[0].depart = '21:00';
  let r = C.merge(b, l, p);
  assert.equal(r.conflicts.length, 1); assert.equal(r.trip.transfers[0].depart, '20:00');
  r = C.merge(b, l, p, {[r.conflicts[0].path]: 'phone'});
  assert.equal(r.trip.transfers[0].depart, '21:00');
  l.transfers.shift(); r = C.merge(b, l, p);
  assert.equal(r.conflicts.length, 1);
  assert.ok(!r.trip.transfers.some(t => t.id === b.transfers[0].id));
  assert.throws(() => C.merge(b, l, {...p, id: 'wrong-trip'}), /different trip/);
});

test('booking an activity creates one linked commitment and clearing a confirmation really removes it', () => {
  const t = fixture(), row = C.rows(t).find(r => r.type === 'event' && r.status !== 'booked');
  assert.ok(row);
  C.edit(t, row.key, {...row, date: t.stays.find(s => s.id === row.stayId).arrive, status: 'booked', ref: 'DEMO'});
  assert.equal(C.rows(t).filter(r => r.key === row.key).length, 1);
  assert.equal(C.rows(t).find(r => r.key === row.key).status, 'booked');
  const c = t.commitments.find(c => c.activityId === row.key.slice(9));
  C.edit(t, row.key, {...C.rows(t).find(r => r.key === row.key), ref: ''});
  assert.equal(t.commitments.find(x => x.id === c.id).ref, undefined);
  assert.equal(t.commitments.filter(x => x.activityId === row.key.slice(9)).length, 1);
});

test('editing lodging preserves its imported check-in and check-out times', () => {
  const t = fixture(), r = C.rows(t).find(r => r.key === 'lodging:palermo');
  assert.equal(r.start, '15:00'); assert.equal(r.end, '11:00');
  C.edit(t, r.key, {...r, nextAction: 'Check entry instructions'});
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.checkIn, '15:00');
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.checkOut, '11:00');
});

test('row status changes preserve reservation details and do not invent a lodging name', () => {
  const t = fixture(), original = C.clone(t.transfers[0]);
  C.setStatus(t, 'transfer:' + original.id, 'resolved');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'resolved');
  assert.deepEqual(t.transfers[0].booking.price, original.booking.price);
  assert.equal(t.transfers[0].booking.ref, original.booking.ref);
  C.setStatus(t, 'transfer:' + original.id, 'booked');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'booked');
  C.setStatus(t, 'transfer:' + original.id, 'research');
  assert.equal(C.rows(t).find(r => r.key === 'transfer:' + original.id).status, 'research');
  const s = t.stays.find(s => s.id === 'madryn'); delete s.lodging.name;
  const options = C.clone(s.lodging.options);
  C.setStatus(t, 'lodging:madryn', 'resolved');
  assert.equal(s.lodging.name, undefined);
  assert.deepEqual(s.lodging.options, options);
});

test('an older phone export cannot silently discard research', () => {
  const b = fixture(), p = C.clone(b); delete p.planning;
  assert.throws(() => C.merge(b, b, p), /omitted planning details/);
  assert.throws(() => C.merge({...b, revision: 2}, {...b, revision: 2}, b), /predates/);
});

test('flight edits write native airports, times, cost and confirmation and remove stale coordinates', () => {
  const p = C.openProject(fixture()), row = C.rows(p.trip).find(r => r.key === 'transfer:fly-south');
  C.edit(p.trip, row.key, {...row, status: 'booked', start: '09:10', end: '11:25', ref: 'DEMO-FLIGHT', price: {amount: 245, currency: 'USD'}, sourceNotes: 'Demo airline flight 123; one checked bag.', fields: {...row.fields, 'Departure airport': 'Different departure airport', 'Arrival airport': 'Different arrival airport'}});
  const f = C.exportTrip(p).transfers.find(t => t.id === 'fly-south');
  assert.equal(f.depart, '09:10'); assert.equal(f.arrive, '11:25');
  assert.equal(f.fromPlace.name, 'Different departure airport'); assert.equal(f.toPlace.name, 'Different arrival airport');
  assert.equal(f.fromPlace.lat, undefined); assert.equal(f.toPlace.lng, undefined);
  assert.equal(f.booking.ref, 'DEMO-FLIGHT'); assert.equal(f.booking.price.amount, 245);
  assert.equal(f.details, 'Demo airline flight 123; one checked bag.');
});

test('unchanged native places keep their coordinates while lodging and old ground bookings accept real details', () => {
  const t = fixture(), flight = t.transfers.find(f => f.id === 'fly-south'), before = C.clone(flight.fromPlace);
  const r = C.rows(t).find(r => r.key === 'transfer:fly-south'); C.edit(t, r.key, {...r});
  assert.deepEqual(flight.fromPlace, before);
  const room = C.rows(t).find(r => r.key === 'lodging:palermo');
  C.edit(t, room.key, {...room, fields: {...room.fields, Address: '123 Demo Street'}, sourceNotes: 'Entry code in confirmation.'});
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.place.query, '123 Demo Street');
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.place.address, '123 Demo Street');
  assert.equal(t.stays.find(s => s.id === 'palermo').lodging.notes, 'Entry code in confirmation.');
  const ground = C.rows(t).find(r => r.key === 'commitment:demo-airport-taxi');
  C.edit(t, ground.key, {...ground, mode: 'taxi', start: '14:30', ref: 'DEMO-TAXI', fields: {...ground.fields, Pickup: 'Arrival hall'}, sourceNotes: 'Driver meets at the exit.'});
  const c = t.commitments.find(c => c.id === 'demo-airport-taxi');
  assert.equal(c.start, '14:30'); assert.equal(c.ref, 'DEMO-TAXI');
  assert.equal(c.place.name, 'Arrival hall'); assert.equal(c.notes, 'Driver meets at the exit.');
});

test('lodging address fields prefer the address over the property name and preserve unchanged coordinates', () => {
  const t = fixture(), s = t.stays.find(s => s.id === 'palermo');
  s.lodging.place = {name: 'Demo property', address: '123 Demo Street', query: '123 Demo Street', lat: -34.5, lng: -58.4};
  const before = C.clone(s.lodging.place), r = C.rows(t).find(r => r.key === 'lodging:palermo');
  assert.equal(r.fields.Address, '123 Demo Street');
  C.edit(t, r.key, {...r}); assert.deepEqual(s.lodging.place, before);
});

test('short method notes set transfer mode without replacing existing travel details', () => {
  const t = fixture(), f = t.transfers.find(f => f.id === 'taxi-across'), details = f.details;
  C.setNote(t, 'transfer:taxi-across', 'shuttle');
  assert.equal(f.mode, 'bus'); assert.equal(f.details, details);
  const r = C.rows(t).find(r => r.key === 'transfer:taxi-across');
  assert.equal(r.shortNote, 'shuttle'); assert.equal(r.mode, 'shuttle');
  C.setNote(t, 'transfer:taxi-across', 'Ask hotel about pickup');
  assert.equal(f.mode, 'bus'); assert.equal(f.details, details);
});

test('legacy airport notes are promoted on export but do not replace newer native phone edits', () => {
  const p = C.openProject(fixture()), f = p.trip.transfers.find(f => f.id === 'fly-south');
  delete f.fromPlace;
  const meta = p.trip.planning.find(p => p.id === 'transfer:fly-south');
  meta.fields['Departure airport'] = 'Legacy departure airport';
  meta.fields['Arrival airport'] = 'Stale arrival airport';
  const out = C.exportTrip(p), saved = out.transfers.find(f => f.id === 'fly-south');
  assert.equal(saved.fromPlace.name, 'Legacy departure airport');
  assert.equal(saved.toPlace.name, f.toPlace.name);
  assert.equal(out.planning.find(p => p.id === meta.id).fields['Arrival airport'], undefined);
  assert.equal(p.trip.transfers.find(f => f.id === 'fly-south').fromPlace, undefined);
});
