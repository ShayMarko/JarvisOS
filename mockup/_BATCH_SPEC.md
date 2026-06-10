# JARVIS UI mockup batch — shared spec (T81–T180)

You are generating dark, hi-tech UI mockup screens for **JARVIS** — a local-first personal AI
assistant / "AI OS" (think Iron-Man's Jarvis as a desktop app). These are STATIC HTML mockups for
design ideation; the owner (Shay) will pick layouts he loves to build the real client.

## Output format — for EACH assigned template, Write ONE self-contained file to the EXACT path given
- Single HTML doc:
  `<!doctype html><html><head><meta charset="utf-8"><title>TNN</title><style> …all CSS inline… </style></head><body> … </body></html>`
- **NO external resources**: no web fonts, no `<img>` with URLs, no JS libraries, no network calls.
  Use only system fonts: `system-ui` / `-apple-system` / `'Segoe UI'` for sans, `ui-monospace` /
  `'SF Mono'` / `monospace` for mono, `Georgia` / `'Times New Roman'` / serif for serif.
  Inline **SVG** and **CSS gradients** are encouraged for charts/shapes. A tiny inline `<script>` is
  allowed only if the archetype truly needs it — prefer pure CSS/HTML (these are static shots).
- Canvas: design for **~1440×900** (screenshotted at 2×). Start CSS with
  `*{margin:0;box-sizing:border-box}` ; body `height:100vh;overflow:hidden` and FILL the viewport
  edge-to-edge (no big empty bottom band). Exception: archetypes whose whole point is scrolling.

## Style
DARK, cool, clear, modern, hi-tech — UNLESS the assigned **art-style's identity is light**
(paper, e-ink, risograph, Material-You, claymorphism, origami) — then lean fully into that style.
Each template must be a **genuinely different LAYOUT archetype** — NEVER the generic "row of equal
cards" grid. Make it look like a real polished product screenshot: deliberate hierarchy, type scale,
spacing, one clear focal point.

## Brand + consistent sample data (use the same facts everywhere; show whatever subset fits)
- Wordmark: **JARVIS**. Status: `● Online · Opus · 20:34 · Tue Jun 09`.
- Greeting: **"Good evening, Shay."** Wake phrase: "Hey Jarvis". Input: "Ask Jarvis, or type a /command…".
- Money: **Net this month $3,840 · ROI 4.2× · ▲18%** · AI cost **$58 / $80** budget.
  Revenue sources: Stripe subs $1,240, Gumroad $890, Lemon Squeezy $310, Newsletter $400.
- 3 things **need you**: CRITICAL "Send invoice → 14 customers" ($1,240, EA via Resend);
  HIGH "Publish SaaS starter → Gumroad $39"; HIGH "Deploy micro-API → Cloudflare".
- 2 **jobs running**: "habit-tracker app" 72% (Code agent); "research note-taking niche" 40% (Scout).
  newsletter #14 queued.
- System: **51 agents · 26 connectors (9 live) · 7 products live**.
- Proactive suggest: 'Double down: "REST API + auth" guide drove 61% of traffic'.
Accent colors: tasteful palette per screen (cyan/blue/violet/green/amber on-brand); art-style screens
use that style's signature palette.

## Quality references — READ THESE FIRST, then match or exceed
- `/Users/shaymarko/Desktop/jarvis v3/mockup/t76.html` (analytics chart hero)
- `/Users/shaymarko/Desktop/jarvis v3/mockup/t51.html` (SVG sankey data-viz)
- `/Users/shaymarko/Desktop/jarvis v3/mockup/t80.html` (Swiss typographic grid)
- `/Users/shaymarko/Desktop/jarvis v3/mockup/t60.html` (newspaper / editorial)

## Rules
Do NOT render, screenshot, or start any server — ONLY write the HTML files. Make each of your assigned
screens visually distinct from your others. When done, reply with one line per file:
`tNN ✓ <2-4 word label>`.
