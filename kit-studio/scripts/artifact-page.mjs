// Turns dist/index.html into a page body for the hosted preview (the host adds
// its own <html>/<head>/<body> skeleton).
import { readFileSync, writeFileSync } from 'node:fs';
const html = readFileSync('dist/index.html', 'utf8');
const head = html.match(/<head>([\s\S]*?)<\/head>/)[1]
  .replace(/<meta charset[^>]*>\s*/i, '')
  .replace(/<meta name="viewport"[^>]*>\s*/i, '');
const body = html.match(/<body>([\s\S]*?)<\/body>/)[1];
writeFileSync('dist/kit-studio.html', `${head.trim()}\n${body.trim()}\n`);
writeFileSync('dist/garments.b64.txt', readFileSync('dist/garments.bin').toString('base64'));
console.log('wrote dist/kit-studio.html and dist/garments.b64.txt');
