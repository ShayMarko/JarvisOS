import { useEffect, useRef, useState } from 'react'
import type { Win } from '../types'

/** A draggable, resizable HUD window shell. */
export function FloatingWindow({ win, onClose, onFocus, onMinimize, children }: { win: Win; onClose: () => void; onFocus: () => void; onMinimize?: () => void; children: React.ReactNode }) {
  const [pos, setPos] = useState({ x: win.x, y: win.y })
  const [w0, h0] = win.dim.split('×').map(Number)
  const [size, setSize] = useState({ w: w0 || 720, h: h0 || 520 })
  const drag = useRef<{ dx: number; dy: number } | null>(null)
  const rez = useRef<{ sx: number; sy: number; w: number; h: number } | null>(null)

  // Let an external arrange (cascade/tile) reposition + resize this window: when the Win's
  // x/y/dim props change, adopt them. Plain drags/resizes only touch local state, so they persist.
  useEffect(() => { setPos({ x: win.x, y: win.y }) }, [win.x, win.y])
  useEffect(() => {
    const [pw, ph] = win.dim.split('×').map(Number)
    if (pw && ph) setSize({ w: pw, h: ph })
  }, [win.dim])

  const onDown = (e: React.PointerEvent) => {
    onFocus()
    drag.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y }
    const move = (ev: PointerEvent) => { if (drag.current) setPos({ x: ev.clientX - drag.current.dx, y: ev.clientY - drag.current.dy }) }
    const up = () => { drag.current = null; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
  }
  // Drag the bottom-right corner to resize (clamped to sane bounds).
  const onResize = (e: React.PointerEvent) => {
    e.stopPropagation(); onFocus()
    rez.current = { sx: e.clientX, sy: e.clientY, w: size.w, h: size.h }
    const move = (ev: PointerEvent) => {
      if (!rez.current) return
      setSize({
        w: Math.max(360, Math.min(window.innerWidth - 40, rez.current.w + (ev.clientX - rez.current.sx))),
        h: Math.max(220, Math.min(window.innerHeight - 40, rez.current.h + (ev.clientY - rez.current.sy))),
      })
    }
    const up = () => { rez.current = null; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
  }

  return (
    <div className="window fade-in" style={{ left: pos.x, top: pos.y, zIndex: win.z, width: size.w, height: size.h }} onMouseDown={onFocus}>
      <div className="win-head" onPointerDown={onDown}>
        <span className="pip" />
        <span className="title">{win.title}</span>
        <span className="subtitle">{win.subtitle}</span>
        <span className="dim">{Math.round(size.w)}×{Math.round(size.h)}</span>
        {onMinimize && <button className="close minimize" title="Minimize to dock" onClick={(e) => { e.stopPropagation(); onMinimize() }}>—</button>}
        <button className="close" onClick={onClose}>✕</button>
      </div>
      <div className="win-body">{children}</div>
      <div className="win-resize" onPointerDown={onResize} title="Drag to resize" />
    </div>
  )
}
