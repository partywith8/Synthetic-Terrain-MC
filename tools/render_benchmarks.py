"""Render compact, transparent README charts from measured benchmark aggregates."""

import json
import math
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / "build" / "matplotlib"))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import MultipleLocator

THEMES = {
    "": {"text": "#24292f", "muted": "#57606a", "grid": "#d0d7de"},
    "-dark": {"text": "#e6edf3", "muted": "#aebbc8", "grid": "#394654"},
}
COLORS = {"stress": "#29a58c", "smp": "#598fdb", "flat_overworld": "#c69231"}


def save(fig, path):
    fig.savefig(
        path,
        dpi=180,
        transparent=True,
        metadata={"Title": "Synthetic Terrain MC benchmark", "Software": "Matplotlib"},
    )
    plt.close(fig)


def bars(directory, groups, theme, suffix, timing=False):
    fig, ax = plt.subplots(figsize=(5, 2.85))
    fig.subplots_adjust(left=0.29, right=0.85, top=0.84, bottom=0.17)
    labels = [
        "STM stress · 10",
        "STM stress · 25",
        "SMP · 10",
        "SMP · 25→14",
        "Flat OW · 10",
        "Flat OW · 25",
    ]
    values = [
        g["sampled_client_loop_ms"]["p99"] if timing else g["average_fps"]
        for g in groups
    ]
    ax.barh(
        range(len(groups)),
        values,
        height=0.57,
        color=[COLORS[g["scenario"]] for g in groups],
    )
    ax.set_yticks(range(len(groups)), labels)
    ax.invert_yaxis()
    ax.set_xlim(0, 9 if timing else 2050)
    ax.xaxis.set_major_locator(MultipleLocator(2 if timing else 500))
    ax.set_axisbelow(True)
    ax.grid(axis="x", color=theme["grid"], linewidth=0.5)
    ax.tick_params(colors=theme["muted"], length=0, pad=5, labelsize=9)
    for spine in ax.spines.values():
        spine.set_visible(False)
    for y, value in enumerate(values):
        ax.text(
            value + (0.12 if timing else 25),
            y,
            f"{value:.2f}" if timing else f"{value:,.1f}",
            va="center",
            fontsize=9,
            color=theme["text"],
        )
    fig.text(
        0.04,
        0.94,
        "p99 client loop · ms ↓" if timing else "Average FPS ↑",
        fontsize=11,
        weight="bold",
        color=theme["text"],
        va="top",
    )
    ax.set_xlabel(
        "CPU samples · pooled" if timing else "Frames / elapsed time · pooled",
        fontsize=8,
        color=theme["muted"],
    )
    save(
        fig,
        directory
        / (("stress-timing" if timing else "stress-performance") + suffix + ".png"),
    )


def pie(directory, capture, theme, suffix):
    stages = capture["cpu_profiler_stages"]
    swap = stages["swapBuffers"]["percent_of_profiled_root"]
    render = stages["render"]["percent_of_profiled_root"]
    terrain = stages["epd_terrain"]["percent_of_profiled_root"]
    # The terrain pass is nested in render. Subtract it to keep slices disjoint.
    shares = [swap, terrain, render - terrain, 100 - swap - render]
    assert all(math.isfinite(v) and v >= 0 for v in shares)
    assert math.isclose(sum(shares), 100, abs_tol=1e-8)
    fig = plt.figure(figsize=(5, 2.15))
    ax = fig.add_axes((0.015, 0.04, 0.37, 0.77))
    wedges, _ = ax.pie(
        shares,
        colors=[COLORS["smp"], COLORS["stress"], COLORS["flat_overworld"], "#83909e"],
        startangle=90,
        counterclock=False,
        wedgeprops={"linewidth": 0},
    )
    fig.text(
        0.04,
        0.95,
        "25-chunk stress · CPU profile",
        fontsize=11,
        weight="bold",
        color=theme["text"],
        va="top",
    )
    names = [
        "Swap / presentation",
        "STM render pass",
        "Other rendering",
        "Other profiled work",
    ]
    fig.legend(
        wedges,
        [f"{name}  {value:.2f}%" for name, value in zip(names, shares)],
        loc="center left",
        bbox_to_anchor=(0.39, 0.44),
        frameon=False,
        fontsize=9,
        labelcolor=theme["text"],
        labelspacing=1.0,
        handlelength=1,
    )
    save(fig, directory / ("stress-cpu-profile" + suffix + ".png"))


def main():
    directory = ROOT / "docs/benchmarks"
    report = json.loads(
        (directory / "comparison-2026-10-14.json").read_text(encoding="utf-8")
    )
    lookup = {(g["scenario"], g["effective_view_chunks"]): g for g in report["groups"]}
    groups = [
        lookup[key]
        for key in [
            ("stress", 10),
            ("stress", 25),
            ("smp", 10),
            ("smp", 14),
            ("flat_overworld", 10),
            ("flat_overworld", 25),
        ]
    ]
    capture = next(c for c in report["captures"] if c["capture"] == "STM-25-B")
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9})
    for suffix, theme in THEMES.items():
        bars(directory, groups, theme, suffix)
        bars(directory, groups, theme, suffix, timing=True)
        pie(directory, capture, theme, suffix)


if __name__ == "__main__":
    main()
