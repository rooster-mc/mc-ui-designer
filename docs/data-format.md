# Data Format

The exporter writes a JSON array of chest designs. The schema below is the
authoritative one; the original draft in `TODO-QUEUE.md` was not valid JSON and
used mixed casing, so it was normalised.

## Schema

```jsonc
[
  {
    "name": "Shop",          // required; non-blank and unique in the file
    "rows": 6,               // 1..6; single chest = 3, double chest = 6
    "content": [
      {
        "row": 1,            // 1..rows
        "slots": [
          {
            "slot": 1,       // 1..9, left-to-right within the row
            "item": "minecraft:stone",  // namespaced item id
            "name": "Stone"             // custom item display name; omitted when none/blank
          }
        ]
      }
    ]
  }
]
```

## Rules

- `content` is sorted by `row`; `slots` sorted by `slot`.
- The scanner does not emit slots with no item; a row with no items is omitted
  entirely by the exporter.
- `name` on a chest is required and non-blank; the file is rejected (nothing
  written) if any exported chest has no name or if two names collide. Duplicate
  detection trims surrounding whitespace and ignores case, but the original
  casing is preserved in the output. `name` is the importer's identity for the
  chest.
- `name` on a slot is omitted when the item has no custom display name. A blank
  `name` (`""` or whitespace-only) is treated as absent by the exporter on
  slots.
- `name` on a slot is the item's custom display name (the name shown in the
  item tooltip), not the material name.
- Chests are ordered deterministically: by the world position of their
  canonical (lowest `x`, then `y`, then `z`) block. Chests with equal positions
  keep their input order (stable sort); canonical positions are expected to be
  unique in practice.
- `rows` derives from the physical chest: single chest inventories have 27
  slots (3 rows), double chests have 54 (6 rows).
- The array must contain at least one chest. An empty design (`[]`) is rejected
  when read (naming the file), so `scaffold` never reports a green no-op.

## Kotlin model

```kotlin
@Serializable
data class UiChest(
    val name: String,
    val rows: Int,
    val content: List<UiRow>,
    @Transient val position: BlockPos? = null,
)

@Serializable
data class UiRow(val row: Int, val slots: List<UiSlot>)

@Serializable
data class UiSlot(val slot: Int, val item: String, val name: String? = null)

data class BlockPos(val x: Int, val y: Int, val z: Int) : Comparable<BlockPos>
```

`position` is `@Transient` (never serialized): it carries the canonical block
position so the exporter can order chests, and it is `null` by default so a
missing position fails fast (`requireNotNull`) instead of silently falling back
to input order. `name` is required: the save command calls `validateForExport`
and refuses to invoke the exporter unless every name is non-blank and unique
(trimmed, case-insensitive), so no file is written otherwise. `JsonExporter`
itself only orders entries and strips blank *slot* names.

## Delta from the draft

| Draft | Here | Why |
|---|---|---|
| `Name` / `Rows` / `Content` | `name` / `rows` / `content` | consistent camelCase |
| `Row` / `Slot` / `Item` | `row` / `slot` / `item` | consistent camelCase |
| `name` inside item | `name` | already camelCase, kept |
| `Content: [{ Row: 1, [{...}] }]` | `content: [{ row, slots: [...] }]` | draft was missing the key for the slot array |
| unnamed chest | `name` required and unique | names are the importer's identity; an unnamed entry is unreferenceable |
