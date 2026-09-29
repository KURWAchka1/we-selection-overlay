# Changelog 1.6.0

## Added

- Full-screen overlay palette editor opened from the overlay settings screen.
- Live preview for every supported WorldEdit CUI shape: Cuboid / Extend, Polygon 2D, Ellipsoid / Sphere, Cylinder, Convex / Polyhedron, and the dimension ruler.
- Per-element color and transparency controls with spectrum, hue, alpha, Hex, and RGBA inputs.
- Per-element line thickness controls for outline, grid, point, connector, and ruler elements.
- Top global thickness slider that is available while all adjustable elements share the same thickness value.
- Per-element reset buttons in the element list.
- Global reset menu with separate actions for resetting all colors, all thickness values, or the whole palette to defaults.
- Built-in overlay presets for balanced, high-contrast, subtle, precision, and large-selection workflows.
- Confirmation dialog before applying a preset, with Escape/cancel support.
- Adaptive editor layout for different screen sizes, including trimmed labels and compact controls where needed.
- Compact scroll layout for high Minecraft GUI Scale values.
- Saved configuration for custom overlay colors and line thickness values.

## Changed

- Selection preview now uses the same generated selection geometry as the in-world overlay, so the editor matches real Cuboid, Polygon, Ellipsoid, Cylinder, and Polyhedron rendering more closely.
- Preview rotation now uses one consistent orbit direction for all shape previews.
- RGBA numeric labels are aligned directly before their matching input fields.
- Documented recommended Minecraft GUI Scale: 2x.
