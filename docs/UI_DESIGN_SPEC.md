# UI specification — Truck Photo Inspection

Status: APPROVED by product owner, 2026-11-06.

## Approved main dashboard reference
The approved visual reference is the latest smartphone mockup shown in the conversation (dark navy dashboard with a truck header, three statistic tiles, prominent blue "Начать новый осмотр" button, recent inspections with status pills, and bottom navigation).

The reference is a design target, not evidence of implemented features. Do not silently replace it with a different design.

## Design language
- Native Android application: Kotlin + Jetpack Compose.
- Apply Ant Design visual principles in native Compose: dark navy/graphite surfaces, bright blue primary actions, consistent cards, typography, spacing, status colors, iconography and interaction states.
- Main dashboard: brand/truck header; counts for all, completed and in-progress inspections; primary create-inspection action; recent inspection cards with number, model, date/time and status; bottom navigation for Home, Inspections, Statistics, Settings.
- Use real local data, not hardcoded demo counts or vehicle photos.
- Do not add PDF export: explicitly out of scope.

## Mandatory responsiveness — ALL screens and elements
Every screen, dialog, form, button, card, navigation item, photo preview, list and empty/error/loading state MUST adapt to the actual device size and configuration.

Acceptance requirements:
1. Support narrow Android phones, typical phones, large phones, tablets, portrait and landscape; no fixed screen-size assumptions.
2. No clipped text, off-screen controls, overlapping elements or inaccessible actions. Respect system insets, status/navigation bars and display cutouts.
3. Use Compose adaptive layouts (BoxWithConstraints, WindowSizeClass or equivalent), weight/fillMaxWidth, sensible max widths, scrolling and responsive columns/grids; avoid rigid pixel dimensions.
4. Scale gracefully with user font-size/display-size accessibility settings; use sp for text and dp for layout, allow text to wrap.
5. For narrow widths, statistics tiles and action rows must wrap/stack or scroll appropriately; navigation labels and controls remain usable.
6. On wide screens/tablets, use available space intelligently, limiting content width or switching to multi-column layouts where appropriate.
7. All tap targets should be accessible (typically at least 48dp); preserve contrast and readable labels.
8. Test at multiple viewport sizes, font scales, orientations and keyboard-visible states before marking a screen complete.

## Implementation plan
1. Dashboard and shared design tokens/navigation.
2. New/edit inspection form.
3. Inspection checklist and capture.
4. Photo review and manual approval.
5. Completion/history/report-without-PDF.
6. Settings and responsive QA.

Implementation is incremental; this document records the approved requirements, not completion.
