#!/usr/bin/env python3
"""
Genera el mapa de la app leyendo el código: módulos, navegación, pantallas, casos de uso,
repositorios, datos en Firestore y pruebas.

    python3 scripts/app_map.py          # escribe docs/APP_MAP.md y docs/app-map.json
    python3 scripts/app_map.py --check  # falla (exit 1) si el mapa no coincide con el código (CI)

No usa dependencias externas. Si cambias la forma del código (p. ej. el NavHost), ajusta las
expresiones de este archivo; el CI te avisará si el mapa deja de coincidir.
"""
from __future__ import annotations

import json
import re
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
NAV_HOST = ROOT / "app/src/main/kotlin/com/barberia/app/navigation/BarberiaNavHost.kt"
RULES = ROOT / "firebase/firestore.rules"

LAYER = {
    "barberia.jvm.library": "Kotlin puro",
    "barberia.android.compose": "Android + Compose",
    "barberia.android.library": "Android",
    "android.application": "App (Android)",
}


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def kotlin_files(base: Path, kind: str = "main") -> list[Path]:
    return sorted(p for p in base.glob(f"src/{kind}/**/*.kt"))


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def block_after(text: str, start: int) -> str:
    """Devuelve el bloque { ... } que empieza en o después de `start`."""
    i = text.index("{", start)
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i + 1 : j]
    return text[i + 1 :]


# ── Módulos ──────────────────────────────────────────────────────────────────

def modules() -> list[dict]:
    settings = read(ROOT / "settings.gradle.kts")
    result = []
    for path in re.findall(r'include\("(:[^"]+)"\)', settings):
        folder = ROOT / path.strip(":").replace(":", "/")
        build = read(folder / "build.gradle.kts")
        layer = "?"
        if "barberia.jvm.library" in build:
            layer = LAYER["barberia.jvm.library"]
        elif "barberia.android.compose" in build:
            layer = LAYER["barberia.android.compose"]
        elif "barberia.android.library" in build:
            layer = LAYER["barberia.android.library"]
        elif "android.application" in build:
            layer = LAYER["android.application"]
        deps = []
        for config, dep in re.findall(r'(\w+)\((?:testFixtures\()?project\("(:[^"]+)"\)', build):
            if config.startswith("test"):
                continue
            deps.append(dep)
        tests = sum(read(f).count("@Test") for f in kotlin_files(folder, "test"))
        result.append({
            "path": path,
            "folder": rel(folder),
            "layer": layer,
            "dependsOn": sorted(set(deps)),
            "kotlinFiles": len(kotlin_files(folder)),
            "unitTests": tests,
        })
    return result


# ── Navegación ───────────────────────────────────────────────────────────────

def navigation() -> dict:
    text = read(NAV_HOST)
    start = re.search(r"startDestination\s*=\s*(\w+)", text).group(1)
    host = block_after(text, text.index("NavHost("))
    destinations, edges = [], []
    for m in re.finditer(r"composable<(\w+)>", host):
        body = block_after(host, m.end())
        screen = re.search(r"\b(\w+(?:Route|Screen))\(", body)
        destinations.append({"route": m.group(1), "screen": screen.group(1) if screen else None})
        for target in re.findall(r"navigate\((\w+)\)", body):
            edges.append({"from": m.group(1), "to": target, "trigger": "botón en pantalla"})
        if "popBackStack" in body:
            edges.append({"from": m.group(1), "to": "(atrás)", "trigger": "volver"})
    session = []
    mapping = block_after(text, text.index("fun NavHostController.navigateToSession"))
    for name, route in re.findall(r"SessionDestination\.(\w+)\s*->\s*(\w+)", mapping):
        session.append({"sessionDestination": name, "route": route})
    return {"start": start, "destinations": destinations, "edges": edges, "sessionRoutes": session}


def walkthroughs(nav: dict) -> list[dict]:
    """Camino más corto desde el inicio hasta cada pantalla (para recorridos de prueba)."""
    graph: dict[str, list[tuple[str, str]]] = {}
    for s in nav["sessionRoutes"]:
        for d in nav["destinations"]:
            graph.setdefault(d["route"], []).append((s["route"], f"la sesión resuelve **{s['sessionDestination']}**"))
    for e in nav["edges"]:
        if e["to"] != "(atrás)":
            graph.setdefault(e["from"], []).append((e["to"], "tocar el botón que navega"))
    paths = {nav["start"]: []}
    queue = deque([nav["start"]])
    while queue:
        node = queue.popleft()
        for target, how in graph.get(node, []):
            if target not in paths:
                paths[target] = paths[node] + [(target, how)]
                queue.append(target)
    result = []
    for d in nav["destinations"]:
        steps = paths.get(d["route"])
        result.append({"route": d["route"], "steps": [{"to": t, "how": h} for t, h in steps] if steps is not None else None})
    return result


# ── Pantallas, ViewModels, casos de uso ──────────────────────────────────────

def all_kotlin() -> list[Path]:
    return [p for p in ROOT.glob("**/src/main/**/*.kt") if "/build/" not in p.as_posix() and "build-logic" not in p.as_posix()]


def find_class_file(name: str, files: list[Path]) -> Path | None:
    pattern = re.compile(rf"\bclass {name}\b")
    return next((f for f in files if pattern.search(read(f))), None)


def constructor_types(text: str, class_name: str) -> list[str]:
    m = re.search(rf"class {class_name}\s*\(", text)
    if not m:
        return []
    depth, i = 1, m.end()
    while depth and i < len(text):
        depth += {"(": 1, ")": -1}.get(text[i], 0)
        i += 1
    params = text[m.end() : i - 1]
    return re.findall(r":\s*(\w+)", params)


def intents_of(vm_file: Path, vm_name: str) -> list[str]:
    folder_texts = "\n".join(read(f) for f in vm_file.parent.glob("*.kt"))
    base = vm_name.removesuffix("ViewModel")
    m = re.search(rf"sealed interface ({base}Intent|\w*Intent)\s*:\s*UiIntent", folder_texts)
    if not m:
        return []
    body = block_after(folder_texts, m.end())
    return sorted(set(re.findall(r"data (?:class|object) (\w+)", body)))


def screens(nav: dict) -> list[dict]:
    files = all_kotlin()
    result = []
    for d in nav["destinations"]:
        screen = d["screen"]
        if not screen:
            continue
        src = next((f for f in files if re.search(rf"fun {screen}\(", read(f))), None)
        vm = None
        if src:
            m = re.search(rf"fun {screen}\((.*?)\)\s*\{{", read(src), re.S)
            if m:
                vm_match = re.search(r"viewModel:\s*(\w+ViewModel)", m.group(1))
                vm = vm_match.group(1) if vm_match else None
        if vm is None and d["route"] in ("SplashDest", "OfflineDest"):
            vm = "SessionViewModel"
        vm_file = find_class_file(vm, files) if vm else None
        use_cases = [t for t in constructor_types(read(vm_file), vm) if t.endswith("UseCase")] if vm_file else []
        result.append({
            "route": d["route"],
            "screen": screen,
            "file": rel(src) if src else None,
            "viewModel": vm,
            "viewModelFile": rel(vm_file) if vm_file else None,
            "intents": intents_of(vm_file, vm) if vm_file else [],
            "useCases": sorted(set(use_cases)),
        })
    return result


def use_cases() -> list[dict]:
    result = []
    for f in all_kotlin():
        text = read(f)
        for name in re.findall(r"\bclass (\w+UseCase)\s*\(", text):
            deps = [t for t in constructor_types(text, name) if t.endswith(("Repository", "Editor"))]
            result.append({"name": name, "file": rel(f), "uses": sorted(set(deps))})
    return sorted(result, key=lambda u: u["name"])


def repositories() -> list[dict]:
    result = []
    for f in all_kotlin():
        text = read(f)
        for m in re.finditer(r"class (\w+)\s*\((?:[^()]|\([^()]*\))*\)\s*:\s*([\w ,]+?)\s*\{", text):
            ifaces = [i.strip() for i in m.group(2).split(",") if i.strip().endswith("Repository")]
            if ifaces:
                result.append({"implementation": m.group(1), "implements": ifaces, "file": rel(f)})
    return sorted(result, key=lambda r: r["implementation"])


def firestore_paths() -> list[dict]:
    paths, stack = [], []
    current = None  # ruta a la que pertenecen los `allow` que siguen
    for line in read(RULES).splitlines():
        m = re.search(r"match\s+(\S+)\s*\{", line)
        if m:
            stack.append(m.group(1))
            full = "".join(stack).replace("/databases/{database}/documents", "")
            current = None
            if full and "{document=**}" not in full:
                current = {"path": full, "ops": []}
                paths.append(current)
        elif re.search(r"allow\s+([\w, ]+):", line) and current is not None:
            ops = re.search(r"allow\s+([\w, ]+):", line).group(1)
            cond = "if false" not in line
            current["ops"].extend(f"{op.strip()}{'' if cond else ' ✗'}" for op in ops.split(","))
        if line.strip() == "}" and stack:
            stack.pop()
    return paths


def rules_tests() -> int:
    return sum(len(re.findall(r'\btest\("', read(f))) for f in (ROOT / "firebase/test").glob("*.test.js"))


# ── Salida ───────────────────────────────────────────────────────────────────

def mid(name: str) -> str:
    return re.sub(r"\W", "_", name.strip(":")) or "root"


def render(data: dict) -> str:
    mods, nav, scr = data["modules"], data["navigation"], data["screens"]
    unit = sum(m["unitTests"] for m in mods)
    out = [
        "# Mapa de la app",
        "",
        "> **Generado por `scripts/app_map.py`. No editar a mano.** El CI falla si no coincide con el código:",
        "> después de cambiar módulos, navegación, ViewModels o reglas, ejecuta `python3 scripts/app_map.py`.",
        "> Versión para IA/herramientas: [`app-map.json`](app-map.json).",
        "",
        "## Resumen",
        "",
        "| Módulos | Pantallas | Casos de uso | Repositorios | Rutas en Firestore | Tests Kotlin | Tests de reglas |",
        "|---|---|---|---|---|---|---|",
        f"| {len(mods)} | {len(nav['destinations'])} | {len(data['useCases'])} | {len(data['repositories'])} "
        f"| {len(data['firestore'])} | {unit} | {data['rulesTests']} |",
        "",
        "## 1. Módulos y dependencias",
        "",
        "La flecha va de quien usa a quien es usado. `domain` es Kotlin puro: no conoce Android ni Firebase.",
        "",
        "```mermaid",
        "graph LR",
    ]
    for m in mods:
        out.append(f'  {mid(m["path"])}["{m["path"]}<br/><small>{m["layer"]}</small>"]')
    for m in mods:
        for d in m["dependsOn"]:
            out.append(f"  {mid(m['path'])} --> {mid(d)}")
    out += ["```", "", "| Módulo | Capa | Archivos Kotlin | Tests | Depende de |", "|---|---|---|---|---|"]
    for m in mods:
        deps = ", ".join(f"`{d}`" for d in m["dependsOn"]) or "—"
        out.append(f"| [`{m['path']}`](../{m['folder']}) | {m['layer']} | {m['kotlinFiles']} | {m['unitTests']} | {deps} |")

    out += [
        "",
        "## 2. Navegación",
        "",
        "Las flechas punteadas las decide **la sesión** (`SessionViewModel`): login, registro, elegir modo y",
        "cerrar sesión solo cambian la sesión y la app navega sola. Las sólidas son botones dentro de una pantalla.",
        "",
        "```mermaid",
        "graph TD",
        f"  START(((Abrir app))) --> {nav['start']}",
        '  SESSION{{"Sesión<br/>(SessionViewModel)"}}',
    ]
    for d in nav["destinations"]:
        out.append(f'  {d["route"]}["{d["route"]}<br/><small>{d["screen"] or ""}</small>"]')
    out.append(f"  {nav['start']} -.-> SESSION")
    for s in nav["sessionRoutes"]:
        out.append(f"  SESSION -. {s['sessionDestination']} .-> {s['route']}")
    for e in nav["edges"]:
        if e["to"] != "(atrás)":
            out.append(f"  {e['from']} --> {e['to']}")
    out += ["```", ""]

    out += ["## 3. Pantallas", "", "| Ruta | Pantalla | ViewModel | Intents (lo que el usuario puede hacer) | Casos de uso |",
            "|---|---|---|---|---|"]
    for s in scr:
        screen = f"[`{s['screen']}`](../{s['file']})" if s["file"] else s["screen"]
        vm = f"[`{s['viewModel']}`](../{s['viewModelFile']})" if s["viewModelFile"] else (s["viewModel"] or "—")
        intents = ", ".join(s["intents"]) or "—"
        ucs = ", ".join(s["useCases"]) or "—"
        out.append(f"| `{s['route']}` | {screen} | {vm} | {intents} | {ucs} |")

    out += ["", "## 4. Recorridos de prueba", "",
            "Camino más corto desde que se abre la app hasta cada pantalla. Sirve para probar a mano o",
            "para escribir una prueba de interfaz.", ""]
    for w in data["walkthroughs"]:
        if w["steps"] is None:
            out.append(f"- **{w['route']}**: ⚠️ no se llega desde el inicio (¿pantalla huérfana?)")
        elif not w["steps"]:
            out.append(f"- **{w['route']}**: es la pantalla inicial.")
        else:
            steps = " → ".join(f"`{s['to']}` ({s['how']})" for s in w["steps"])
            out.append(f"- **{w['route']}**: abrir app → {steps}")

    used_by: dict[str, list[str]] = {}
    for s in scr:
        for uc in s["useCases"]:
            used_by.setdefault(uc, []).append(s["route"])
    orphans = [u["name"] for u in data["useCases"] if u["name"] not in used_by]
    out += ["", "## 5. Casos de uso → repositorios", ""]
    if orphans:
        out += [f"⚠️ **{len(orphans)} casos de uso sin pantalla** (lógica lista que nadie usa todavía): "
                + ", ".join(f"`{o}`" for o in orphans), ""]
    out += ["| Caso de uso | Usa | Lo usan las pantallas | Archivo |", "|---|---|---|---|"]
    for u in data["useCases"]:
        screens_txt = ", ".join(f"`{r}`" for r in sorted(set(used_by.get(u["name"], [])))) or "⚠️ ninguna"
        out.append(f"| `{u['name']}` | {', '.join(u['uses']) or '—'} | {screens_txt} | [{Path(u['file']).name}](../{u['file']}) |")
    out += ["", "| Implementación | Implementa | Archivo |", "|---|---|---|"]
    for r in data["repositories"]:
        out.append(f"| `{r['implementation']}` | {', '.join(r['implements'])} | [{Path(r['file']).name}](../{r['file']}) |")

    out += ["", "## 6. Datos en Firestore (según las reglas)", "", "✗ = prohibido explícitamente.", "",
            "| Ruta | Operaciones |", "|---|---|"]
    for p in data["firestore"]:
        out.append(f"| `{p['path']}` | {', '.join(p['ops'])} |")

    out += [
        "",
        "## 7. ¿Dónde le muevo?",
        "",
        "| Quiero cambiar… | Ve a… |",
        "|---|---|",
        "| Una regla de negocio (validación, cálculo) | `feature/<x>/domain/.../usecase` o `.../editor`, con su test en `src/test` |",
        "| Lo que se ve en una pantalla | El archivo de la columna *Pantalla* (sección 3) |",
        "| Qué pasa cuando el usuario toca algo | El *ViewModel* de esa pantalla → `handleIntent` |",
        "| Cómo se guarda o lee en Firebase | `feature/<x>/data/.../firebase` (gateway) y su repositorio |",
        "| Quién puede leer/escribir un dato | `firebase/firestore.rules` + `firebase/test/*.test.js` |",
        "| A dónde va el usuario al iniciar sesión | `resolveDestination` en `feature/auth/domain/.../SessionState.kt` |",
        "| Una ruta o botón de navegación | `app/.../navigation/BarberiaNavHost.kt` |",
        "| Colores, botones, campos | `core/designsystem` |",
        "| Configuración de Gradle común | `build-logic/convention` |",
        "| Textos | `src/main/res/values/strings.xml` del módulo de la pantalla |",
        "",
    ]
    return "\n".join(out)


def build() -> tuple[str, str]:
    nav = navigation()
    data = {
        "modules": modules(),
        "navigation": nav,
        "walkthroughs": walkthroughs(nav),
        "screens": screens(nav),
        "useCases": use_cases(),
        "repositories": repositories(),
        "firestore": firestore_paths(),
        "rulesTests": rules_tests(),
    }
    return render(data), json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    md, js = build()
    targets = {DOCS / "APP_MAP.md": md, DOCS / "app-map.json": js}
    if "--check" in sys.argv:
        stale = [rel(p) for p, content in targets.items() if not p.exists() or read(p) != content]
        if stale:
            print("El mapa no coincide con el código. Ejecuta: python3 scripts/app_map.py")
            print("Desactualizado:", ", ".join(stale))
            return 1
        print("Mapa al día.")
        return 0
    for path, content in targets.items():
        path.write_text(content, encoding="utf-8")
        print("Escrito", rel(path))
    return 0


if __name__ == "__main__":
    sys.exit(main())
