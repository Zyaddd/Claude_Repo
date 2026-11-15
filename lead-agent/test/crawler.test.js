import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { extract, crawlSite, normalizePhone } from '../src/crawler.js';

const HOME = `<!doctype html><html><head><title>Smile Dental Clinic | Jeddah</title>
<meta name="description" content="Family dentistry in Jeddah">
<script type="application/ld+json">{"@type":"Dentist","telephone":"+966 12 555 0101","sameAs":["https://www.instagram.com/smiledental/"]}</script>
<script src="https://embed.tawk.to/abc/default"></script></head><body>
<a href="mailto:Info@SmileDental.example.org?subject=hi">Email us</a>
<a href="tel:+966-12-555-0199">Call</a>
<a href="https://www.facebook.com/SmileDentalJeddah">fb</a>
<a href="https://www.facebook.com/sharer/sharer.php?u=x">share</a>
<a href="https://wa.me/966500000000">WhatsApp</a>
<a href="https://twitter.com/intent/tweet?text=x">tweet</a>
<a href="https://www.instagram.com/p/ABC123/">post</a>
<a href="/contact-us">Contact</a><a href="/services">Services</a>
<img src="logo@2x.png">
<p>Bookings: reception [at] smiledental [dot] example [dot] org</p>
</body></html>`;
const CONTACT = `<html><body><p>Call our branch on +966 12 555 0222 or write to bookings@smiledental.example.org</p>
<a href="https://www.tiktok.com/@smiledental">TikTok</a><a href="https://calendly.com/smile">Book</a></body></html>`;

test('extracts contacts and ignores junk', () => {
  const ex = extract(HOME, 'https://smiledental.example.org/');
  assert.ok(ex.emails.includes('info@smiledental.example.org'));
  assert.ok(ex.emails.includes('reception@smiledental.example.org'), 'de-obfuscates [at]/[dot]');
  assert.ok(!ex.emails.some((e) => e.includes('2x.png')));
  assert.ok(ex.phones.includes('+966125550199'));
  assert.ok(ex.phones.includes('+966125550101'), 'reads JSON-LD telephone');
  assert.equal(ex.socials.facebook, 'https://www.facebook.com/SmileDentalJeddah');
  assert.equal(ex.socials.instagram, 'https://www.instagram.com/smiledental/');
  assert.equal(ex.socials.whatsapp, 'https://wa.me/966500000000');
  assert.equal(ex.socials.twitter, undefined, 'share intents are not profiles');
  assert.deepEqual(ex.chatWidgets, ['Tawk', 'WhatsApp widget']);
  assert.equal(ex.title, 'Smile Dental Clinic | Jeddah');
});

test('normalizes phones', () => {
  assert.equal(normalizePhone('tel:0096612 555 0101'), '+966125550101');
  assert.equal(normalizePhone('12'), null);
});

test('crawls home + contact page of a live site', async () => {
  process.env.ALLOW_PRIVATE_URLS = '1';
  const srv = http.createServer((req, res) => {
    if (req.url === '/') { res.writeHead(302, { Location: '/home' }); return res.end(); }
    res.writeHead(req.url === '/home' || req.url === '/contact-us' ? 200 : 404, { 'Content-Type': 'text/html' });
    res.end(req.url === '/home' ? HOME : req.url === '/contact-us' ? CONTACT : 'nope');
  });
  await new Promise((r) => srv.listen(0, r));
  try {
    const r = await crawlSite(`http://127.0.0.1:${srv.address().port}/`);
    assert.equal(r.error, null);
    assert.equal(r.pages.length, 2);
    assert.ok(r.emails.includes('bookings@smiledental.example.org'));
    assert.ok(r.phones.includes('+966125550222'));
    assert.equal(r.socials.tiktok, 'https://www.tiktok.com/@smiledental');
    assert.deepEqual(r.bookingTools, ['Calendly']);
  } finally {
    srv.close();
    delete process.env.ALLOW_PRIVATE_URLS;
  }
});

test('refuses private addresses by default', async () => {
  const r = await crawlSite('http://127.0.0.1:9/');
  assert.match(r.error, /private/);
});
