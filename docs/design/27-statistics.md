# 27: Print statistics card on the History tab

A collapsed card at the top of the History list expands to show print counts, hours and grams for a chosen period: this month (default), the last 30 days, or all time.
It reads `GET archives/stats` only and has no offline copy; without the server it shows the error with Retry.
The card is part of the list, so it appears only when the History list has entries.
Built.

## Decisions

- Three fixed periods; there is no custom date range.
- `StatsPeriod` turns a period and today's date into a date range; the device time zone id goes in `tz`, so "this month" is the user's calendar month.
  "Last 30 days" is today and the 29 days before it.
- Nothing is requested until the card is expanded.
- Bars are plain Compose boxes, no chart library.
- Cost, energy and accuracy fields of the reply are ignored.
- The success rate is successful divided by successful plus failed; with zero finished prints no percentage shows.

## Design

```text
 collapsed                                 expanded
 +----------------------------------+      +--------------------------------------------+
 | Statistics · This month      [v] |      | Statistics                             [^] |
 +----------------------------------+      | [This month] [Last 30 days] [All time]     |
                                           | 24 prints   92% succeeded   31.0 h   1.2 kg|
                                           | By printer   X2D ######## 18   P1S #### 6  |
                                           | By material  PLA ######## 20   PETG ## 4   |
                                           | Failed 2 · Cancelled 0                     |
                                           +--------------------------------------------+
 error: one line + Retry.   zero prints: "No prints in this period."
```

```text
 StatsCard expands / period chip / Retry -> StatsViewModel.refresh or select(period)
   -> StatsRepository.getStats(period)  (StatsRepositoryImpl)
        StatsPeriod.range(today) -> DateRange
        -> StatsApi.getStats: GET archives/stats?date_from&date_to&tz -> ArchiveStatsDto
        -> toDomain -> PrintStats
   -> StatsState (Loading, Success, Error) -> StatsCard
 A newer request cancels the older one, so a slow reply cannot overwrite a newer period.
```

## Behavior

- `ArchiveStatsDto` fields are all optional and unknown fields are ignored; missing numbers become 0.
- Printer ids are named from the reply's id-to-name map, else "Printer 3".
- By-printer and by-material lists drop zero counts and keep the four largest entries, largest first.
- `HistoryContent` shows `StatsCard` as the first item of the list.
- Whether the server counts a reprint as another print is not verified.

## Tests

Unit tests cover the mapper (missing and extra fields, names, top four, zero prints), the period ranges, the request parameters and the ViewModel (period change reloads, stale reply dropped, error).
The card's look is not covered by tests.
