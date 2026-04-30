# MS-75: Bookmarks — save full article with encouragement to Room and Bookmarks screen

## What was built

Users can now bookmark any article from the HeadlineDetail screen or the History screen. Bookmarked articles appear in a dedicated Bookmarks screen (accessed from the You tab → Saved). Tapping a bookmark navigates to the full HeadlineDetail screen. The bookmark state updates reactively across all three screens without any restart.

## Data layer changes

**EncouragementEntity** (version 9 → 10)
- Added `bookmarked: Boolean = false` field. Default `false` means existing rows are unaffected after the destructive migration.
- Room schema exported to `shared/schemas/.../10.json`.

**EncouragementDao**
- `getBookmarked(): Flow<List<EncouragementEntity>>` — queries `WHERE bookmarked = 1`, used by `BookmarksViewModel`.
- `observeBookmarkState(articleUrl: String): Flow<Boolean>` — returns a live `Flow<Boolean>` for a single row; used by `HeadlineDetailViewModel` to keep the icon in sync.
- `toggleBookmark(articleUrl: String)` — SQL `UPDATE ... SET bookmarked = NOT bookmarked`; both Android and iOS use `fallbackToDestructiveMigration` so no explicit migration object is needed.

**EncouragementRepository**
- Added `observeIsBookmarked(articleUrl: String): Flow<Boolean>` and `toggleBookmark(articleUrl: String)` to the domain interface and `EncouragementRepositoryImpl`.
- `HeadlineDetailViewModel` calls both methods via the repository to stay consistent with its existing abstraction boundary.

## UI changes

**HeadlineDetailScreen / Contract / ViewModel**
- `UiState.Success` gained `isBookmarked: Boolean = false`.
- `Intent.ToggleBookmark` triggers `encouragementRepository.toggleBookmark(articleUrl)`.
- `observeBookmark()` coroutine collects `observeIsBookmarked()` and patches `isBookmarked` onto the current `Success` state reactively.
- The back row now contains a `Row(SpaceBetween)` with the match theme text and a filled/outlined bookmark icon. The icon only appears once the encouragement has loaded (not during the loading animation or error state).

**HistoryScreen / Contract / ViewModel**
- `HistoryItem` gained `isBookmarked: Boolean = false`.
- `Intent.ToggleBookmark(articleUrl)` was added; `HistoryViewModel` calls `encouragementDao.toggleBookmark` directly (consistent with the existing DAO-direct pattern for History).
- Cards now show a bookmark icon on the right; tapping toggles the state. The card body shifts to a `Row(SpaceBetween)` layout to accommodate the icon.

**Bookmarks feature (new files)**
- `BookmarksContract` — Loading / Empty / Success states; `Intent.ToggleBookmark`.
- `BookmarksViewModel` — collects `encouragementDao.getBookmarked()` reactively; handles the remove-bookmark intent.
- `BookmarksScreen` — mirrors `HistoryScreen` layout: back row header, lazy list of `BookmarkCard`s (always show filled bookmark icon), and an empty state.
- Wired in `AppModule` and `MediaSageScaffold`. The existing `Route.Bookmarks` and `navigateToBookmarks()` in `MediaSageAppState` were already in place.

## Reactive bookmark pattern

All three screens (`HeadlineDetail`, `History`, `Bookmarks`) read bookmark state from Room flows. Because Room emits on every write, toggling a bookmark on any one screen is immediately reflected on the others — no restart required.

```
EncouragementDao.toggleBookmark(url)
    ↓ UPDATE … SET bookmarked = NOT bookmarked
    ↓ Room emits to all active flows
HeadlineDetailViewModel.observeBookmark() → patches UiState.Success.isBookmarked
HistoryViewModel.loadHistory()             → getAll() already includes bookmarked field
BookmarksViewModel.loadBookmarks()         → getBookmarked() query re-emits
```

## Testing

- `EncouragementRepositoryTest` — two new tests: `toggleBookmarkFlipsBookmarkedState` (toggles twice and verifies round-trip) and `observeIsBookmarkedReturnsFalseForUnknownUrl`.
- `HistoryViewModelTest.FakeEncouragementDao` — added the three new DAO methods to satisfy the interface.
- All other existing tests continue to pass unchanged.
