# Key data models

Traveler stores a trip as one document. These UML class diagrams show its core relationships; optional descriptive fields are omitted. Class names match the [Kotlin models](../app/src/main/kotlin/dev/tlong/traveler/model/Trip.kt).

## The trip document

```mermaid
classDiagram
    class Trip {
        String id
        Int revision
        String title
        String startDate
        String endDate
    }
    class Stay {
        String id
        String name
        String arrive
        String depart
    }
    class Activity {
        String id
        String stayId
        String name
        String userStatus
    }
    class Day {
        String date
        String stayId
        String kind
    }
    class PlanItem {
        String activityId
        String slot
        String time
        String status
    }
    class Transfer {
        String id
        String from
        String to
        String date
    }
    class Commitment {
        String id
        String date
        String activityId
        Boolean booked
    }
    Trip "1" *-- "0..*" Stay
    Trip "1" *-- "0..*" Activity
    Trip "1" *-- "0..*" Day
    Trip "1" *-- "0..*" Transfer
    Trip "1" *-- "0..*" Commitment
    Trip "1" *-- "0..*" PlanningDetail
    Stay "1" *-- "0..1" Lodging
    Stay "1" *-- "0..1" WorkRhythm
    Activity "0..*" --> "1" Stay : stayId
    Day "0..*" --> "0..1" Stay : stayId
    Day "1" *-- "0..*" PlanItem : plan
    PlanItem "0..*" --> "1" Activity : activityId
    Transfer --> Stay : from / to IDs
    Commitment --> Stay : optional stayId
    Commitment --> Activity : optional activityId
    Activity "1" *-- "0..1" Booking
    Transfer "1" *-- "0..1" Booking
```

- **Itinerary** is a view of days and their plans, not a separate stored class. Activities belong to the trip’s pool and refer to a stay; plan items refer to activities rather than copying them.
- **Lodging, transfers and commitments** carry reservation facts. `PlanningDetail` carries logistics research and candidate options, keyed to bookable items; see [its model](../app/src/main/kotlin/dev/tlong/traveler/model/PlanningDetail.kt).
- Solid diamonds mean ownership within the document; arrows mean references by ID. IDs remain stable across revisions; days are identified by date.

## Daily Activity Patch

A patch is an `ActivityFile` (`traveler-activities`) with dated plans. The same format without days simply adds activity ideas.

```mermaid
classDiagram
    class ActivityFile {
        String format = traveler-activities
        String tripId (optional)
    }
    class FileDay {
        String date
    }
    ActivityFile "1" *-- "0..*" Activity : activities
    ActivityFile "1" *-- "0..*" FileDay : days
    FileDay "1" *-- "0..*" PlanItem : plan
    PlanItem --> Activity : activityId in file
    ActivityFile ..> Trip : import into
    FileDay ..> Day : refresh suggestions by date
```

Import matches activities to stays and existing ideas. Each selected date replaces that day’s suggestions while preserving completed items, booked activities and user-created entries. Dates outside the trip are skipped. See the [activities-file contract](trip-format.md) for fields and matching rules.

## What stays on the phone

| Stored alongside the trip | Purpose |
| --- | --- |
| `TripRow.baseJson` / `localJson` | Last imported document and the traveler’s edited document: the basis for merging a revision. |
| `SnapshotRow` | Previous versions for history and recovery. |
| `TripRow.notes`, `checklist`, `work` | Personal trip notes, preparation lists and work-block adjustments. Included in backups, omitted from trip exports. |

See [Architecture](ARCHITECTURE.md) for persistence and merge behavior, or the [format reference](trip-format.md) for the complete field contract.
