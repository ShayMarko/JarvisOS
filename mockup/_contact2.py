#!/usr/bin/env python3
DIR = "/Users/shaymarko/Desktop/jarvis-ui-templates"
OUT = "/Users/shaymarko/Desktop/jarvis v3/mockup"

labels = {
181:"Nightingale rose",182:"Violin dist.",183:"Ridgeline",184:"Hexbin",185:"Parallel coords",
186:"Dendrogram",187:"Voronoi cells",188:"Slope graph",189:"Bump rank-flow",190:"Cumulative flow",
191:"Radial clock-plot",192:"Contribution map",193:"Beeswarm",194:"Tag cloud",
195:"Banking app",196:"Crypto wallet",197:"Brokerage",198:"ATM screen",199:"Elevator panel",
200:"Vending machine",201:"ATC radar",202:"Sonar HUD",203:"Spaceship bridge",204:"Aircraft panel",
205:"Diving computer",206:"Vitals monitor",207:"Oscilloscope",208:"Seismograph",209:"Doppler radar",
210:"Resume / CV",211:"A4 invoice",212:"Certificate",213:"Passport ID",214:"Stamp sheet",
215:"Whiteboard",216:"Storyboard",217:"Sheet music",218:"Crossword",219:"Periodic table",
220:"Restaurant menu",221:"Ticker tape",222:"Type poster",223:"Index cards",
224:"Coverflow 3D",225:"Window cascade",226:"Dual rails",227:"Zoomable canvas",228:"Pull-to-refresh",
229:"Swipe actions",230:"Date-range dash",231:"3-col inspector",232:"Keyboard inbox",233:"Toast+drawer",
234:"Gallery lightbox",235:"Messenger 2-pane",236:"Voice talk-view",237:"A–Z long list",
238:"Art Deco",239:"Constructivist",240:"Frutiger Aero",241:"Op-art",242:"Mondrian",
243:"70s retro-future",244:"Dark academia",245:"Solarpunk",246:"Cyber-samurai",247:"Grainy gradient",
248:"Liquid chrome",249:"Maximalist",250:"Acid rave",251:"Dev terminal",252:"Fashion editorial",
253:"Globe arcs",254:"Particle field",255:"Galaxy spiral",256:"Aurora",257:"Aquarium",
258:"Terrain relief",259:"Day/night arc",260:"Fluid tanks",261:"Growing tree",262:"Terrarium",
263:"Tunnel vortex",264:"Low-poly crystal",265:"Smoke backdrop",266:"Neon-tube sign",
267:"Console home",268:"VR wrist menu",269:"Windshield HUD",270:"Smart mirror",271:"Speaker display",
272:"Fitness band",273:"Fridge hub",274:"Photo frame",275:"Dual-monitor",276:"Jarvis OS desktop",
277:"Dynamic Island",278:"Wallet passes",279:"Control Center",280:"Notif cascade",
}

sheets=[(181,200),(201,220),(221,240),(241,260),(261,280)]
for idx,(a,b) in enumerate(sheets,6):
    tiles=""
    for n in range(a,b+1):
        tiles+=f'<div class="t"><img src="file://{DIR}/t{n}.png"><div class="c"><b>T{n}</b> {labels.get(n,"")}</div></div>'
    html=f'''<!doctype html><html><head><meta charset="utf-8"><style>
*{{margin:0;box-sizing:border-box;font-family:system-ui,-apple-system,'Segoe UI',sans-serif}}
body{{width:1600px;background:#0a0b10;color:#e8eefc;padding:22px 26px}}
.h{{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:16px}}
.h b{{font-size:20px;letter-spacing:3px;color:#7ab6ff}}.h span{{color:#6b7690;font-size:13px}}
.grid{{display:grid;grid-template-columns:repeat(5,1fr);gap:14px}}
.t img{{width:100%;aspect-ratio:16/10;object-fit:cover;border:1px solid #232838;border-radius:8px;display:block;background:#000}}
.c{{font-size:12px;color:#aab4cc;padding:6px 2px 0}}.c b{{color:#fff}}
</style></head><body>
<div class="h"><b>J A R V I S &nbsp;·&nbsp; UI TEMPLATES</b><span>contact sheet {idx}/10 &nbsp;·&nbsp; T{a}–T{b} &nbsp;·&nbsp; full-res PNGs in ~/Desktop/jarvis-ui-templates/</span></div>
<div class="grid">{tiles}</div></body></html>'''
    with open(f"{OUT}/_sheet{idx}.html","w") as f: f.write(html)
    print(f"_sheet{idx}.html  T{a}-T{b}")
print("done")
