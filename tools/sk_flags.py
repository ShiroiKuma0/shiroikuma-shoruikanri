#!/usr/bin/env python3
"""The flags of the App language picker, as bundled images.

One PNG per listed language's country, and a globe for a language without a country and for
"System default" — drawn from Google's Noto Color Emoji (SIL Open Font License), never left to
the phone's own emoji font, which may lack flags or draw them differently.

    python3 tools/sk_flags.py            # writes app/src/main/res/drawable-nodpi/sk_flag_*.png

Run it again when a language joins the list: add its country to COUNTRIES.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable-nodpi")
COUNTRIES = ["JP", "US", "EG", "BG", "ES", "CZ", "DK", "DE", "GR", "IR", "FI", "FR", "HU", "ID", "IS", "IT", "IL", "KR", "LT", "NO", "NL", "PL", "PT", "RO", "RU", "TR", "UA", "VN", "CN", "TW"]
SIZE = (136, 128)


def render(text: str, name: str, font) -> None:
    image = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    box = draw.textbbox((0, 0), text, font=font, embedded_color=True)
    x = (SIZE[0] - (box[2] - box[0])) // 2 - box[0]
    y = (SIZE[1] - (box[3] - box[1])) // 2 - box[1]
    draw.text((x, y), text, font=font, embedded_color=True)
    image.save(os.path.join(OUT, f"sk_flag_{name}.png"), optimize=True)


def main() -> int:
    font = ImageFont.truetype(FONT, 109)
    os.makedirs(OUT, exist_ok=True)
    for country in COUNTRIES:
        flag = "".join(chr(0x1F1E6 + ord(letter) - ord("A")) for letter in country)
        render(flag, country.lower(), font)
    render("\U0001F310", "globe", font)
    return 0


if __name__ == "__main__":
    sys.exit(main())
