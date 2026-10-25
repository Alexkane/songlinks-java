#!/usr/bin/env python3
"""Generate src/main/java/io/songlinks/model/*.java from the API's OpenAPI document.

    curl -sf https://songlinks.io/v1/openapi.json -o src/test/resources/openapi.json
    python3 scripts/gen_models.py

One immutable record per response schema, fields in the API's order, read with Jackson's snake_case naming and unknown
fields ignored, so a field the API adds later never breaks a client built today. List fields the API may omit are
empty lists rather than null. Hand-rolled rather than openapi-generator: that tool's OpenAPI 3.1 support turns the
API's nullable `anyOf` fields into wrapper types and adds runtime dependencies an SDK should not carry.
ModelConformanceTest checks the generated records against the same document on every build.
"""
from __future__ import annotations

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = os.path.join(ROOT, "src", "test", "resources", "openapi.json")
OUT = os.path.join(ROOT, "src", "main", "java", "io", "songlinks", "model")

# The schemas a client reads, and their Java names. Two are renamed: `Record` would shadow java.lang.Record, and
# `Error` reads as java.lang.Error. Request-side and validation schemas are left out.
NAMES = {
    "LookupResponse": "LookupResponse", "Query": "Query", "Cluster": "Cluster", "Record": "LookupRecord",
    "Evidence": "Evidence", "ReviewItem": "ReviewItem", "Link": "Link", "Rejection": "Rejection",
    "Provenance": "Provenance", "SongMatch": "SongMatch", "SongRecord": "SongRecord", "SongBeyond": "SongBeyond",
    "RateLimit": "RateLimit", "PersonResponse": "PersonResponse", "PersonRecord": "PersonRecord",
    "PersonWork": "PersonWork", "WorksPage": "WorksPage", "PersonEvent": "PersonEvent",
    "PersonProvenance": "PersonProvenance", "BuildRef": "BuildRef",
    "PersonWithdrawnResponse": "PersonWithdrawnResponse", "WritersResponse": "WritersResponse",
    "WriterAnswer": "WriterAnswer", "WritersSummary": "WritersSummary", "VersionResponse": "VersionResponse",
    "Error": "ApiError", "RateLimited": "RateLimited",
}
# Integers that are identifiers, read as long so no future id outgrows int.
LONG_FIELDS = {"party_record_id", "build_id", "from_record"}
# Lists of record IDs, read as List<Long> to match partyRecordId(): with List<Integer>,
# work.records().contains(record.partyRecordId()) boxes a Long and is silently false for a record that is there.
LONG_LISTS = {("Evidence", "records"), ("ReviewItem", "records"), ("PersonWork", "records")}


def camel(snake: str) -> str:
    head, *rest = snake.split("_")
    return head + "".join(w[:1].upper() + w[1:] for w in rest)


def java_type(prop: dict, field: str, required: bool) -> str:
    if "$ref" in prop:
        return NAMES[prop["$ref"].rsplit("/", 1)[-1]]
    if "anyOf" in prop:
        inner = [p for p in prop["anyOf"] if p.get("type") != "null"]
        return java_type(inner[0], field, False) if inner else "Object"
    kind = prop.get("type")
    if kind == "array":
        item = java_type(prop.get("items", {}), field, False)
        return f"List<{item}>"
    if kind == "object":
        return "Map<String, Object>"
    boxed = {"integer": "Long" if field in LONG_FIELDS else "Integer", "number": "Double", "boolean": "Boolean",
             "string": "String"}
    prim = {"integer": "long" if field in LONG_FIELDS else "int", "number": "double", "boolean": "boolean",
            "string": "String"}
    return (prim if required else boxed).get(kind, "Object")


SITE = "https://songlinks.io"


# Where the API's own wording names API parameters or paths, the Java reader needs the Java names instead. Keyed by
# (schema, field); the text is Javadoc already. Everything else comes from the API's descriptions.
OVERRIDES = {
    ("BuildRef", "environment"): "Where the build ran.",
    ("BuildRef", "started_at"): "When the build started, in ISO 8601.",
    ("BuildRef", "finished_at"): "When the build finished, in ISO 8601, or null if it has not finished.",
    ("PersonResponse", "works_list"): "One page of works. For the next page, pass {@code worksList().nextOffset()} to "
        "{@code Songlinks.person(id, offset, limit)}, or read them all with {@code Songlinks.personWorks(id)}.",
    ("WorksPage", "next_offset"): "The offset to ask for next, as the {@code worksOffset} argument of "
        "{@code Songlinks.person(id, offset, limit)}; null on the last page.",
    ("SongRecord", "party_record_id"): "The record, as it appears in a candidate's {@code records()}.",
    ("SongRecord", "ref"): "The candidate holding the record, by its {@code ref()}.",
    ("WriterAnswer", "person_url"): "The page to open, relative to https://songlinks.io: the person's page, or for "
        "{@code several} the page listing everyone who holds the number.",
    ("LookupResponse", "links"): "Records registered under another name next to this lookup's. Filled only when the "
        "lookup's routes include {@code catalogue}, the default.",
    ("Provenance", "threshold"): "The threshold this answer used; see {@code LookupOptions.threshold()}.",
    ("VersionResponse", "threshold"): "The default threshold for a lookup; see {@code LookupOptions.threshold()}.",
}


def prose(text: str, fields: set[str]) -> str:
    """One API description as Javadoc English: a capitalised sentence ending in a full stop, `field_name` and
    `a.b_c` references turned into the Java accessors a reader calls ({@code storedPersons()},
    {@code provenance().maxCandidates()}), other code spans kept as {@code ...}, and markdown links as HTML links
    (site-relative ones made absolute, since Javadoc is read away from the site). HTML is escaped outside code spans
    only: inside {@code ...} Javadoc prints text as it is, so an entity there would show as an entity."""
    text = re.sub(r"\s+", " ", text or "").strip()
    pieces = re.split(r"(`[^`]+`)", text)
    out = []
    for piece in pieces:
        if piece.startswith("`") and piece.endswith("`") and len(piece) > 1:
            token = piece[1:-1]
            parts = token.split(".")
            if all(p in fields for p in parts) and ("_" in token or "." in token):
                out.append("{@code " + "().".join(camel(p) for p in parts) + "()}")
            else:
                out.append("{@code " + token + "}")
        else:
            piece = piece.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            piece = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)",
                           lambda m: f'<a href="{SITE + m.group(2) if m.group(2).startswith("/") else m.group(2)}">'
                                     f"{m.group(1)}</a>", piece)
            out.append(piece)
    text = "".join(out).replace("*/", "*&#47;")
    if text and text[0].islower():
        text = text[0].upper() + text[1:]
    if text and not text.endswith((".", "!", "?")):
        text += "."
    return text


def wrap(text: str, prefix: str, width: int = 116) -> list[str]:
    """Javadoc lines of at most `width`. A {@code ...} span is never split across lines, and continuation lines of an
    @param sit four spaces in, as most Java style guides lay them out."""
    units = re.findall(r"\{@code [^}]*\}\S*|\S+", text)
    cont = " *     " if "@param" in prefix else " * "
    lines, line = [], prefix.rstrip()
    for unit in units:
        if len(line) + 1 + len(unit) > width and line not in (prefix.rstrip(), cont[:-1]):
            lines.append(line)
            line = cont[:-1]   # the space that joins it to the next unit is added below
        line += " " + unit
    lines.append(line)
    return lines


def render(schema_name: str, schema: dict) -> str:
    name = NAMES[schema_name]
    required = set(schema.get("required", []))
    fields = []
    for json_name, prop in schema.get("properties", {}).items():
        jt = java_type(prop, json_name, json_name in required)
        if (schema_name, json_name) in LONG_LISTS:
            jt = "List<Long>"
        fields.append((json_name, camel(json_name), jt, prop.get("description")))
    lists = [f for f in fields if f[2].startswith("List<")]
    imports = ["com.fasterxml.jackson.annotation.JsonIgnoreProperties", "com.fasterxml.jackson.annotation.JsonProperty"]
    if lists:
        imports.append("java.util.List")
    if any(f[2].startswith("Map<") for f in fields):
        imports.append("java.util.Map")
    out = ["// Generated by scripts/gen_models.py from the Songlinks OpenAPI document. Do not edit by hand.",
           "package io.songlinks.model;", ""]
    out += [f"import {i};" for i in sorted(imports)] + [""]
    all_fields = {f for sc in ALL_SCHEMAS.values() for f in sc.get("properties", {})}
    out.append("/**")
    out += wrap(prose(schema.get("description") or "", all_fields), " * ")
    out.append(" *")
    for json_name, java_name, _, desc in fields:
        text = OVERRIDES.get((schema_name, json_name)) or prose(desc or "", all_fields)
        out += wrap(text, f" * @param {java_name} ")
    out.append(" */")
    out.append("@JsonIgnoreProperties(ignoreUnknown = true)")
    params = [f'        @JsonProperty("{json_name}") {jt} {java_name}' for json_name, java_name, jt, _ in fields]
    out.append(f"public record {name}(")
    out.append(",\n".join(params) + ") {")
    if lists:
        out.append("")
        out.append("    /** Lists the API leaves out arrive as empty lists, never null, and none can be modified. */")
        out.append(f"    public {name} {{")
        for _, java_name, _, _ in lists:
            out.append(f"        {java_name} = {java_name} == null ? List.of() : List.copyOf({java_name});")
        out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


ALL_SCHEMAS: dict = {}


def main() -> None:
    schemas = json.load(open(SPEC, encoding="utf-8"))["components"]["schemas"]
    ALL_SCHEMAS.update(schemas)
    os.makedirs(OUT, exist_ok=True)
    for schema_name in NAMES:
        with open(os.path.join(OUT, NAMES[schema_name] + ".java"), "w", encoding="utf-8") as f:
            f.write(render(schema_name, schemas[schema_name]))
    print(f"{len(NAMES)} records -> {OUT}")


if __name__ == "__main__":
    main()
