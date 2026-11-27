#!/usr/bin/env python3
"""
Generates printable StoneRift cards from the card data.

Every card gets its own generated artwork. The artwork is also what the phone's camera recognises
(ARCore "augmented images"), so it is drawn with lots of sharp, high-contrast, non-repeating detail.
It is a placeholder until real art exists; replacing it later only needs the Unity image library rebuilt.

Outputs:
  stonerift/print/StoneRift-all-cards.pdf      one of each card, 9 per A4 page, with cut marks
  stonerift/print/StoneRift-<deck>.pdf         a full 30-card starter deck, ready to print and play
  stonerift/print/card-back.png                the back design (for double-sided printing)
  stonerift/unity/Assets/StoneRift/CardImages/<id>.jpg   reference images for card scanning in the app

Run: python3 stonerift/tools/gen_cards.py   (needs Pillow built with libraqm for Arabic text)
"""
import hashlib
import json
import math
import pathlib
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "core" / "StoneRift.Core" / "Data"
PRINT = ROOT / "print"
UNITY_IMAGES = ROOT / "unity" / "Assets" / "StoneRift" / "CardImages"
FONT_BOLD = str(PRINT / "fonts" / "noto_kufi_semibold.ttf")
FONT_TEXT = str(PRINT / "fonts" / "noto_kufi_regular.ttf")

DPI = 300
W, H = 744, 1039            # 63 x 88 mm at 300 dpi (standard card size)
A4 = (2480, 3508)

FACTION = {
    "Magma":   {"ar": "ماجما",  "main": (255, 107, 20),  "dark": (40, 14, 8)},
    "Tide":    {"ar": "تيار",   "main": (51, 191, 255),  "dark": (6, 26, 44)},
    "Volt":    {"ar": "برق",    "main": (255, 230, 64),  "dark": (34, 32, 18)},
    "Crystal": {"ar": "بلور",   "main": (217, 179, 255), "dark": (30, 22, 46)},
    "Gravity": {"ar": "جاذبية", "main": (153, 77, 255),  "dark": (16, 8, 30)},
    "None":    {"ar": "محايد",  "main": (200, 200, 210), "dark": (24, 24, 30)},
}
TYPE_AR = {"Creature": "كائن", "Technique": "تقنية", "Trap": "فخ", "Field": "ساحة"}


def font(path, size):
    return ImageFont.truetype(path, size, layout_engine=ImageFont.Layout.RAQM)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def seeded(card_id):
    return random.Random(int(hashlib.md5(card_id.encode()).hexdigest(), 16))


# ---------------------------------------------------------------- artwork (also the AR marker)

def draw_art(card, w, h):
    f = FACTION[card["Faction"]]
    rnd = seeded(card["Id"])
    img = Image.new("RGB", (w, h), f["dark"])
    d = ImageDraw.Draw(img, "RGBA")

    # Background shards: random dark/light polygons give the camera plenty of unique corners.
    for _ in range(70):
        cx, cy = rnd.uniform(0, w), rnd.uniform(0, h)
        r = rnd.uniform(20, 140)
        pts = [(cx + math.cos(a) * r * rnd.uniform(0.4, 1.0), cy + math.sin(a) * r * rnd.uniform(0.4, 1.0))
               for a in sorted(rnd.uniform(0, 2 * math.pi) for _ in range(rnd.randint(3, 6)))]
        shade = mix(f["dark"], f["main"], rnd.uniform(0.05, 0.55))
        d.polygon(pts, fill=shade + (rnd.randint(90, 220),))

    motif = card["Faction"]
    if motif == "Magma":
        for _ in range(14):  # glowing cracks
            x, y = rnd.uniform(0, w), rnd.uniform(0, h)
            pts = [(x, y)]
            for _ in range(rnd.randint(4, 9)):
                x += rnd.uniform(-60, 60); y += rnd.uniform(-60, 60); pts.append((x, y))
            d.line(pts, fill=f["main"] + (255,), width=rnd.randint(3, 7), joint="curve")
        for _ in range(60):
            x, y, s = rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(2, 6)
            d.ellipse([x - s, y - s, x + s, y + s], fill=(255, 220, 120, 230))
    elif motif == "Tide":
        for k in range(9):  # waves
            y0, amp, freq, ph = rnd.uniform(0, h), rnd.uniform(10, 40), rnd.uniform(0.01, 0.04), rnd.uniform(0, 6)
            pts = [(x, y0 + math.sin(x * freq + ph) * amp) for x in range(0, w + 8, 8)]
            d.line(pts, fill=mix(f["main"], (255, 255, 255), rnd.uniform(0, 0.6)) + (230,), width=rnd.randint(3, 8))
        for _ in range(40):
            x, y, s = rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(4, 16)
            d.ellipse([x - s, y - s, x + s, y + s], outline=(220, 245, 255, 230), width=3)
    elif motif == "Volt":
        for _ in range(10):  # lightning
            x, y = rnd.uniform(0, w), rnd.uniform(0, h * 0.3)
            pts = [(x, y)]
            while y < h:
                x += rnd.uniform(-45, 45); y += rnd.uniform(25, 70); pts.append((x, y))
            d.line(pts, fill=(255, 255, 255, 255), width=rnd.randint(2, 4))
            d.line(pts, fill=f["main"] + (140,), width=rnd.randint(7, 11))
    elif motif == "Crystal":
        for _ in range(22):  # faceted prisms
            cx, cy, r = rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(25, 90)
            rot = rnd.uniform(0, math.pi)
            pts = [(cx + math.cos(rot + i * math.pi / 3) * r, cy + math.sin(rot + i * math.pi / 3) * r * 1.6) for i in range(6)]
            d.polygon(pts, fill=mix(f["main"], (255, 255, 255), rnd.uniform(0, 0.5)) + (rnd.randint(120, 210),),
                      outline=(255, 255, 255, 255))
            d.line([pts[0], pts[3]], fill=(255, 255, 255, 200), width=2)
    elif motif == "Gravity":
        for _ in range(10):  # orbits
            cx, cy = w / 2 + rnd.uniform(-90, 90), h / 2 + rnd.uniform(-90, 90)
            rx, ry = rnd.uniform(80, 320), rnd.uniform(30, 140)
            d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=f["main"] + (220,), width=rnd.randint(2, 6))
        for _ in range(50):
            x, y, s = rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(3, 10)
            d.rectangle([x - s, y - s, x + s, y + s], fill=(200, 170, 255, 220))
    else:
        for _ in range(30):
            x1, y1, x2, y2 = rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(0, w), rnd.uniform(0, h)
            d.line([x1, y1, x2, y2], fill=(255, 255, 255, 160), width=3)

    # The glowing core, shaped by card type.
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    cx, cy, R = w / 2, h / 2, min(w, h) * 0.2
    g.ellipse([cx - R * 1.6, cy - R * 1.6, cx + R * 1.6, cy + R * 1.6], fill=f["main"] + (150,))
    glow = glow.filter(ImageFilter.GaussianBlur(40))
    img.paste(glow, (0, 0), glow)
    d = ImageDraw.Draw(img, "RGBA")
    t = card["Type"]
    if t == "Creature":
        pts = [(cx, cy - R), (cx + R * 0.7, cy), (cx, cy + R), (cx - R * 0.7, cy)]
    elif t == "Technique":
        pts = [(cx + math.cos(i * math.pi / 5 - math.pi / 2) * (R if i % 2 == 0 else R * 0.45),
                cy + math.sin(i * math.pi / 5 - math.pi / 2) * (R if i % 2 == 0 else R * 0.45)) for i in range(10)]
    elif t == "Trap":
        pts = [(cx + math.cos(i * math.pi / 3) * R, cy + math.sin(i * math.pi / 3) * R) for i in range(6)]
    else:
        pts = [(cx - R, cy + R * 0.3), (cx - R * 0.3, cy - R * 0.6), (cx + R * 0.2, cy), (cx + R * 0.6, cy - R * 0.4), (cx + R, cy + R * 0.3)]
    d.polygon(pts, fill=(255, 255, 255, 235), outline=f["main"] + (255,), width=6)
    return img


# ---------------------------------------------------------------- card face

def wrap_rtl(draw, text, fnt, max_w):
    words, lines, line = text.split(), [], ""
    for word in words:
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=fnt, direction="rtl") <= max_w:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def text_right(draw, xy_right, text, fnt, fill):
    x, y = xy_right
    tw = draw.textlength(text, font=fnt, direction="rtl")
    draw.text((x - tw, y), text, font=fnt, fill=fill, direction="rtl")


def text_center(draw, center, text, fnt, fill, rtl=True):
    cx, cy = center
    kw = {"direction": "rtl"} if rtl else {}
    box = draw.textbbox((0, 0), text, font=fnt, **kw)
    draw.text((cx - (box[2] - box[0]) / 2 - box[0], cy - (box[3] - box[1]) / 2 - box[1]), text, font=fnt, fill=fill, **kw)


def render_card(card, number, total):
    f = FACTION[card["Faction"]]
    img = Image.new("RGB", (W, H), f["dark"])
    d = ImageDraw.Draw(img, "RGBA")

    # Frame.
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=36, fill=mix(f["dark"], (0, 0, 0), 0.3))
    d.rounded_rectangle([14, 14, W - 15, H - 15], radius=28, outline=f["main"], width=8)
    d.rounded_rectangle([30, 30, W - 31, H - 31], radius=20, fill=mix(f["dark"], f["main"], 0.12))

    # Name banner with cost gem on the left.
    d.rounded_rectangle([44, 44, W - 45, 132], radius=16, fill=mix(f["dark"], f["main"], 0.35))
    name = card["NameAr"].split("،")[0]
    text_right(d, (W - 66, 50), name, font(FONT_BOLD, 50), (255, 255, 255))
    d.ellipse([52, 50, 132, 130], fill=(30, 30, 40), outline=f["main"], width=6)
    text_center(d, (92, 88), str(card["Cost"]), font(FONT_BOLD, 46), (255, 255, 255), rtl=False)

    # Art window.
    art = draw_art(card, W - 100, 470)
    img.paste(art, (50, 146))
    d.rectangle([50, 146, W - 51, 146 + 470], outline=f["main"], width=4)

    # Type line.
    d.rounded_rectangle([44, 628, W - 45, 686], radius=12, fill=mix(f["dark"], f["main"], 0.3))
    text_right(d, (W - 66, 630), f"{TYPE_AR[card['Type']]}  •  {f['ar']}", font(FONT_BOLD, 32), (255, 255, 255))

    # Ability text.
    box_top, box_bottom = 700, (900 if card["Type"] == "Creature" else 960)
    d.rounded_rectangle([44, box_top, W - 45, box_bottom], radius=12, fill=(250, 246, 238))
    fnt = font(FONT_TEXT, 30)
    y = box_top + 14
    for line in wrap_rtl(d, card["TextAr"], fnt, W - 140):
        text_right(d, (W - 70, y), line, fnt, (30, 26, 36))
        y += 46

    # Attack / defence.
    if card["Type"] == "Creature":
        for i, (label, value, color) in enumerate([("هجوم", card["Attack"], (255, 140, 90)), ("دفاع", card["Defense"], (130, 200, 255))]):
            x0 = 60 if i == 1 else W // 2 + 10
            d.rounded_rectangle([x0, 912, x0 + W // 2 - 70, 976], radius=14, fill=(24, 22, 30), outline=color, width=4)
            text_center(d, (x0 + (W // 2 - 70) / 2, 944), f"{label} {value}", font(FONT_BOLD, 34), color)

    # Footer.
    small = font(FONT_TEXT, 20)
    d.text((56, 990), f"STONERIFT  S1  {number:02d}/{total}", font=small, fill=(220, 220, 230))
    tw = d.textlength(card["Id"], font=small)
    d.text((W - 56 - tw, 990), card["Id"], font=small, fill=(160, 160, 175))
    return img


def render_back():
    img = Image.new("RGB", (W, H), (14, 12, 20))
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle([14, 14, W - 15, H - 15], radius=28, outline=(200, 170, 110), width=8)
    rnd = random.Random(7)
    for faction in ["Magma", "Tide", "Volt", "Crystal", "Gravity"]:
        c = FACTION[faction]["main"]
        for _ in range(4):
            rx, ry = rnd.uniform(120, 300), rnd.uniform(60, 200)
            d.ellipse([W / 2 - rx, H / 2 - ry, W / 2 + rx, H / 2 + ry], outline=c + (150,), width=4)
    text_center(d, (W / 2, H / 2 - 30), "STONERIFT", font(FONT_BOLD, 78), (240, 220, 170), rtl=False)
    text_center(d, (W / 2, H / 2 + 60), "صدع الحجر", font(FONT_BOLD, 52), (240, 220, 170))
    return img


# ---------------------------------------------------------------- print sheets

def sheets(images, path):
    cols, rows = 3, 3
    mx, my = (A4[0] - cols * W) // 2, (A4[1] - rows * H) // 2
    pages = []
    for start in range(0, len(images), cols * rows):
        page = Image.new("RGB", A4, "white")
        d = ImageDraw.Draw(page)
        for i, im in enumerate(images[start:start + cols * rows]):
            x, y = mx + (i % cols) * W, my + (i // cols) * H
            page.paste(im, (x, y))
        # Cut marks on the outer edge of every column and row.
        for c in range(cols + 1):
            x = mx + c * W
            d.line([x, my - 60, x, my - 15], fill="black", width=3)
            d.line([x, my + rows * H + 15, x, my + rows * H + 60], fill="black", width=3)
        for r in range(rows + 1):
            y = my + r * H
            d.line([mx - 60, y, mx - 15, y], fill="black", width=3)
            d.line([mx + cols * W + 15, y, mx + cols * W + 60, y], fill="black", width=3)
        pages.append(page)
    pages[0].save(path, save_all=True, append_images=pages[1:], resolution=DPI, quality=88)
    print("Wrote", path, f"({len(pages)} pages)")


def main():
    if not features.check("raqm"):
        raise SystemExit("Pillow needs libraqm for Arabic text (pip install pillow on most systems includes it).")
    cards = json.loads((DATA / "cards.json").read_text(encoding="utf-8"))
    decks = json.loads((DATA / "decks.json").read_text(encoding="utf-8"))
    order = {"Creature": 0, "Technique": 1, "Trap": 2, "Field": 3}
    cards.sort(key=lambda c: (order[c["Type"]], c["Faction"], c["Cost"], c["Id"]))

    faces = {}
    UNITY_IMAGES.mkdir(parents=True, exist_ok=True)
    for n, card in enumerate(cards, 1):
        face = render_card(card, n, len(cards))
        faces[card["Id"]] = face
        # The scanner's reference image: the whole card face, smaller.
        face.resize((W // 2, H // 2), Image.LANCZOS).save(UNITY_IMAGES / f"{card['Id']}.jpg", quality=88)

    PRINT.mkdir(exist_ok=True)
    sheets([faces[c["Id"]] for c in cards], PRINT / "StoneRift-all-cards.pdf")
    for deck in decks:
        images = [faces[cid] for cid, count in sorted(deck["Cards"].items()) for _ in range(count)]
        sheets(images, PRINT / f"StoneRift-{deck['Id']}.pdf")
    render_back().save(PRINT / "card-back.png")
    faces[cards[0]["Id"]].save(PRINT / "preview.png")
    print("Wrote", len(faces), "reference images to", UNITY_IMAGES)


if __name__ == "__main__":
    main()
