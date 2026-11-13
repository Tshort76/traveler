/* Shared by the standalone planner and its Node tests. No network or browser storage. */
(() => {
  const clone = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
  const types = ['flight', 'transfer', 'lodging', 'event'];
  const modes = {bus: 'bus', train: 'train', taxi: 'taxi', shuttle: 'bus', car: 'car', boat: 'boat', ferry: 'ferry', hike: 'hike', other: 'other'};
  const placeText = p => p?.name || p?.address || p?.query || '';
  const updatePlace = (value, old, address = false) => { const text = (value || '').trim(), previous = address ? old?.address || old?.query || old?.name || '' : placeText(old); return text === previous ? clone(old) : text ? {...(address ? {address: text} : {name: text}), query: text} : undefined; };
  const statuses = ['research', 'ready', 'booked', 'resolved'];
  const dateOK = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s;
  const shift = (s, days) => { if (!dateOK(s)) return ''; const d = new Date(s); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); };
  const dayDiff = (a, b) => Math.round((Date.parse(a) - Date.parse(b)) / 86400000);
  const uid = () => 'planner-' + (globalThis.crypto?.randomUUID?.() || Date.now().toString(36) + Math.random().toString(36).slice(2));
  function validateTrip(t) {
    if (!t || t.format !== 'traveler-trip' || t.formatVersion !== 1 || !t.id || !t.title || !dateOK(t.startDate) || !dateOK(t.endDate) || !Array.isArray(t.stays) || !t.stays.length) throw Error('Choose a Traveler version 1 trip file.');
    for (const k of ['stays', 'transfers', 'activities', 'commitments', 'days', 'planning']) {
      if (t[k] !== undefined && !Array.isArray(t[k])) throw Error(k + ' must be a list.');
      const ids = new Set();
      for (const e of t[k] || []) {
        const id = k === 'days' ? e?.date : e?.id;
        if (typeof id !== 'string' || !id || ids.has(id)) throw Error('Missing or duplicate ID in ' + k);
        ids.add(id);
      }
    }
    for (const p of t.planning || []) {
      if (!types.includes(p.type) || !['research', 'ready', 'resolved'].includes(p.status || 'research') || !/^(lodging|transfer|activity|commitment):.+$/.test(p.id)) throw Error('Invalid planning entry.');
      if (p.options && (!Array.isArray(p.options) || p.options.some(o => !o || typeof o.name !== 'string'))) throw Error('Invalid research options.');
      if (p.fields && (typeof p.fields !== 'object' || Array.isArray(p.fields) || Object.values(p.fields).some(v => typeof v !== 'string'))) throw Error('Invalid planning fields.');
    }
    return t;
  }
  function openProject(value) {
    if (value?.format === 'traveler-trip') return {format: 'traveler-planner', formatVersion: 1, trip: clone(validateTrip(value)), lastExchange: clone(value)};
    if (value?.format !== 'traveler-planner' || value.formatVersion !== 1) throw Error('Choose a Traveler trip or planner project JSON.');
    validateTrip(value.trip);
    if (value.lastExchange) { validateTrip(value.lastExchange); if (value.lastExchange.id !== value.trip.id) throw Error('Exchange baseline belongs to a different trip.'); }
    return clone(value);
  }
  const stay = (t, id) => t.stays.find(s => s.id === id);
  function rows(t) {
    const out = [], details = new Map((t.planning || []).map(p => [p.id, p]));
    function add(key, type, title, date, stayId, booking, extra = {}) {
      const p = details.get(key) || {}, booked = booking?.status === 'booked';
      out.push({key, type: p.type || type, title: p.title || title, date: date || '', stayId: p.stayId || stayId || '', city: stay(t, p.stayId || stayId)?.name || 'Unassigned', bookBy: booking?.bookDaysBefore != null && date ? shift(date, -booking.bookDaysBefore) : '', status: booked ? 'booked' : p.status || 'research', ref: booking?.ref || '', url: booking?.url || '', price: clone(booking?.price), how: booking?.how || '', nextAction: p.nextAction || '', notes: p.notes || '', fields: {...clone(extra.defaultFields || {}), ...clone(p.fields || {}), ...clone(extra.nativeFields || {})}, shortNote: p.fields?.['Short note'] || '', options: clone(p.options || extra.defaultOptions || []), critical: p.critical ?? type !== 'event', flightId: p.flightId || '', journey: p.journey || '', ...extra, mode: p.fields?.Method && (!extra.mode || modes[p.fields.Method] === extra.mode) ? p.fields.Method : extra.mode || ''});
    }
    for (const s of t.stays) if (s.arrive !== s.depart) add('lodging:' + s.id, 'lodging', s.lodging?.name || 'Lodging in ' + s.name, s.arrive, s.id, s.lodging, {defaultFields: {'Address': s.lodging?.place?.address || s.lodging?.place?.query || ''}, defaultOptions: (s.lodging?.options || []).map(o => ({name: o.name, url: o.url || '', estimate: o.price ? [o.price.amount + (o.price.max ? '–' + o.price.max : ''), o.price.currency || 'USD', o.price.unit || 'total'].join(' ') : '', notes: [o.type, o.room, o.area, o.rating ? o.rating.score + ' ' + o.rating.source : '', o.pro, o.con].filter(Boolean).join(' · ')})), endDate: s.depart, start: s.lodging?.checkIn || '', end: s.lodging?.checkOut || '', sourceNotes: s.lodging?.notes || '', nativeFields: s.lodging?.place ? {Address: s.lodging.place.address || s.lodging.place.query || s.lodging.place.name || ''} : {}});
    for (const f of t.transfers || []) if (f.booking?.status !== 'onsite') add('transfer:' + f.id, f.mode === 'flight' ? 'flight' : 'transfer', (stay(t, f.from)?.name || f.from) + ' → ' + (stay(t, f.to)?.name || f.to), f.date, stay(t, f.to)?.arrive !== stay(t, f.to)?.depart ? f.to : f.from, f.booking, {defaultFields: f.mode === 'flight' ? {'Departure airport': f.fromPlace?.name || '', 'Arrival airport': f.toPlace?.name || ''} : {'Pickup': f.fromPlace?.name || '', 'Drop-off': f.toPlace?.name || ''}, start: f.depart || '', end: f.arrive || '', from: f.from, to: f.to, fromPlace: placeText(f.fromPlace), toPlace: placeText(f.toPlace), mode: f.mode || '', sourceNotes: f.details || '', nativeFields: {...(f.fromPlace ? {[f.mode === 'flight' ? 'Departure airport' : 'Pickup']: placeText(f.fromPlace)} : {}), ...(f.toPlace ? {[f.mode === 'flight' ? 'Arrival airport' : 'Drop-off']: placeText(f.toPlace)} : {})}});
    const folded = new Set();
    for (const a of t.activities || []) if (a.booking && a.booking.status !== 'onsite') {
      const c = (t.commitments || []).find(c => c.activityId === a.id && c.booked);
      const d = (t.days || []).find(d => (d.plan || []).some(p => p.activityId === a.id));
      add('activity:' + a.id, 'event', c?.title || a.name, c?.date || d?.date, a.stayId, c ? {...a.booking, ...c, status: 'booked'} : a.booking, {start: c?.start || '', end: c?.end || '', commitmentId: c?.id, sourceNotes: c?.notes || a.practical?.booking || '', nativeFields: (c?.place || a.place) ? {'Venue / operator': placeText(c?.place || a.place)} : {}});
      folded.add(a.id);
    }
    for (const c of t.commitments || []) if ((c.kind === 'booking' || c.booked) && !folded.has(c.activityId)) add('commitment:' + c.id, 'event', c.title, c.date, c.stayId, {...c, status: c.booked ? 'booked' : 'needed'}, {start: c.start || '', end: c.end || '', sourceNotes: c.notes || '', nativeFields: c.place ? {[details.get('commitment:' + c.id)?.type === 'transfer' ? 'Pickup' : 'Venue / operator']: placeText(c.place)} : {}});
    return out;
  }
  function planning(t, row) {
    t.planning ||= [];
    let p = t.planning.find(p => p.id === row.key);
    if (!p) { p = {id: row.key, type: row.type}; t.planning.push(p); }
    return p;
  }
  function validateEdit(v) {
    if (!types.includes(v.type) || !statuses.includes(v.status) || !v.title.trim()) throw Error('Choose a type, title and status.');
    if (v.type === 'transfer' && v.mode && !Object.hasOwn(modes, v.mode)) throw Error('Choose a valid transfer method.');
    if (v.date && !dateOK(v.date)) throw Error('Travel date is invalid.');
    if (v.bookBy && (!dateOK(v.bookBy) || !v.date || v.bookBy > v.date)) throw Error('Book-by date needs a travel date and cannot follow it.');
    for (const k of ['start', 'end']) if (v[k] && !/^([01]\d|2[0-3]):[0-5]\d$/.test(v[k])) throw Error('Use 24-hour times.');
    for (const url of [v.url, ...(v.options || []).map(o => o.url)]) if (url && !/^https?:\/\//i.test(url)) throw Error('Links must begin with https:// or http://.');
    if (v.price?.unit === 'night' && v.type !== 'lodging') throw Error('Per-night prices are only for lodging.');
    if (v.price && (!Number.isFinite(v.price.amount) || v.price.amount < 0)) throw Error('Cost must be a nonnegative number.');
  }
  function edit(t, key, v) {
    validateEdit(v);
    if (v.stayId && !stay(t, v.stayId)) throw Error('Choose a stay from this trip.');
    if (v.flightId && !(t.transfers || []).some(f => f.id === v.flightId && f.mode === 'flight')) throw Error('Linked flight no longer exists.');
    const row = rows(t).find(r => r.key === key); if (!row) throw Error('Arrangement no longer exists.');
    const [kind, id] = [key.slice(0, key.indexOf(':')), key.slice(key.indexOf(':') + 1)];
    const p = planning(t, row);
    Object.assign(p, {type: v.type, title: kind === 'transfer' ? v.title.trim() : undefined, status: v.status === 'booked' ? 'research' : v.status, stayId: v.stayId || undefined, critical: v.critical, nextAction: v.nextAction || undefined, notes: v.notes || undefined, fields: clone(v.fields || {}), options: clone(v.options || []), flightId: v.flightId || undefined, journey: v.journey || undefined});
    if (v.shortNote) p.fields['Short note'] = v.shortNote.trim(); else delete p.fields['Short note'];
    if (v.type === 'transfer' && v.mode) p.fields.Method = v.mode; else delete p.fields.Method;
    function booking(b) {
      const out = {...b, status: v.status === 'booked' ? 'booked' : 'needed'};
      for (const k of ['ref', 'url']) if (v[k]) out[k] = v[k]; else delete out[k];
      if (v.bookBy) out.bookDaysBefore = dayDiff(v.date, v.bookBy); else { delete out.bookDaysBefore; delete out.priority; }
      if (v.price) out.price = clone(v.price); else delete out.price;
      return out;
    }
    function mark(o, fields) { o.userEdited = [...new Set([...(o.userEdited || []), ...fields])]; }
    if (kind === 'lodging') {
      const s = stay(t, id);
      // Stay dates are itinerary boundaries, not just booking fields.
      if (v.date !== s.arrive) throw Error('Change the stay dates in the itinerary before changing lodging check-in.');
      s.lodging = {...booking(s.lodging), name: !s.lodging?.name && v.title.trim() === row.title ? undefined : v.title.trim(), checkIn: v.start || undefined, checkOut: v.end || undefined, notes: v.sourceNotes || undefined, place: updatePlace(v.fields?.Address, s.lodging?.place, true)}; delete p.fields.Address; mark(s, ['lodging']);
    } else if (kind === 'transfer') {
      const f = t.transfers.find(f => f.id === id); if (!v.date) throw Error('A transfer needs a travel date.');
      f.date = v.date; f.depart = v.start || undefined; f.arrive = v.end || undefined; f.booking = booking(f.booking);
      const fromLabel = v.type === 'flight' ? 'Departure airport' : 'Pickup', toLabel = v.type === 'flight' ? 'Arrival airport' : 'Drop-off';
      f.fromPlace = updatePlace(v.fields?.[fromLabel], f.fromPlace); f.toPlace = updatePlace(v.fields?.[toLabel], f.toPlace);
      delete p.fields[fromLabel]; delete p.fields[toLabel];
      if (v.type === 'transfer') f.mode = modes[v.mode] || undefined;
      f.details = v.sourceNotes || undefined;
    } else if (kind === 'commitment') {
      const c = t.commitments.find(c => c.id === id); if (!v.date) throw Error('An arrangement needs a travel date.');
      const b = booking(c); delete b.status;
      Object.assign(c, b, {title: v.title.trim(), date: v.date, stayId: v.stayId || undefined, start: v.start || undefined, end: v.end || undefined, booked: v.status === 'booked', notes: v.sourceNotes || undefined, place: updatePlace(v.fields?.[v.type === 'transfer' ? 'Pickup' : 'Venue / operator'], c.place)});
      delete p.fields[v.type === 'transfer' ? 'Pickup' : 'Venue / operator'];
      // Object.assign cannot remove optional fields cleared in the form.
      for (const k of ['ref', 'url', 'price', 'bookDaysBefore', 'priority']) if (!(k in b)) delete c[k];
      mark(c, ['title', 'date', 'stayId', 'start', 'end', 'booked', 'ref', 'url', 'price', 'bookDaysBefore', 'notes', 'place']);
    } else {
      const a = t.activities.find(a => a.id === id); a.name = v.title.trim(); a.booking = booking(a.booking); a.place = updatePlace(v.fields?.['Venue / operator'], a.place); delete p.fields['Venue / operator']; mark(a, ['name', 'booking', 'place']);
      const linked = (t.commitments || []).filter(c => c.activityId === id);
      if (v.status !== 'booked') { a.practical = {...a.practical, booking: v.sourceNotes || undefined}; mark(a, ['practical']); }
      for (const c of linked) { c.booked = v.status === 'booked'; mark(c, ['booked']); }
      if (v.status === 'booked') {
        if (!v.date) throw Error('A booked activity needs a date.');
        let c = linked[0]; if (!c) { c = {id: uid(), kind: 'booking', activityId: id, origin: 'user'}; (t.commitments ||= []).push(c); }
        Object.assign(c, {title: v.title, date: v.date, stayId: v.stayId || a.stayId, start: v.start || undefined, end: v.end || undefined, ref: v.ref || undefined, url: v.url || undefined, price: clone(v.price), notes: v.sourceNotes || undefined, place: clone(a.place), booked: true});
      } else if (v.date !== row.date) {
        if (v.date) {
          let d = (t.days || []).find(d => d.date === v.date);
          if (!d) { d = {date: v.date, stayId: v.stayId || a.stayId, plan: []}; (t.days ||= []).push(d); }
          if (!(d.plan || []).some(x => x.activityId === id)) { (d.plan ||= []).push({activityId: id, slot: 'morning'}); mark(d, ['plan']); }
        }
        for (const d of t.days || []) if (d.date !== v.date && (d.plan || []).some(x => x.activityId === id)) { d.plan = d.plan.filter(x => x.activityId !== id); mark(d, ['plan']); }
      }
    }
    delete t.validated;
    return key;
  }
  function setNote(t, key, text) {
    const row = rows(t).find(r => r.key === key); if (!row) throw Error('Arrangement no longer exists.');
    const p = planning(t, row); p.fields ||= {};
    const note = String(text).trim();
    if (note) p.fields['Short note'] = note; else delete p.fields['Short note'];
    const method = note.toLowerCase();
    if (row.type === 'transfer' && Object.hasOwn(modes, method)) {
      p.fields.Method = method;
      if (key.startsWith('transfer:')) t.transfers.find(f => 'transfer:' + f.id === key).mode = modes[method];
    }
    delete t.validated;
  }
  function setStatus(t, key, status) {
    if (!statuses.includes(status)) throw Error('Choose a valid status.');
    const row = rows(t).find(r => r.key === key);
    if (!row) throw Error('Arrangement no longer exists.');
    if (key.startsWith('activity:')) return edit(t, key, {...row, status});
    const p = planning(t, row);
    p.status = status === 'booked' ? 'research' : status;
    const [kind, id] = key.split(':');
    if (kind === 'lodging') {
      const s = stay(t, id);
      (s.lodging ||= {}).status = status === 'booked' ? 'booked' : 'needed';
      s.userEdited = [...new Set([...(s.userEdited || []), 'lodging'])];
    } else if (kind === 'transfer') {
      const f = t.transfers.find(f => f.id === id);
      (f.booking ||= {}).status = status === 'booked' ? 'booked' : 'needed';
    } else {
      const c = t.commitments.find(c => c.id === id);
      c.booked = status === 'booked';
      c.userEdited = [...new Set([...(c.userEdited || []), 'booked'])];
    }
    delete t.validated;
    return key;
  }
  function add(t, v) {
    validateEdit(v); if (!v.date) throw Error('Choose a travel date.');
    const id = uid(); let key;
    if (v.type === 'lodging') {
      const s = stay(t, v.stayId); if (!s || s.arrive === s.depart) throw Error('Choose an overnight stay.');
      key = 'lodging:' + s.id;
      if (s.lodging?.name || s.lodging?.status === 'booked') throw Error('This stay already has lodging. Select its existing row to edit it.');
      v.date = s.arrive;
    } else if (v.type === 'flight') {
      if (!stay(t, v.from) || !stay(t, v.to) || v.from === v.to) throw Error('Choose distinct departure and destination stays.');
      (t.transfers ||= []).push({id, from: v.from, to: v.to, date: v.date, mode: 'flight'}); key = 'transfer:' + id;
    } else {
      (t.commitments ||= []).push({id, title: v.title, date: v.date, stayId: v.stayId || undefined, kind: 'booking', booked: false, origin: 'user'}); key = 'commitment:' + id;
    }
    edit(t, key, v); return key;
  }
  function coverage(t) {
    const list = rows(t);
    return (t.transfers || []).filter(f => f.mode === 'flight').flatMap(f => ['departure', 'arrival'].map(journey => ({flight: f, journey, row: list.find(r => r.flightId === f.id && r.journey === journey)})));
  }
  function addAirportJourneys(t) {
    let count = 0;
    for (const {flight: f, journey, row} of coverage(t)) {
      if (row) continue;
      const arrival = journey === 'arrival', s = stay(t, arrival ? f.to : f.from);
      const airport = (arrival ? f.toPlace : f.fromPlace)?.name || (s?.arrive === s?.depart ? s.name : 'Airport (confirm)');
      const ground = s?.arrive === s?.depart ? (arrival ? 'Destination (confirm)' : 'Starting point (confirm)') : s?.name || (arrival ? f.to : f.from);
      // Overnight boundary stays give an arrival date without guessing it from local clock times.
      const date = arrival && s?.arrive > f.date ? s.arrive : f.date;
      const candidates = (t.transfers || []).filter(x => x.id !== f.id && x.mode !== 'flight' && x.date === date && /airport|aeropuerto/i.test(x.details || '') && (arrival ? x.from === f.to : x.from === f.from && x.to === f.to));
      if (candidates.length === 1) {
        const r = rows(t).find(r => r.key === 'transfer:' + candidates[0].id), p = planning(t, r);
        Object.assign(p, {flightId: f.id, journey, type: 'transfer', stayId: s?.id}); count++; continue;
      }
      const v = {type: 'transfer', title: arrival ? airport + ' → ' + ground : ground + ' → ' + airport, date, bookBy: '', stayId: s?.id || '', status: 'research', critical: true, nextAction: 'Confirm airport, route and whether a reservation is needed.', notes: 'Added for coverage; no transport has been researched or booked.', fields: {'Pickup': arrival ? airport : s?.lodging?.name || '', 'Drop-off': arrival ? s?.lodging?.name || '' : airport}, options: [], flightId: f.id, journey};
      add(t, v); count++;
    }
    return count;
  }
  const clean = (v, key) => {
    if (v === null || (key === 'booked' && v === false) || (key === 'critical' && v === true) || (key === 'status' && v === 'research')) return undefined;
    if (Array.isArray(v)) return v.length ? v.map(x => clean(x)) : undefined;
    if (v && typeof v === 'object') { const o = {}; for (const [k, x] of Object.entries(v).sort(([a], [b]) => a.localeCompare(b))) { const c = clean(x, k); if (c !== undefined) o[k] = c; } return Object.keys(o).length ? o : undefined; }
    return v;
  };
  const eq = (a, b, key) => JSON.stringify(clean(a, key)) === JSON.stringify(clean(b, key));
  function merge(base, local, phone, choices = {}) {
    validateTrip(phone); if (phone.id !== local.id) throw Error('This export belongs to a different trip.');
    if (!base) throw Error('No exchange baseline. Open the phone export as a new project first.');
    if ((phone.revision || 1) < (base.revision || 1)) throw Error('This phone export predates the last desktop export. Import the latest desktop revision on the phone before bringing its edits back.');
    if (base.planning?.length && !phone.planning?.length) throw Error('The phone export omitted planning details. Update Traveler before exchanging this project. Your desktop research has been kept.');
    const conflicts = [];
    function walk(b, l, p, path, key = '') {
      if (eq(l, p, key) || eq(b, p, key)) return clone(l);
      if (eq(b, l, key)) return clone(p);
      if ([b, l, p].every(v => v && typeof v === 'object' && !Array.isArray(v))) {
        const out = {};
        for (const k of new Set([...Object.keys(b), ...Object.keys(l), ...Object.keys(p)])) {
          const v = walk(b[k], l[k], p[k], path + '/' + k, k); if (v !== undefined) out[k] = v;
        }
        return out;
      }
      if ([b, l, p].every(v => v === undefined || Array.isArray(v)) && ['stays', 'transfers', 'activities', 'commitments', 'days', 'planning'].includes(key)) {
        const id = key === 'days' ? 'date' : 'id', maps = [b, l, p].map(v => new Map((v || []).map(x => [x[id], x]))), out = [];
        for (const k of new Set([...maps[1].keys(), ...maps[2].keys(), ...maps[0].keys()])) {
          const v = walk(maps[0].get(k), maps[1].get(k), maps[2].get(k), path + '/' + k); if (v !== undefined) out.push(v);
        }
        return out;
      }
      conflicts.push({path, local: clone(l), phone: clone(p)});
      return clone(choices[path] === 'phone' ? p : l);
    }
    const trip = walk(base, local, phone, 'trip'); delete trip.validated; delete trip.exportedFrom;
    trip.revision = Math.max(local.revision || 1, phone.revision || 1);
    return {trip, conflicts};
  }
  function exportTrip(project) {
    const t = clone(project.trip);
    for (const p of t.planning || []) {
      const fields = p.fields || {};
      if (p.id.startsWith('transfer:')) {
        const f = (t.transfers || []).find(f => 'transfer:' + f.id === p.id); if (!f) continue;
        for (const [label, field] of [[p.type === 'flight' ? 'Departure airport' : 'Pickup', 'fromPlace'], [p.type === 'flight' ? 'Arrival airport' : 'Drop-off', 'toPlace']]) {
          if (!f[field] && fields[label]) f[field] = updatePlace(fields[label]);
          delete fields[label];
        }
      } else if (p.id.startsWith('lodging:')) {
        const s = t.stays.find(s => 'lodging:' + s.id === p.id);
        if (s?.lodging && !s.lodging.place && fields.Address) s.lodging.place = updatePlace(fields.Address, undefined, true);
        delete fields.Address;
      }
    }
    t.revision = Math.max(t.revision || 1, project.lastExchange?.revision || 1) + 1;
    t.generatedAt = new Date().toISOString(); t.generatedBy = 'Traveler desktop planner'; delete t.validated; delete t.exportedFrom; return t;
  }
  const api = {clone, types, statuses, dateOK, shift, validateTrip, openProject, rows, edit, setStatus, setNote, add, coverage, addAirportJourneys, merge, exportTrip};
  globalThis.PlannerCore = api;
  if (typeof module !== 'undefined') module.exports = api;
})();
