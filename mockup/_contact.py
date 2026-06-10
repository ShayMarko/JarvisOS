#!/usr/bin/env python3
import os
DIR = "/Users/shaymarko/Desktop/jarvis-ui-templates"
OUT = "/Users/shaymarko/Desktop/jarvis v3/mockup"

labels = {
81:"Treemap",82:"Chord diagram",83:"Gantt chart",84:"Candlestick",85:"Stream graph",86:"Force graph",
87:"Waterfall",88:"Bullet KPI",89:"Sparkline wall",90:"Sunburst",91:"Funnel",92:"Marimekko",
93:"Spreadsheet",94:"Terminal",95:"Code editor",96:"Git graph",97:"Slack channels",98:"Notion doc",
99:"Bloomberg",100:"Transit map",101:"Thermostat",102:"Smart-home",103:"CarPlay",104:"E-reader",
105:"Podcast",106:"Music library",107:"Photo masonry",108:"Video timeline",109:"Audio mixer",
110:"Recipe card",111:"Boarding pass",112:"Receipt",113:"Trading card",114:"Turntable",115:"Game HUD",
116:"RPG inventory",117:"Skill tree",118:"Arcade score",119:"Calculator",120:"Weather",121:"Bento box",
122:"Bottom-sheet",123:"iOS tab bar",124:"Overlay menu",125:"Miller columns",126:"⌘K palette",
127:"Wizard stepper",128:"Onboarding",129:"Widget dash",130:"Landing scroll",131:"Mini-rail flyout",
132:"Dual compare",133:"PiP assistant",134:"Mega-menu",135:"Radial pie",136:"Kanban swimlanes",
137:"Split panes",138:"Turn-by-turn",139:"Brutalist",140:"Neumorphism",141:"Claymorphism",142:"CRT terminal",
143:"Vaporwave",144:"Memphis 80s",145:"Bauhaus",146:"Cyberpunk",147:"Blueprint",148:"Hand-drawn",
149:"Origami",150:"Material You",151:"Fluent acrylic",152:"Skeuomorphic",153:"Dark luxury",154:"Minimalist",
155:"ASCII art",156:"Pixel-art",157:"E-ink",158:"Holographic",159:"Aurora mesh",160:"Liquid blob",
161:"3D wireframe",162:"Isometric HQ",163:"Topographic",164:"Comic book",165:"Glitch",166:"Duotone",
167:"Risograph",168:"Stained glass",169:"Smartwatch",170:"TV 10-foot",171:"Foldable",172:"Tablet split",
173:"Ambient",174:"Kiosk touch",175:"Spatial/Vision",176:"Status-bar",177:"Lock-screen",178:"Mission-ctrl",
179:"Timeline scrub",180:"Mind-map",
}

sheets = [(81,100),(101,120),(121,140),(141,160),(161,180)]
for idx,(a,b) in enumerate(sheets,1):
    tiles=""
    for n in range(a,b+1):
        tiles += f'''<div class="t"><img src="file://{DIR}/t{n}.png"><div class="c"><b>T{n}</b> {labels.get(n,"")}</div></div>'''
    html=f'''<!doctype html><html><head><meta charset="utf-8"><style>
*{{margin:0;box-sizing:border-box;font-family:system-ui,-apple-system,'Segoe UI',sans-serif}}
body{{width:1600px;background:#0a0b10;color:#e8eefc;padding:22px 26px}}
.h{{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:16px}}
.h b{{font-size:20px;letter-spacing:3px;color:#7ab6ff}}.h span{{color:#6b7690;font-size:13px}}
.grid{{display:grid;grid-template-columns:repeat(5,1fr);gap:14px}}
.t img{{width:100%;aspect-ratio:16/10;object-fit:cover;border:1px solid #232838;border-radius:8px;display:block;background:#000}}
.c{{font-size:12px;color:#aab4cc;padding:6px 2px 0}}.c b{{color:#fff}}
</style></head><body>
<div class="h"><b>J A R V I S &nbsp;·&nbsp; UI TEMPLATES</b><span>contact sheet {idx}/5 &nbsp;·&nbsp; T{a}–T{b} &nbsp;·&nbsp; full-res PNGs in ~/Desktop/jarvis-ui-templates/</span></div>
<div class="grid">{tiles}</div></body></html>'''
    with open(f"{OUT}/_sheet{idx}.html","w") as f: f.write(html)
    print(f"_sheet{idx}.html  T{a}-T{b}")
print("done")
