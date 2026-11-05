# 0014 — Material 3 client redesign

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The first client proved the data path. The screens used default type, thin rows,
and mixed spacing. Covers are the main content, but the chrome competed with
them. Android 12 and later can also supply a wallpaper palette.

The reader is stable and must not change. The redesign is limited to the browse
screens, library screens, settings, and shared components.

## Decisions

1. **Use a Material 3 theme with a lavender palette.** The palette matches the
   app icon. The theme supports light, dark, and system modes. A dynamic color
   switch is available on Android 12 and later. It is off by default to keep the
   brand identity.
2. **Share one component language.** `CoverImage`, `BookCard`, `SeriesCard`,
   `BookRow`, `ContinueCard`, `StateMessage`, `SectionTitle`, `Tag`, and
   `MessageBanner` define the spacing, shapes, and states. Screens compose these
   parts. They do not draw one-off rows.
3. **Keep five bottom tabs and the stack.** The tabs are Library, Recent,
   History, Browse, and More. Custom vector icons replace the generic core
   icons. Detail screens push over a tab and hide the navigation bar.
4. **Make discovery and status visible.**
   - Library has a persistent search field, a series/volume control, counts,
     a skeleton state, and pull-to-refresh.
   - Browse uses source and format chips, release cards with a format tile and
     health counts, and a downloads badge.
   - Recent groups books by day. History uses progress cards.
   - Downloads is a separate screen behind the top-right icon in Browse.
5. **Use cards for settings.** Server, appearance, library, statistics, shelves,
   and maintenance are separate cards. Statistics use a small grid instead of
   label rows.

## Consequences

- All screens share one type scale, shape scale, and color role.
- The light theme, dark theme, and wallpaper palette all use the same layout.
- Status bars switch icon color with the app theme, not only the system theme.
- The app remains usable on a phone. The grid still uses the user's column
  setting and does not adapt to tablets.
- Dynamic color is a preference. The default purple palette is stable for
  screenshots and for the brand.

## Alternatives rejected

- **Keep the Mihon-style dense list.** It works, but it hides the covers and
  gives the app no hierarchy.
- **Adopt Navigation Compose for this change.** The current stack is small and
  works. A navigation library adds no visible value here.
- **Enable dynamic color by default.** Wallpaper colors can differ from the icon
  and from the cover art. The user can enable them.
- **Use a different font.** The default Material 3 type scale is legible and
  needs no font assets.
