# Sakhi Android

Android is blocked on the shared local-database migration.

Current execution order:

1. Migrate iOS Realm data to the shared Room KMP store.
2. Verify no data loss with shared migration audits.
3. Start the Android app shell on the frozen shared schema.

The active implementation plan lives in [`glittery-conjuring-feather.md`](./glittery-conjuring-feather.md).
