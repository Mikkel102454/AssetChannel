"""Compile actual Minecraft shaders and validate the compositor and measure its GPU cost using installed libraries.

Usage: python examples/client-probe/run-probes.py [--pack generated/pack.zip]
Requires Java 25, Minecraft 26.3 and Minecraft's LWJGL 3.3.3 GLFW libraries locally.
Writes reproducible logs to build/reports/effect-optimization; never changes the client.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--pack", type=Path)
args = parser.parse_args()
report = ROOT / "build/reports/effect-optimization"
report.mkdir(parents=True, exist_ok=True)
client = Path(os.environ["APPDATA"]) / ".minecraft"
version = client / "versions/26.3"
jar = version / "26.3.jar"
java = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot")) / "bin"
metadata = json.loads((version / "26.3.json").read_text(encoding="utf-8"))
native_libs = [jar]
for library in metadata["libraries"]:
    artifact = library.get("downloads", {}).get("artifact", {})
    path = artifact.get("path", "")
    if path and not any(part in path for part in ("natives-linux", "natives-macos", "arm64", "windows-x86")):
        local = client / "libraries" / path
        # The version manifest also lists OS-specific libraries absent on Windows.
        if local.is_file():
            native_libs.append(local)
gpu_libs = []
for module in ("lwjgl", "lwjgl-glfw", "lwjgl-opengl"):
    gpu_libs.extend(p for p in (client / "libraries/org/lwjgl" / module / "3.3.3").glob("*.jar")
                    if not any(x in p.name for x in ("linux", "macos", "arm64", "windows-x86")))


def run(name, command):
    result = subprocess.run([str(x) for x in command], cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (report / (name + ".log")).write_text(result.stdout, encoding="utf-8")
    print(name + ":", "PASS" if result.returncode == 0 else "FAIL", flush=True)
    print("\n".join(result.stdout.splitlines()[-10:]), flush=True)
    if result.returncode:
        raise SystemExit(f"{name} failed; full output: {report / (name + '.log')}")


native_cp = os.pathsep.join(str(p) for p in native_libs)
gpu_cp = os.pathsep.join(str(p) for p in gpu_libs)
source = ROOT / "examples/client-probe"
run("compile-native", [java / "javac.exe", "-cp", native_cp, "-d", report, source / "NativeShaderProbe.java"])
assets = args.pack.resolve() if args.pack else ROOT / "examples/cinematic-pack/assets"
run("NativeShaderProbe", [java / "java.exe", "-cp", str(report) + os.pathsep + native_cp,
                          "NativeShaderProbe", assets, jar])
shaders = ROOT / "examples/cinematic-pack/assets/cinematic/shaders/post"
if args.pack:
    shaders = report / "served-shaders"
    shaders.mkdir(exist_ok=True)
    with zipfile.ZipFile(assets) as pack:
        for name in ("control_pixel.vsh", "control_value.fsh", "composite.vsh", "composite.fsh"):
            # Read required entries explicitly so old extracted files cannot mask a missing asset.
            (shaders / name).write_bytes(pack.read("assets/cinematic/shaders/post/" + name))
run("compile-gpu", [java / "javac.exe", "-cp", gpu_cp, "-d", report,
                    source / "GpuProbeSupport.java", source / "ClientEffectsProbe.java"])
for name in ("ClientEffectsProbe",):
    run(name, [java / "java.exe", "-cp", str(report) + os.pathsep + gpu_cp, name, shaders, jar])
