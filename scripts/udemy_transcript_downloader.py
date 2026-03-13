#!/usr/bin/env python3
"""
Udemy Transcript Downloader
============================
Downloads transcripts chapter-wise from a Udemy course using Udemy's internal API,
then generates clean HTML documentation.

HOW TO GET YOUR ACCESS TOKEN:
1. Log into Udemy in your browser (Chrome/Firefox)
2. Open DevTools (F12) -> Application tab -> Cookies -> https://www.udemy.com
3. Find the cookie named 'access_token' and copy its value
   OR
   Open DevTools -> Network tab -> refresh page -> find any request to
   'udemy.com/api-2.0/' -> look at Request Headers -> find 'Authorization: Bearer <token>'

USAGE:
    python3 udemy_transcript_downloader.py \
        --access-token "YOUR_ACCESS_TOKEN_HERE" \
        --course-url "https://www.udemy.com/course/claudecode/" \
        --output-dir "./udemy_docs"
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not found. Install it with: pip3 install requests")
    sys.exit(1)


# ──────────────────────────────────────────────
# Udemy API helpers
# ──────────────────────────────────────────────

BASE_URL = "https://www.udemy.com/api-2.0"


def get_headers(access_token: str) -> dict:
    return {
        "Authorization": f"Bearer {access_token}",
        "Accept": "application/json, text/plain, */*",
        "Content-Type": "application/json",
        "X-Udemy-Cache-Course-Landing-Page": "False",
        "Referer": "https://www.udemy.com/",
        "User-Agent": (
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ),
    }


def extract_course_slug(course_url: str) -> str:
    """Extract slug from URL like https://www.udemy.com/course/claudecode/"""
    match = re.search(r"/course/([^/?#]+)", course_url)
    if not match:
        raise ValueError(f"Cannot extract course slug from URL: {course_url}")
    return match.group(1)


def get_course_id(slug: str, headers: dict) -> int:
    """Resolve course slug → numeric course ID."""
    url = f"{BASE_URL}/courses/{slug}/?fields[course]=id,title"
    resp = requests.get(url, headers=headers, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    return data["id"]


def get_curriculum(course_id: int, headers: dict) -> list:
    """Fetch all curriculum items (chapters + lectures)."""
    items = []
    page = 1
    page_size = 200
    while True:
        url = (
            f"{BASE_URL}/courses/{course_id}/subscriber-curriculum-items/"
            f"?page={page}&page_size={page_size}"
            f"&fields[lecture]=title,asset,sort_order,object_index"
            f"&fields[chapter]=title,object_index,sort_order"
            f"&fields[asset]=asset_type,captions,title,length"
            f"&fields[quiz]=title,object_index,sort_order"
        )
        resp = requests.get(url, headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        items.extend(data.get("results", []))
        if not data.get("next"):
            break
        page += 1
        time.sleep(0.5)   # be polite
    return items


def get_captions_for_asset(asset_id: int, headers: dict) -> list:
    """Return list of caption objects for an asset."""
    url = f"{BASE_URL}/assets/{asset_id}/captions/"
    try:
        resp = requests.get(url, headers=headers, timeout=30)
        if resp.status_code == 403:
            return []
        resp.raise_for_status()
        return resp.json().get("results", [])
    except Exception:
        return []


def download_vtt(vtt_url: str, headers: dict) -> str:
    """Download VTT content from URL."""
    try:
        resp = requests.get(vtt_url, headers=headers, timeout=30)
        resp.raise_for_status()
        return resp.text
    except Exception as exc:
        print(f"    ⚠  Could not download VTT: {exc}")
        return ""


# ──────────────────────────────────────────────
# VTT → plain text
# ──────────────────────────────────────────────

def vtt_to_text(vtt_content: str) -> str:
    """Convert WebVTT format to clean plain text."""
    lines = vtt_content.splitlines()
    text_lines = []
    skip_next = False

    for line in lines:
        line = line.strip()
        # Skip header
        if line.startswith("WEBVTT") or line.startswith("NOTE"):
            continue
        # Skip cue numbers (digits only)
        if re.match(r"^\d+$", line):
            skip_next = True
            continue
        # Skip timestamp lines  00:00:00.000 --> 00:00:00.000
        if re.match(r"^\d{2}:\d{2}:\d{2}[.,]\d{3}\s*-->", line):
            skip_next = False
            continue
        if skip_next:
            skip_next = False
            continue
        if line:
            # Remove inline VTT tags like <00:00:00.000><c>
            clean = re.sub(r"<[^>]+>", "", line)
            text_lines.append(clean)

    # Deduplicate adjacent identical lines (common in VTT)
    deduped = []
    prev = None
    for l in text_lines:
        if l != prev:
            deduped.append(l)
            prev = l

    return " ".join(deduped).strip()


# ──────────────────────────────────────────────
# Organise curriculum
# ──────────────────────────────────────────────

def organise_chapters(curriculum: list) -> list:
    """
    Returns list of chapters:
    [
      {
        "title": "Chapter Title",
        "index": 1,
        "lectures": [
          {"title": "Lecture title", "asset_id": 123, "index": 1},
          ...
        ]
      },
      ...
    ]
    """
    chapters = []
    current_chapter = None

    for item in curriculum:
        _class = item.get("_class")
        if _class == "chapter":
            current_chapter = {
                "title": item.get("title", "Untitled Chapter"),
                "index": item.get("object_index", 0),
                "lectures": [],
            }
            chapters.append(current_chapter)
        elif _class == "lecture":
            if current_chapter is None:
                current_chapter = {"title": "Introduction", "index": 0, "lectures": []}
                chapters.append(current_chapter)
            asset = item.get("asset", {})
            asset_type = asset.get("asset_type", "")
            if asset_type in ("Video", "Article"):
                current_chapter["lectures"].append(
                    {
                        "title": item.get("title", "Untitled Lecture"),
                        "index": item.get("object_index", 0),
                        "asset_id": asset.get("id"),
                        "asset_type": asset_type,
                    }
                )

    return chapters


# ──────────────────────────────────────────────
# HTML generator
# ──────────────────────────────────────────────

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>{course_title} – Course Documentation</title>
  <style>
    :root {{
      --bg: #0f1117;
      --surface: #1a1d27;
      --surface2: #23263a;
      --accent: #a78bfa;
      --accent2: #60a5fa;
      --text: #e2e8f0;
      --muted: #94a3b8;
      --border: #2d3148;
      --radius: 10px;
    }}

    * {{ box-sizing: border-box; margin: 0; padding: 0; }}

    body {{
      background: var(--bg);
      color: var(--text);
      font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
      display: flex;
      min-height: 100vh;
    }}

    /* ── Sidebar ── */
    #sidebar {{
      width: 300px;
      min-width: 260px;
      background: var(--surface);
      border-right: 1px solid var(--border);
      padding: 20px 0;
      position: sticky;
      top: 0;
      height: 100vh;
      overflow-y: auto;
    }}

    #sidebar h1 {{
      font-size: 1rem;
      font-weight: 700;
      color: var(--accent);
      padding: 0 20px 16px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 12px;
    }}

    .chapter-link {{
      display: block;
      padding: 8px 20px;
      color: var(--muted);
      text-decoration: none;
      font-size: 0.82rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: .04em;
      transition: color .2s;
    }}
    .chapter-link:hover {{ color: var(--accent); }}

    .lecture-link {{
      display: block;
      padding: 5px 20px 5px 32px;
      color: var(--text);
      text-decoration: none;
      font-size: 0.82rem;
      line-height: 1.4;
      transition: color .2s, background .2s;
      border-radius: 4px;
      margin: 1px 8px;
    }}
    .lecture-link:hover {{ background: var(--surface2); color: var(--accent2); }}

    /* ── Main content ── */
    #main {{
      flex: 1;
      padding: 40px 48px;
      max-width: 960px;
      margin: 0 auto;
    }}

    .course-header {{
      margin-bottom: 48px;
      padding-bottom: 24px;
      border-bottom: 2px solid var(--border);
    }}

    .course-header h1 {{
      font-size: 2rem;
      font-weight: 800;
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin-bottom: 8px;
    }}

    .course-header p {{ color: var(--muted); font-size: 0.9rem; }}

    /* ── Chapter ── */
    .chapter {{
      margin-bottom: 56px;
    }}

    .chapter-title {{
      font-size: 1.35rem;
      font-weight: 700;
      color: var(--accent);
      margin-bottom: 20px;
      padding-bottom: 10px;
      border-bottom: 1px solid var(--border);
      display: flex;
      align-items: center;
      gap: 10px;
    }}

    .chapter-num {{
      background: var(--accent);
      color: #fff;
      font-size: 0.75rem;
      font-weight: 800;
      padding: 2px 8px;
      border-radius: 20px;
      letter-spacing: .05em;
    }}

    /* ── Lecture ── */
    .lecture {{
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      margin-bottom: 20px;
      overflow: hidden;
    }}

    .lecture-header {{
      background: var(--surface2);
      padding: 14px 20px;
      display: flex;
      align-items: center;
      gap: 12px;
      cursor: pointer;
      user-select: none;
    }}

    .lecture-num {{
      color: var(--accent2);
      font-size: 0.75rem;
      font-weight: 700;
      min-width: 28px;
    }}

    .lecture-title {{
      font-size: 0.95rem;
      font-weight: 600;
      flex: 1;
    }}

    .toggle-icon {{
      color: var(--muted);
      font-size: 1rem;
      transition: transform .25s;
    }}

    .lecture-body {{
      padding: 20px;
      display: none;
    }}

    .lecture-body.open {{ display: block; }}

    .transcript {{
      font-size: 0.9rem;
      line-height: 1.8;
      color: var(--text);
      white-space: pre-wrap;
    }}

    .no-transcript {{
      color: var(--muted);
      font-style: italic;
      font-size: 0.85rem;
    }}

    /* ── Search ── */
    #search-wrap {{
      padding: 0 12px 12px;
    }}

    #search {{
      width: 100%;
      padding: 8px 12px;
      background: var(--surface2);
      border: 1px solid var(--border);
      border-radius: 6px;
      color: var(--text);
      font-size: 0.82rem;
      outline: none;
    }}
    #search::placeholder {{ color: var(--muted); }}
    #search:focus {{ border-color: var(--accent); }}

    /* ── Scrollbar ── */
    ::-webkit-scrollbar {{ width: 6px; }}
    ::-webkit-scrollbar-track {{ background: transparent; }}
    ::-webkit-scrollbar-thumb {{ background: var(--border); border-radius: 3px; }}
  </style>
</head>
<body>

<nav id="sidebar">
  <h1>📚 {course_title}</h1>
  <div id="search-wrap">
    <input id="search" type="text" placeholder="Search transcripts…" />
  </div>
  <div id="toc">
{toc_html}
  </div>
</nav>

<main id="main">
  <header class="course-header">
    <h1>{course_title}</h1>
    <p>Auto-generated documentation from video transcripts · {lecture_count} lectures · {chapter_count} chapters</p>
  </header>

{chapters_html}
</main>

<script>
  // Toggle lecture bodies
  document.querySelectorAll('.lecture-header').forEach(header => {{
    header.addEventListener('click', () => {{
      const body = header.nextElementSibling;
      const icon = header.querySelector('.toggle-icon');
      body.classList.toggle('open');
      icon.textContent = body.classList.contains('open') ? '▲' : '▼';
    }});
  }});

  // Search / filter
  const search = document.getElementById('search');
  search.addEventListener('input', () => {{
    const q = search.value.toLowerCase().trim();
    document.querySelectorAll('.lecture').forEach(lec => {{
      const text = lec.textContent.toLowerCase();
      const match = !q || text.includes(q);
      lec.style.display = match ? '' : 'none';
      if (match && q) {{
        const body = lec.querySelector('.lecture-body');
        body.classList.add('open');
        lec.querySelector('.toggle-icon').textContent = '▲';
      }}
    }});
    document.querySelectorAll('.chapter').forEach(ch => {{
      const visible = [...ch.querySelectorAll('.lecture')].some(l => l.style.display !== 'none');
      ch.style.display = visible ? '' : 'none';
    }});
  }});
</script>
</body>
</html>
"""


def build_html(course_title: str, chapters: list) -> str:
    toc_parts = []
    chapter_parts = []
    lecture_count = sum(len(c["lectures"]) for c in chapters)

    for ch in chapters:
        ch_id = f"chapter-{ch['index']}"
        toc_parts.append(
            f'    <a class="chapter-link" href="#{ch_id}">'
            f'{ch["index"]}. {ch["title"]}</a>'
        )

        lec_html_parts = []
        for lec in ch["lectures"]:
            lec_id = f"lecture-{ch['index']}-{lec['index']}"
            toc_parts.append(
                f'    <a class="lecture-link" href="#{lec_id}">'
                f'{lec["index"]}. {lec["title"]}</a>'
            )

            transcript = lec.get("transcript", "")
            if transcript:
                transcript_html = f'<div class="transcript">{transcript}</div>'
            else:
                transcript_html = '<p class="no-transcript">Transcript not available for this lecture.</p>'

            lec_html_parts.append(
                f'      <div class="lecture" id="{lec_id}">\n'
                f'        <div class="lecture-header">\n'
                f'          <span class="lecture-num">#{lec["index"]}</span>\n'
                f'          <span class="lecture-title">{lec["title"]}</span>\n'
                f'          <span class="toggle-icon">▼</span>\n'
                f'        </div>\n'
                f'        <div class="lecture-body">\n'
                f'          {transcript_html}\n'
                f'        </div>\n'
                f'      </div>'
            )

        chapter_parts.append(
            f'  <section class="chapter" id="{ch_id}">\n'
            f'    <h2 class="chapter-title">'
            f'<span class="chapter-num">Ch {ch["index"]}</span>{ch["title"]}</h2>\n'
            + "\n".join(lec_html_parts) + "\n"
            f'  </section>'
        )

    return HTML_TEMPLATE.format(
        course_title=course_title,
        toc_html="\n".join(toc_parts),
        chapters_html="\n\n".join(chapter_parts),
        lecture_count=lecture_count,
        chapter_count=len(chapters),
    )


# ──────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Download Udemy transcripts → HTML docs")
    parser.add_argument("--access-token", required=True, help="Udemy access_token from browser cookie")
    parser.add_argument("--course-url", required=True, help="Udemy course URL, e.g. https://www.udemy.com/course/claudecode/")
    parser.add_argument("--output-dir", default="./udemy_docs", help="Directory to save output (default: ./udemy_docs)")
    parser.add_argument("--lang", default="en", help="Caption language code (default: en)")
    args = parser.parse_args()

    headers = get_headers(args.access_token)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    slug = extract_course_slug(args.course_url)
    print(f"[1/5] Course slug: {slug}")

    print("[2/5] Fetching course ID…")
    try:
        course_id = get_course_id(slug, headers)
        course_title = slug.replace("-", " ").title()
        print(f"      Course ID: {course_id}")
    except Exception as exc:
        print(f"      ERROR: {exc}")
        print("      Hint: Check your access_token and make sure you own the course.")
        sys.exit(1)

    print("[3/5] Fetching curriculum…")
    curriculum = get_curriculum(course_id, headers)
    chapters = organise_chapters(curriculum)
    lecture_count = sum(len(c["lectures"]) for c in chapters)
    print(f"      {len(chapters)} chapters, {lecture_count} lectures found.")

    print("[4/5] Downloading transcripts…")
    raw_data = {"course_title": course_title, "chapters": []}

    for ch_idx, chapter in enumerate(chapters, 1):
        print(f"\n  Chapter {ch_idx}/{len(chapters)}: {chapter['title']}")
        ch_data = {"title": chapter["title"], "index": chapter["index"], "lectures": []}

        for lec in chapter["lectures"]:
            lec_title = lec["title"]
            asset_id = lec.get("asset_id")
            transcript_text = ""

            if asset_id:
                captions = get_captions_for_asset(asset_id, headers)
                # Prefer requested language, fall back to first available
                caption = next(
                    (c for c in captions if c.get("locale_id", "").startswith(args.lang)),
                    captions[0] if captions else None,
                )
                if caption:
                    vtt_url = caption.get("url") or caption.get("vtt_url") or caption.get("file_url")
                    if vtt_url:
                        print(f"    ✓ {lec['index']:3}. {lec_title[:60]}")
                        vtt_content = download_vtt(vtt_url, headers)
                        transcript_text = vtt_to_text(vtt_content)
                    else:
                        print(f"    - {lec['index']:3}. {lec_title[:60]} (no VTT URL)")
                else:
                    print(f"    - {lec['index']:3}. {lec_title[:60]} (no captions)")
                time.sleep(0.3)

            ch_data["lectures"].append(
                {
                    "title": lec_title,
                    "index": lec["index"],
                    "asset_type": lec.get("asset_type"),
                    "transcript": transcript_text,
                }
            )

        raw_data["chapters"].append(ch_data)

    # Save raw JSON
    json_path = output_dir / "transcripts.json"
    json_path.write_text(json.dumps(raw_data, indent=2, ensure_ascii=False))
    print(f"\n  Raw transcripts saved → {json_path}")

    print("\n[5/5] Generating HTML documentation…")
    html = build_html(course_title, raw_data["chapters"])
    html_path = output_dir / "documentation.html"
    html_path.write_text(html, encoding="utf-8")
    print(f"  HTML documentation saved → {html_path}")
    print("\n✅ Done! Open the HTML file in your browser.")


if __name__ == "__main__":
    main()
