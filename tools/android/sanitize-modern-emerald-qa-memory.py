#!/usr/bin/env python3
"""Build the debug-only Modern Emerald raw-memory QA scenario asset."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import struct
from dataclasses import dataclass
from pathlib import Path

SOURCE_SHA256 = "40958796e0acd76bac20aef3c484d451685fffa255c45a5eec57df6a0511f5a5"
ROM_SHA256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
ROM_CRC32 = "8C7DBECA"
ROM_BASENAME = "Modern Emerald (v3.5).gba"
EWRAM_BASE = 0x02000000
EWRAM_SIZE = 0x40000
IWRAM_BASE = 0x03000000
IWRAM_SIZE = 0x8000
SAVE_BLOCK_1_POINTER = 0x030036F0
SAVE_BLOCK_2_POINTER = 0x030036F4
STORAGE_POINTER = 0x030036F8
PLAYER_PARTY = 0x0201D9C8
BATTLE_MONS = 0x0200143C
PARTY_RECORD_SIZE = 100
BOX_RECORD_SIZE = 80
BATTLE_MON_SIZE = 88
PARTY_CAPACITY = 6
BOX_COUNT = 15
BOX_CAPACITY = 30
BOX_NAME_LENGTH = 9
POKEMON_STORAGE_RECORDS_OFFSET = 4
SECRET_BASE_COUNT = 20
SECRET_BASE_SIZE = 0xA0
FRAME_IDS = (
    "overworld-1",
    "battle-start",
    "move-selected",
    "move-executed",
    "battle-end",
    "overworld-2",
)
EXPECTED_LABELS = (
    "OVERWORLD",
    "BATTLE_START",
    "MOVE_SELECTED",
    "MOVE_EXECUTED",
    "BATTLE_END",
    "OVERWORLD",
)


@dataclass
class MemoryFrame:
    frame_id: str
    ewram: bytearray
    iwram: bytearray
    protected_ranges: list[tuple[int, int]]


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def encode_gen3(value: str, size: int) -> bytes:
    encoded = bytearray()
    for character in value:
        if "A" <= character <= "Z":
            encoded.append(0xBB + ord(character) - ord("A"))
        elif "0" <= character <= "9":
            encoded.append(0xA1 + ord(character) - ord("0"))
        elif character == " ":
            encoded.append(0x00)
        else:
            raise ValueError(f"unsupported Gen III QA character: {character!r}")
    if len(encoded) >= size:
        raise ValueError("Gen III QA replacement must leave room for a terminator")
    return bytes(encoded + bytearray([0xFF] * (size - len(encoded))))


def read_pointer(iwram: bytearray, address: int) -> int:
    offset = address - IWRAM_BASE
    if offset < 0 or offset + 4 > len(iwram):
        raise ValueError("runtime pointer address is outside IWRAM")
    return struct.unpack_from("<I", iwram, offset)[0]


def ewram_offset(address: int, size: int = 1) -> int:
    offset = address - EWRAM_BASE
    if offset < 0 or offset + size > EWRAM_SIZE:
        raise ValueError("runtime structure is outside EWRAM")
    return offset


def looks_like_personal_text(value: bytes) -> bool:
    prefix = value.split(b"\xff", 1)[0]
    if len(prefix) < 2:
        return False
    return any(0xA1 <= byte <= 0xEE for byte in prefix)


def replacement_for(size: int, label: str) -> bytes:
    if label == "box":
        return encode_gen3("QA BOX", size)
    if label == "pokemon":
        return encode_gen3("", size)
    return encode_gen3("QA", size)


def sanitize_field(
    memory: bytearray,
    offset: int,
    size: int,
    label: str,
    signatures: dict[bytes, bytes],
) -> None:
    if offset < 0 or offset + size > len(memory):
        raise ValueError("sensitive field is outside captured memory")
    original = bytes(memory[offset : offset + size])
    replacement = replacement_for(size, label)
    if original != replacement and looks_like_personal_text(original):
        signatures.setdefault(original, replacement)
    memory[offset : offset + size] = replacement


def zero_range(memory: bytearray, start: int, end: int) -> None:
    if start < 0 or end < start or end > len(memory):
        raise ValueError("sensitive range is outside captured memory")
    memory[start:end] = b"\x00" * (end - start)


def protect(frame: MemoryFrame, start: int, end: int) -> None:
    if start < 0 or end < start or end > len(frame.ewram):
        raise ValueError("protected Pokémon range is outside EWRAM")
    frame.protected_ranges.append((start, end))


def sanitize_pokemon_record(
    frame: MemoryFrame,
    record_offset: int,
    record_size: int,
    nickname_offset: int,
    nickname_size: int,
    ot_offset: int,
    ot_size: int,
    signatures: dict[bytes, bytes],
) -> None:
    protect(frame, record_offset, record_offset + record_size)
    sanitize_field(frame.ewram, record_offset + nickname_offset, nickname_size, "pokemon", signatures)
    sanitize_field(frame.ewram, record_offset + ot_offset, ot_size, "trainer", signatures)


def sanitize_frame(frame: MemoryFrame, signatures: dict[bytes, bytes]) -> None:
    save_block_1 = read_pointer(frame.iwram, SAVE_BLOCK_1_POINTER)
    save_block_2 = read_pointer(frame.iwram, SAVE_BLOCK_2_POINTER)
    storage = read_pointer(frame.iwram, STORAGE_POINTER)
    save1 = ewram_offset(save_block_1)
    save2 = ewram_offset(save_block_2)
    storage_offset = ewram_offset(
        storage,
        POKEMON_STORAGE_RECORDS_OFFSET + BOX_COUNT * BOX_CAPACITY * BOX_RECORD_SIZE + BOX_COUNT * BOX_NAME_LENGTH,
    )

    sanitize_field(frame.ewram, save2, 8, "trainer", signatures)
    frame.ewram[save2 + 0x0A : save2 + 0x0E] = b"\x12\x34\x56\x78"
    zero_range(frame.ewram, save2 + 0xB0, min(save2 + 0x1000, len(frame.ewram)))

    for slot in range(PARTY_CAPACITY):
        sanitize_pokemon_record(
            frame,
            save1 + 0x238 + slot * PARTY_RECORD_SIZE,
            PARTY_RECORD_SIZE,
            8,
            10,
            20,
            7,
            signatures,
        )

    zero_range(frame.ewram, save1 + 0x1A9C, save1 + 0x271C)
    zero_range(frame.ewram, save1 + 0x27CC, save1 + 0x3716)
    zero_range(frame.ewram, save1 + 0x3728, save1 + 0x3B24)
    zero_range(frame.ewram, save1 + 0x3B58, save1 + 0x3D5A)
    zero_range(frame.ewram, save1 + 0x3D70, save1 + 0x3D88)

    party_offset = ewram_offset(PLAYER_PARTY, PARTY_CAPACITY * PARTY_RECORD_SIZE)
    for slot in range(PARTY_CAPACITY):
        sanitize_pokemon_record(
            frame,
            party_offset + slot * PARTY_RECORD_SIZE,
            PARTY_RECORD_SIZE,
            8,
            10,
            20,
            7,
            signatures,
        )

    for box in range(BOX_COUNT):
        for slot in range(BOX_CAPACITY):
            sanitize_pokemon_record(
                frame,
                storage_offset + POKEMON_STORAGE_RECORDS_OFFSET + (box * BOX_CAPACITY + slot) * BOX_RECORD_SIZE,
                BOX_RECORD_SIZE,
                8,
                10,
                20,
                7,
                signatures,
            )
    box_names_offset = storage_offset + POKEMON_STORAGE_RECORDS_OFFSET + BOX_COUNT * BOX_CAPACITY * BOX_RECORD_SIZE
    for box in range(BOX_COUNT):
        sanitize_field(
            frame.ewram,
            box_names_offset + box * BOX_NAME_LENGTH,
            BOX_NAME_LENGTH,
            "box",
            signatures,
        )

    battle_offset = ewram_offset(BATTLE_MONS, 4 * BATTLE_MON_SIZE)
    for battler in range(4):
        sanitize_pokemon_record(
            frame,
            battle_offset + battler * BATTLE_MON_SIZE,
            BATTLE_MON_SIZE,
            0x30,
            11,
            0x3C,
            8,
            signatures,
        )

    for secret_base in range(SECRET_BASE_COUNT):
        base = save1 + 0x1A9C + secret_base * SECRET_BASE_SIZE
        sanitize_field(frame.ewram, base + 2, 7, "trainer", signatures)


def range_intersects(start: int, end: int, ranges: list[tuple[int, int]]) -> bool:
    return any(start < protected_end and protected_start < end for protected_start, protected_end in ranges)


def replace_signatures(memory: bytearray, protected_ranges: list[tuple[int, int]], signatures: dict[bytes, bytes]) -> None:
    for original, replacement in signatures.items():
        if original == replacement:
            continue
        cursor = 0
        while True:
            offset = memory.find(original, cursor)
            if offset < 0:
                break
            end = offset + len(original)
            if not range_intersects(offset, end, protected_ranges):
                memory[offset:end] = replacement
            cursor = end


def replace_unprotected_signatures(frame: MemoryFrame, signatures: dict[bytes, bytes]) -> None:
    replace_signatures(frame.ewram, frame.protected_ranges, signatures)
    replace_signatures(frame.iwram, [], signatures)


def validate_memory_signatures(
    memory: bytearray,
    protected_ranges: list[tuple[int, int]],
    signatures: dict[bytes, bytes],
) -> None:
    for original, replacement in signatures.items():
        if original == replacement:
            continue
        cursor = 0
        while True:
            offset = memory.find(original, cursor)
            if offset < 0:
                break
            end = offset + len(original)
            if not range_intersects(offset, end, protected_ranges):
                raise ValueError("an original sensitive field remains in sanitized memory")
            cursor = end


def validate_sanitization(frames: list[MemoryFrame], signatures: dict[bytes, bytes]) -> None:
    for frame in frames:
        validate_memory_signatures(frame.ewram, frame.protected_ranges, signatures)
        validate_memory_signatures(frame.iwram, [], signatures)

        for pointer_address in (SAVE_BLOCK_1_POINTER, SAVE_BLOCK_2_POINTER, STORAGE_POINTER):
            ewram_offset(read_pointer(frame.iwram, pointer_address))


def decode_source(source_path: Path) -> tuple[dict, list[MemoryFrame]]:
    source_bytes = source_path.read_bytes()
    if sha256_bytes(source_bytes) != SOURCE_SHA256:
        raise ValueError("source memory dump SHA-256 does not match the reviewed capture")
    root = json.loads(source_bytes)
    if root.get("containsRawMemory") is not True:
        raise ValueError("source memory dump does not contain raw memory")
    snapshots = root.get("snapshots")
    if not isinstance(snapshots, list) or len(snapshots) != len(FRAME_IDS):
        raise ValueError("source memory dump does not contain the expected six snapshots")

    frames: list[MemoryFrame] = []
    for frame_id, expected_label, snapshot in zip(FRAME_IDS, EXPECTED_LABELS, snapshots, strict=True):
        if snapshot.get("label") != expected_label:
            raise ValueError("source memory snapshot order does not match the reviewed sequence")
        regions = {}
        for region in snapshot.get("regions", []):
            decoded = base64.b64decode(region["base64Bytes"], validate=True)
            if len(decoded) != region["size"] or sha256_bytes(decoded) != region["sha256"].lower():
                raise ValueError("source memory region integrity validation failed")
            regions[region["baseAddress"]] = decoded
        if set(regions) != {EWRAM_BASE, IWRAM_BASE}:
            raise ValueError("source memory snapshot does not contain exact EWRAM and IWRAM regions")
        if len(regions[EWRAM_BASE]) != EWRAM_SIZE or len(regions[IWRAM_BASE]) != IWRAM_SIZE:
            raise ValueError("source memory snapshot has unexpected region geometry")
        frames.append(
            MemoryFrame(
                frame_id=frame_id,
                ewram=bytearray(regions[EWRAM_BASE]),
                iwram=bytearray(regions[IWRAM_BASE]),
                protected_ranges=[],
            )
        )
    return root, frames


def encode_asset(frames: list[MemoryFrame]) -> dict:
    region_frames = []
    for frame in frames:
        regions = []
        for base_address, memory in ((EWRAM_BASE, frame.ewram), (IWRAM_BASE, frame.iwram)):
            raw = bytes(memory)
            regions.append(
                {
                    "baseAddress": base_address,
                    "size": len(raw),
                    "sha256": sha256_bytes(raw),
                    "base64Bytes": base64.b64encode(raw).decode("ascii"),
                }
            )
        region_frames.append({"id": frame.frame_id, "regions": regions})

    normal_frames = [
        {"id": frame.frame_id, "sourceFrameId": frame.frame_id}
        for frame in frames
    ]
    fault_region = {"baseAddress": EWRAM_BASE, "size": EWRAM_SIZE}

    def scenario(scenario_id: str, scenario_frames: list[dict], basename: str = ROM_BASENAME, crc32: str = ROM_CRC32) -> dict:
        return {
            "id": scenario_id,
            "systemId": "game_boy_advance",
            "gameBasename": basename,
            "crc32": crc32,
            "savefileDirectory": "/qa/saves",
            "savestateDirectory": "/qa/states",
            "systemDirectory": "/qa/system",
            "frames": scenario_frames,
        }

    return {
        "schema": 1,
        "provenance": {
            "sourceMemoryDumpSha256": SOURCE_SHA256,
            "romSha256": ROM_SHA256,
            "romCrc32": ROM_CRC32,
            "sanitized": True,
        },
        "regionFrames": region_frames,
        "scenarios": [
            scenario("modern-normal", normal_frames),
            scenario(
                "modern-unreadable",
                [{"id": "unreadable", "sourceFrameId": frames[0].frame_id, "readFaults": [{**fault_region, "kind": "UNREADABLE"}]}],
            ),
            scenario(
                "modern-partial",
                [{"id": "partial", "sourceFrameId": frames[0].frame_id, "readFaults": [{**fault_region, "kind": "PARTIAL"}]}],
            ),
            scenario(
                "modern-malformed",
                [{"id": "malformed", "sourceFrameId": frames[0].frame_id, "readFaults": [{**fault_region, "kind": "MALFORMED"}]}],
            ),
            scenario(
                "stale-identity",
                [{"id": "stale", "sourceFrameId": frames[0].frame_id}],
                basename="DualDex QA stale identity",
                crc32="00000000",
            ),
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="reviewed raw-memory JSON capture")
    parser.add_argument("output", type=Path, help="debug asset path to create")
    args = parser.parse_args()

    _, frames = decode_source(args.source)
    signatures: dict[bytes, bytes] = {}
    for frame in frames:
        sanitize_frame(frame, signatures)
    for frame in frames:
        replace_unprotected_signatures(frame, signatures)
    validate_sanitization(frames, signatures)

    asset = encode_asset(frames)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(asset, separators=(",", ":")), encoding="utf-8")
    print(f"Wrote {len(frames)} sanitized raw-memory frames to {args.output}")


if __name__ == "__main__":
    main()
