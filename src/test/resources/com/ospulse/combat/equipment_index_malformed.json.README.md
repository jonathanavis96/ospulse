# equipment_index_malformed.json

Test-only fixture, hand-written. Not game data: two real ids (whip, twisted
bow) with their names and weapon slot, plus deliberately malformed rows that
`EquipmentIndexRepositoryTest` expects `EquipmentIndexRepository.loadFromResource`
to skip rather than fail on.
