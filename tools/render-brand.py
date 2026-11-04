#!/usr/bin/env python3
"""Renders the TV banner PNGs and the legacy launcher icons.

    python3 tools/render-brand.py          # needs rsvg-convert (icons only) and Pillow

The square launcher icon is a vector adaptive icon (res/drawable/ic_launcher_*.xml) on Android 8.0
and later; docs/brand/icon.svg is its source of truth for the website and README. Android 5.x to 7.x
cannot read adaptive icons, so this also renders mipmap-*/ic_launcher.png from that SVG.

The banner is drawn here with Pillow rather than rendered from docs/brand/banner-art.svg: the art is
a gradient, one arc and a wordmark, and going through rsvg meant the banner could not be regenerated
on a machine without it. The colours and the arc's geometry are copied from that SVG — change both
together.
"""
import os
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON = os.path.join(ROOT, "docs/brand/icon.svg")
FONT = os.path.join(ROOT, "app/src/main/res/font/inter.ttf")
RES = os.path.join(ROOT, "app/src/main/res")


BANNER_START = (0xFF, 0xA7, 0x33)
BANNER_END = (0xFF, 0x5E, 0x1A)
SS = 3  # the banner is drawn at 3x and scaled down, so the 7 dp arc has a smooth edge


def _gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / (w - 1)) + (y / (h - 1))) / 2
            px[x, y] = tuple(round(BANNER_START[i] + (BANNER_END[i] - BANNER_START[i]) * t) for i in range(3))
    return img


def _bezier(p0, p1, p2, p3, steps=600):
    out = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        out.append((
            u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1],
        ))
    return out


def _text_font(px):
    font = ImageFont.truetype(FONT, px)
    try:
        font.set_variation_by_axes([32, 650])  # optical size (display), weight
    except Exception:
        pass
    return font


def banner(scale):
    w, h = 320 * scale * SS, 180 * scale * SS
    k = w / 320.0
    img = _gradient(320 * scale, 180 * scale).resize((w, h), Image.BILINEAR)
    draw = ImageDraw.Draw(img)
    # M60 104c8-16 16-24 24-24s16 8 24 24 — one arc, stroke 7, round caps.
    curve = _bezier((60, 104), (68, 88), (76, 80), (84, 80)) + _bezier((84, 80), (92, 80), (100, 88), (108, 104))
    pts = [(x * k, y * k) for x, y in curve]
    draw.line(pts, fill=(255, 255, 255), width=max(1, round(7 * k)), joint="curve")
    r = 3.5 * k
    for end in (pts[0], pts[-1]):
        draw.ellipse([end[0] - r, end[1] - r, end[0] + r, end[1] + r], fill=(255, 255, 255))
    # "Orange" is wider than the "Aerial" this banner was drawn for, so the wordmark is sized to the
    # space right of the arc instead of running off the 320 dp edge.
    text = "Orange"
    x = 124 * k
    limit = (320 - 34) * k - x
    size = 50
    while size > 20:
        font = _text_font(round(size * k))
        left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
        if right - left <= limit:
            break
        size -= 1
    y = (h - (bottom - top)) / 2 - top + 2 * k
    draw.text((x, y), text, font=font, fill=(255, 255, 255))
    return img.resize((320 * scale, 180 * scale), Image.LANCZOS)


def legacy_icon(px):
    """The visible 72 of the 108 adaptive canvas, as a rounded square px wide."""
    full = px * 108 // 72
    with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
        subprocess.run(["rsvg-convert", "-w", str(full), "-h", str(full), ICON, "-o", tmp.name], check=True)
        img = Image.open(tmp.name).convert("RGBA")
    inset = (full - px) // 2
    img = img.crop((inset, inset, inset + px, inset + px))
    # Draw the mask at 4x and scale down for a smooth edge.
    mask = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, px * 4 - 1, px * 4 - 1), radius=px * 4 * 0.22, fill=255)
    img.putalpha(mask.resize((px, px), Image.LANCZOS))
    return img


if __name__ == "__main__":
    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        out = os.path.join(RES, f"mipmap-{density}", "ic_launcher.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        legacy_icon(px).save(out, optimize=True)
        print(out)
    for density, scale in (("xhdpi", 2), ("xxhdpi", 3)):
        out = os.path.join(RES, f"drawable-{density}", "banner.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        banner(scale).convert("RGB").save(out, optimize=True)
        print(out)
