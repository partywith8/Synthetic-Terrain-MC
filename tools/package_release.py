"""Package the current STM mod and sources. Runtime data is never included."""

import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = "https://github.com/partywith8/SyntheticTerrain-MC"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    properties = dict(
        line.split("=", 1)
        for line in (ROOT / "gradle.properties").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    version = properties["mod_version"]
    stem = properties["archives_base_name"] + "-" + version
    jar = ROOT / "build/libs" / (stem + ".jar")
    sources = jar.with_name(stem + "-sources.jar")
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["id"] == "epd" and metadata["version"] == version
        assert metadata["name"] == "Synthetic Terrain MC"
        assert metadata["contact"]["sources"] == REPO
        for name in names:
            assert not name.startswith(("net/minecraft/", "assets/minecraft/")), name
            if name.endswith(".class"):
                assert name.startswith("io/github/partywith8/epd/"), name
                assert "MotionTest" not in name, name
        for config in ("epd.mixins.json", "epd.client.mixins.json"):
            definition = json.loads(archive.read(config))
            for mixin in definition.get("mixins", []) + definition.get("client", []):
                assert (
                    definition["package"].replace(".", "/") + "/" + mixin + ".class"
                    in names
                ), mixin
        assert "data/epd/dimension/lab.json" in names
    with zipfile.ZipFile(sources) as archive:
        assert archive.testzip() is None
        assert any(name.endswith("DemoCommands.java") for name in archive.namelist())
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    # Keep large showcase media in the repository, not in every mod download.
    readme = re.sub(
        r"## Showcase\n.*?(?=^## )",
        "## Showcase\n\n" + REPO + "#showcase\n\n",
        readme,
        flags=re.S | re.M,
    ).replace(
        "(dist/SyntheticTerrainMC.zip)",
        "(" + REPO + "/blob/main/dist/SyntheticTerrainMC.zip)",
    )
    payload = {
        jar.name: jar.read_bytes(),
        sources.name: sources.read_bytes(),
        "README.md": readme.encode("utf-8"),
        "docs/TECHNICAL.md": (ROOT / "docs/TECHNICAL.md").read_bytes(),
        "LICENSE": (ROOT / "LICENSE").read_bytes(),
    }
    for benchmark in sorted((ROOT / "docs/benchmarks").iterdir()):
        if benchmark.suffix in {".json", ".png"}:
            payload[benchmark.relative_to(ROOT).as_posix()] = benchmark.read_bytes()
    payload["INSTALL.txt"] = (
        "Synthetic Terrain MC " + version + "\n\n"
        "MC 26.2 / Java 25 / Fabric Loader 0.19+ / Fabric API for 26.2.\n"
        "Install " + jar.name + " in client and server mods folders.\n"
        "Do not install the -sources.jar. Fabric API is a separate dependency.\n"
        "Use /stm enter, /trigger wave, /stm leave. Debug: /stm debug on.\n"
    ).encode("utf-8")
    payload["SHA256SUMS"] = "".join(
        sha(data) + "  " + name + "\n" for name, data in payload.items()
    ).encode("utf-8")
    dest = ROOT / "dist"
    dest.mkdir(exist_ok=True)
    output = dest / "SyntheticTerrainMC.zip"
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(payload.items()):
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    (dest / "SHA256SUMS").write_text(
        sha(output.read_bytes()) + "  SyntheticTerrainMC.zip\n", encoding="utf-8"
    )
    report = {
        "version": version,
        "jar_sha256": sha(jar.read_bytes()),
        "jar_bytes": jar.stat().st_size,
        "zip_sha256": sha(output.read_bytes()),
        "zip_bytes": output.stat().st_size,
    }
    (ROOT / "build/release.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
