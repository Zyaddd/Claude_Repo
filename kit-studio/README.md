# Kit Studio: 3D football shirt customiser

A web app for sports shops that print custom football shirts. Customers pick a kit, type a
name and number, and see it live on a 3D shirt and shorts that they can turn 360°. It works in
mobile and desktop browsers with no app install.

## What it does

- **3D "ghost mannequin" kit.** The shirt and shorts are shaped as if someone is wearing them,
  but hollow: you can see inside the collar, sleeves and legs. It has a ribbed collar and cuffs,
  folded hems, a knit fabric texture, soft folds and a printed size label inside the back neck.
- **360° turntable.** Drag or swipe to spin, pinch or scroll to zoom, double-click to reset.
  It auto-rotates while idle and has Front, Back and Side buttons.
- **Live name and number.** Clicking the name or number field turns the shirt to show the back.
  Every keystroke updates the print. There are four lettering styles, an optional arched name,
  and Arabic names printed right to left. Long names are fitted the way a printer would:
  condensed first, then reduced in height.
- **Upsells.** Player presets, sleeve patches, matching shorts, a number on the shorts, a kids
  size range with its own price, and quantity.
- **Print job sheet.** A printable A4 sheet (or PDF) for printing staff. It shows the name,
  number, lettering style, colours, sizes in cm, placement, front and back renders, the flat
  artwork, signature boxes, and a QR code that reopens the exact design.
- **Sharing.** A link that restores the design, and a branded image of front and back that
  customers can post or save.
- **Shop integration.** "Add to basket" sends the full design to the host page
  (`window.postMessage` with `type: "kitstudio:add-to-basket"`) and fires a DOM event.
  Analytics events go to `window.dataLayer` (`kitstudio_kit_selected`, `kitstudio_add_to_basket`
  and others).
- **White label.** Shop name, accent colour, currency, prices, sizes and blocked words are set
  in `src/config.js`. For sales demos you can also set them in the URL, for example
  `?shop=Jersey%20House&accent=%23ff3d6e&currency=SAR`.

## Run it

```bash
cd kit-studio
npm install
npm run dev        # http://localhost:5173
npm run build      # static site in dist/ — host anywhere (Netlify, S3, shop server…)
```

To embed it in a shop website:

```html
<iframe src="https://your-host/kit-studio/" style="width:100%;height:100vh;border:0" allow="clipboard-write; web-share"></iframe>
<script>
  addEventListener('message', (e) => {
    if (e.data?.type === 'kitstudio:add-to-basket') console.log(e.data.design); // send to cart
  });
</script>
```

## How it is built

| Part | File |
|---|---|
| Garment shape (signed distance fields) | `src/garment/sdf.js`, `src/garment/layout.js` |
| Mesh generation (surface nets, openings, rims, AO) | `src/garment/build.js` → `npm run garments` writes `public/garments.bin` |
| Fabric material (kit projection, live decals, knit relief, hollow interior) | `src/render/materials.js` |
| Scene, lights, turntable | `src/render/scene.js`, `src/render/turntable.js` |
| Kit artwork painter and catalogue | `src/kits/painter.js`, `src/kits/kits.js` |
| Name and number lettering, print specs | `src/kits/lettering.js` |
| UI, job sheet, sharing | `src/main.js`, `src/ui/*` |

The mesh is generated once, at build time. It ships as a compact binary of about 1.6 MB
gzipped, so phones don't have to compute it.

## Adding licensed kits

The included clubs, crests, sponsors and kit makers are **fictional originals**, so the product
can be demonstrated and sold anywhere. Club crests, league marks, sponsor logos and official
name-and-number fonts are trademarks or copyrighted. Only shops licensed to use them (for
example, official retailers) should load them.

Each kit is artwork painted into a texture atlas: front, back and both sleeves, laid out in
`src/garment/layout.js`. Next step (Phase 2): an admin page where a shop uploads front, back
and sleeve images for each kit, plus their licensed lettering fonts. The engine already
renders any artwork placed in those regions.

## Roadmap

- **Phase 2:** admin panel (upload kits, set prices, stock), shop-uploaded fonts, Arabic/RTL
  interface, e-commerce plugins (Shopify and WooCommerce).
- **Phase 3:** AR view on phones, analytics dashboard, multi-shop subscription accounts.
