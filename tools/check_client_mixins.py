"""Load client mixin targets under Fabric without opening a window or a world.

Uses existing local dependencies only. This checks transformation compatibility,
not rendering or in-game behavior. Logs stay in build/.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = [
    "net.minecraft.world.entity.player.Player",
    "net.minecraft.world.entity.Entity",
    "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
    "net.minecraft.world.level.BlockGetter",
    "net.minecraft.world.level.CollisionGetter",
    "net.minecraft.server.level.ServerPlayerGameMode",
    "net.minecraft.client.multiplayer.ClientLevel",
    "net.minecraft.client.multiplayer.MultiPlayerGameMode",
    "net.minecraft.client.renderer.RenderPipelines",
    "net.minecraft.client.renderer.LevelRenderer",
    "net.minecraft.client.renderer.extract.LevelExtractor",
    "net.minecraft.client.renderer.chunk.RenderSectionRegion",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--minecraft", type=Path, required=True)
    parser.add_argument("--loader-libraries", type=Path, required=True)
    parser.add_argument("--mod", type=Path, action="append", required=True)
    parser.add_argument("--label", default="client")
    args = parser.parse_args()
    targets = list(TARGETS)
    sodium = "net/caffeinemc/mods/sodium/client/world/LevelSlice.class"
    for mod in args.mod:
        with zipfile.ZipFile(mod) as archive:
            if sodium in archive.namelist():
                targets.append(sodium[:-6].replace("/", "."))
    if not args.label.replace("-", "").isalnum():
        parser.error("label must be alphanumeric")
    output = ROOT / "build" / "mixin-check" / args.label
    output.mkdir(parents=True, exist_ok=True)
    metadata = json.loads((args.minecraft / "versions/26.2/26.2.json").read_text())
    libraries = [args.minecraft / "versions/26.2/26.2.jar"]
    for lib in metadata["libraries"]:
        path = lib.get("downloads", {}).get("artifact", {}).get("path")
        if path and "natives-" not in path:
            candidate = args.minecraft / "libraries" / path
            if candidate.exists():
                libraries.append(candidate)
    for namespace in ("net/fabricmc", "org/ow2/asm"):
        libraries.extend(sorted((args.loader_libraries / namespace).rglob("*.jar")))
    classpath = os.pathsep.join(str(p.resolve()) for p in libraries)
    source = output / "ClientMixinProbe.java"
    source.write_text(
        """
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
public class ClientMixinProbe implements PreLaunchEntrypoint {
    public void onPreLaunch() {
        try {
            String[] targets = {"""
        + ",".join(json.dumps(t) for t in targets)
        + """};
            for (String name : targets) {
                Class.forName(name, false, getClass().getClassLoader());
                System.out.println("STM_TRANSFORM_PASS " + name);
            }
            System.out.println("STM_CLIENT_MIXINS_PASS");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(2);
        }
    }
}
"""
    )

    def invoke(executable, arguments, log):
        argfile = output / (log + ".args")
        argfile.write_text(
            "\n".join(
                '"' + str(a).replace("\\", "\\\\").replace('"', '\\"') + '"'
                for a in arguments
            )
        )
        try:
            result = subprocess.run(
                [str(args.jdk / "bin" / executable), "@" + str(argfile)],
                cwd=output,
                capture_output=True,
                text=True,
                timeout=90,
            )
            (output / (log + ".log")).write_text(result.stdout + result.stderr)
            return result
        finally:
            argfile.unlink(missing_ok=True)

    compile_result = invoke(
        "javac.exe", ["-proc:none", "-cp", classpath, str(source)], "compile"
    )
    compile_result.check_returncode()
    probe = output / "probe.jar"
    with zipfile.ZipFile(probe, "w") as archive:
        archive.write(output / "ClientMixinProbe.class", "ClientMixinProbe.class")
        archive.writestr(
            "fabric.mod.json",
            json.dumps(
                dict(
                    schemaVersion=1,
                    id="epd_probe",
                    version="1",
                    environment="client",
                    entrypoints={"preLaunch": ["ClientMixinProbe"]},
                )
            ),
        )
    mods = os.pathsep.join(str(p.resolve()) for p in [probe, *args.mod])
    result = invoke(
        "java.exe",
        [
            "-Xmx1G",
            "-Dfabric.addMods=" + mods,
            "-cp",
            classpath,
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--gameDir",
            str(output),
        ],
        "transform",
    )
    print(result.stdout[-1800:] + result.stderr[-3500:])
    print("Log:", output / "transform.log")
    result.check_returncode()
    assert "STM_CLIENT_MIXINS_PASS" in result.stdout


if __name__ == "__main__":
    main()
