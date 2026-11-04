#!/usr/bin/env python3
"""Renders the launcher icons, the TV banner and the logo PNG.

    python3 tools/render-brand.py          # Pillow only

The mark is one ring plus an "r"-shaped stem: an orange O whose top carries a short stalk
bending to the right, so it reads as an orange and not as a letter. The numbers below are the
same ones in docs/brand/icon.svg, which is the source for the website and README, and in
site/favicon.svg, which is the same mark at 32 px. Change all three together.

Everything is drawn here with Pillow rather than rendered from the SVG: rsvg-convert is not on
every machine (it was not on this one when the mark was redesigned), and a banner that cannot
be regenerated is worse than one drawn twice. Both marks are strokes, so the shapes are just
circles and a cubic — nothing that needs a renderer.

Android 8.0 and later read the vector adaptive icon (res/drawable/ic_launcher_*.xml) and never
look at these PNGs; 5.x to 7.x cannot read adaptive icons, so mipmap-*/ic_launcher.png is
rendered from the same numbers to keep the two from drifting apart.
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app/src/main/res/font/inter.ttf")
RES = os.path.join(ROOT, "app/src/main/res")
BRAND = os.path.join(ROOT, "docs/brand")

# --- the mark, in the 108 dp canvas of docs/brand/icon.svg -------------------------------------
BG = (0xEE, 0xED, 0xEB)
RING = (0xFE, 0xB0, 0x5D)
STEM = (0x5A, 0x59, 0x57)

RING_CX, RING_CY, RING_R, RING_W = 54.07, 58.52, 20.69, 7.07
STEM_W = 3.78
STEM_X = 54.28
UPRIGHT_TOP, UPRIGHT_BOTTOM = 27.16, 34.65      # the stalk is drawn from the upright's middle
STALK = (
    ((54.28, 29.26), (56.00, 28.20), (58.50, 27.90), (61.00, 27.90)),
    ((61.00, 27.90), (63.00, 27.90), (64.30, 28.10), (65.00, 28.35)),
)

BANNER_START = (0xFF, 0xA7, 0x33)
BANNER_END = (0xFF, 0x5E, 0x1A)
SS = 4  # everything is drawn at 4x and scaled down, so 0.5 dp strokes keep a clean edge


def _gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / (w - 1)) + (y / (h - 1))) / 2
            px[x, y] = tuple(round(BANNER_START[i] + (BANNER_END[i] - BANNER_START[i]) * t) for i in range(3))
    return img


def _bezier(p0, p1, p2, p3, steps=160):
    out = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        out.append((
            u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1],
        ))
    return out


def _stroke(draw, pts, width, color):
    """A round-capped polyline, as SVG stroke-linecap="round" draws it."""
    draw.line(pts, fill=color, width=max(1, round(width)), joint="curve")
    r = width / 2
    for x, y in (pts[0], pts[-1]):
        draw.ellipse([x - r, y - r, x + r, y + r], fill=color)


def mark_layer(size, k, dx=0, dy=0, ring=RING, stem=STEM):
    """The mark on a transparent `size` square, scaled by `k` from the 108 dp canvas.

    A layer rather than drawing straight onto the ground keeps the ring's hole clear: composited
    over a gradient it shows the gradient, over a flat colour it shows that colour.
    """
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = RING_CX * k + dx, RING_CY * k + dy
    r, w = RING_R * k, RING_W * k
    d.ellipse([cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2], fill=ring)
    d.ellipse([cx - r + w / 2, cy - r + w / 2, cx + r - w / 2, cy + r - w / 2], fill=(0, 0, 0, 0))
    x = STEM_X * k + dx
    _stroke(d, [(x, UPRIGHT_BOTTOM * k + dy), (x, UPRIGHT_TOP * k + dy)], STEM_W * k, stem)
    for c in STALK:
        pts = [((p[0]) * k + dx, (p[1]) * k + dy) for p in _bezier(*c)]
        _stroke(d, pts, STEM_W * k, stem)
    return layer


def legacy_icon(px):
    """The visible 72 of the 108 adaptive canvas, as a rounded square px wide."""
    full = px * 108 // 72
    big = full * SS
    k = big / 108.0
    img = Image.new("RGBA", (big, big), BG + (255,))
    img.alpha_composite(mark_layer(big, k))
    inset = (big - px * SS) // 2
    img = img.crop((inset, inset, inset + px * SS, inset + px * SS))
    # Draw the mask at the same size as the crop so the corner radius is exact, then scale once.
    mask = Image.new("L", (px * SS, px * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, px * SS - 1, px * SS - 1), radius=px * SS * 0.22, fill=255)
    img.putalpha(mask)
    return img.resize((px, px), Image.LANCZOS)


def banner(scale, centre=False):
    w, h = 320 * scale * SS, 180 * scale * SS
    k_screen = w / 320.0          # banner dp -> drawn px
    img = _gradient(320 * scale, 180 * scale).resize((w, h), Image.BILINEAR).convert("RGBA")
    # The mark is 57.1 dp tall in its own canvas. At 48 dp it reads as the same weight as the
    # 48 dp-wide arch it replaces, and its stem still clears the banner's top edge.
    k = 48 / 57.1 * k_screen
    mx, my = 72 * k_screen, 90 * k_screen      # the mark's own bounding box centre
    dx = mx - 48.6 * k
    dy = my - 53.85 * k
    # The mark *is* the "o" of the wordmark, so the text after it is "range" and the banner reads
    # ǒrange. It used to say "Orange" behind the mark, which read as ǒOrange — two o's in a row.
    # Starting the text at the mark's measured right edge keeps the pair joined however the mark
    # is scaled; a hard-coded x would drift the moment the mark's size changes.
    text = "range"
    gap = 3 * k_screen                        # one letter-space at this size, measured by eye
    limit = (320 - 34) * k_screen
    mark_left, mark_right = mark_layer(w, k, dx, dy).getbbox()[0::2]
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    size = 50
    while size > 20:
        font = _text_font(round(size * k_screen))
        left, top, right, bottom = probe.textbbox((0, 0), text, font=font)
        if mark_right + gap + (right - left) <= limit:
            break
        size -= 1
    x = mark_right + gap - left               # cancel the glyph's own left side bearing
    if centre:
        # Dropping the O left the group sitting 33 dp off-centre, so the mark's old 72 dp anchor
        # no longer means anything. Centre the mark-plus-text block instead of trusting it.
        group = (x + right) - mark_left
        shift = (w - group) / 2 - mark_left
        dx, x = dx + shift, x + shift
    # Seat the ring like a capital O: its outer bottom on the baseline, so the ring and the "r"
    # stand on one line. Measured against the "r" rather than the string's own bbox, because the
    # "g" hangs below the baseline and would drag the mark down by its descender. Left as it was,
    # the ring sat 14.4 dp below the baseline at 50 dp type — which reads as a lowercase o that
    # fell over, not as the capital of its own wordmark.
    probe_y0 = probe.textbbox((0, 0), "r", font=font)[3]
    dy += probe_y0 - mark_layer(w, k, dx, dy).getbbox()[3]
    # With the mark on the baseline the pair is one block; centre that block, descender and
    # stalk included, rather than centring the text and letting the stalk stick out the top.
    mark_box = mark_layer(w, k, dx, dy).getbbox()
    y = h / 2 - (min(mark_box[1], top) + max(mark_box[3], bottom)) / 2
    dy += y
    img.alpha_composite(mark_layer(w, k, dx, dy, ring=(255, 255, 255, 255), stem=(255, 255, 255, 255)))
    ImageDraw.Draw(img).text((x, y), text, font=font, fill=(255, 255, 255, 255))
    return img.convert("RGB").resize((320 * scale, 180 * scale), Image.LANCZOS)

def _text_font(px):
    font = ImageFont.truetype(FONT, px)
    try:
        font.set_variation_by_axes([32, 650])  # optical size (display), weight
    except Exception:
        pass
    return font


if __name__ == "__main__":
    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        out = os.path.join(RES, f"mipmap-{density}", "ic_launcher.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        legacy_icon(px).save(out, optimize=True)
        print(out)
    out = os.path.join(BRAND, "icon.png")
    legacy_icon(256).save(out, optimize=True)
    print(out)
    for density, scale in (("xhdpi", 2), ("xxhdpi", 3)):
        out = os.path.join(RES, f"drawable-{density}", "banner.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        # Centred: the mark is the whole left edge of the wordmark now, so the old left-aligned
        # 72 dp anchor would leave 33 dp more white on the right than on the left.
        banner(scale, centre=True).save(out, optimize=True)
        print(out)
