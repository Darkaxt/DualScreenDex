# Live Gen III Party Design

## Goal

Show the current Gen III party immediately from live core memory, including before the player performs an in-game save, while retaining SaveRAM as the safe fallback.

## Authority and data flow

- The ROM parser identifies `gPlayerPartyCount` and `gPlayerParty` from compiled EWRAM reference evidence. It does not match ROM names, hashes, or fixed addresses.
- A layout is published only when one reference-ranked count/party pair is uniquely authoritative. Missing or ambiguous evidence leaves the fields absent.
- While a matching Gen III session is active, the memory coordinator reads one count byte and six 100-byte party records. The existing checksum-validating `Gen3PokemonCodec` decodes them.
- A completely decoded live party replaces only the current party portion of the knowledge ledger. SaveRAM remains authoritative for stored Pokémon, Pokédex flags, and saved location.
- Live non-egg party members become seen and caught. Invalid or incomplete live records are ignored, preserving the last valid state and SaveRAM fallback.

## Runtime behavior

- A live count of zero truthfully clears the current team.
- Party changes with the same count are detected because the bounded record window is read on each completed memory sample.
- Disconnecting or switching ROMs clears live-party authority. A later SaveRAM snapshot remains usable.
- The feature adds no memory writes and no new Android permissions.

## Verification

- Synthetic parser evidence proves unique selection and ambiguous fail-closed behavior.
- The exact Modern Emerald 3.5 control proves the source-authoritative live addresses.
- Decoder tests cover valid, empty, corrupt, and partial parties.
- Coordinator tests prove the bounded reads and publication.
- Runtime tests prove live authority over stale SaveRAM and preservation of boxed ownership.
- The final device check starts from the current unsaved game and requires the starter to appear in the Team filter.
