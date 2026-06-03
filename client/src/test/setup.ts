import '@testing-library/jest-dom'

// jsdom doesn't implement scrollIntoView; components that auto-scroll call it on mount.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
