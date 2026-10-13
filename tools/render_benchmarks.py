"""Render transparent README charts from measured benchmark aggregates."""

import json
import math
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / "build" / "matplotlib"))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

THEMES = {
    "": {"text": "#24292f", "muted": "#57606a", "grid": "#d0d7de"},
    "-dark": {"text": "#e6edf3", "muted": "#aebbc8", "grid": "#394654"},
}
COLORS = {"stress": "#29a58c", "smp": "#598fdb", "flat_overworld": "#c69231"}
LABELS = [
    "STM stress · 10",
    "STM stress · 25",
    "SMP · 10",
    "SMP · 25→14",
    "Flat OW · 10",
    "Flat OW · 25",
]


def save(fig, path):
    fig.savefig(
        path,
        dpi=180,
        transparent=True,
        metadata={"Title": "Synthetic Terrain MC benchmark", "Software": "Matplotlib"},
    )
    plt.close(fig)


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


def clean_axis(ax, theme, grid_axis="y"):
    ax.set_axisbelow(True)
    ax.grid(axis=grid_axis, color=theme["grid"], linewidth=0.5)
    ax.tick_params(colors=theme["muted"], length=0, labelsize=10)
    for spine in ax.spines.values():
        spine.set_visible(False)


def low_bars(directory, groups, theme, suffix):
    fig, ax = plt.subplots(figsize=(9.5, 5))
    fig.subplots_adjust(left=0.19, right=0.92, top=0.82, bottom=0.12)
    colors = ["#598fdb", "#29a58c", "#c69231"]
    keys = [None, "one_percent", "point_one_percent"]
    names = ["Avg FPS (wall time)", "1% low (CPU-derived)", "0.1% low (CPU-derived)"]
    for series, (key, name, color) in enumerate(zip(keys, names, colors)):
        values = [
            g["average_fps"] if key is None else g["cpu_derived_lows"][key]["fps"]
            for g in groups
        ]
        positions = [i + (series - 1) * 0.24 for i in range(len(groups))]
        ax.barh(positions, values, height=0.21, color=color, label=name)
        for y, value in zip(positions, values):
            ax.text(
                value + 15,
                y,
                f"{value:,.1f}",
                va="center",
                fontsize=9,
                color=theme["text"],
            )
    ax.set_yticks(range(len(groups)), LABELS)
    ax.invert_yaxis()
    ax.set_xlim(0, 2050)
    clean_axis(ax, theme, "x")
    ax.legend(
        loc="lower left",
        bbox_to_anchor=(-0.21, 1.035),
        ncol=3,
        frameon=False,
        fontsize=9,
        labelcolor=theme["text"],
        handlelength=1,
    )
    fig.text(
        0.025,
        0.97,
        "Average FPS + sampled lows ↑",
        color=theme["text"],
        weight="bold",
        fontsize=14,
        va="top",
    )
    ax.set_xlabel(
        "FPS · pooled within each scene / view setting",
        color=theme["muted"],
        fontsize=10,
    )
    save(fig, directory / ("fps-lows" + suffix + ".png"))


def timeline(directory, groups, captures, theme, suffix):
    fig, axes = plt.subplots(3, 2, figsize=(9.5, 7.5))
    fig.subplots_adjust(
        left=0.085, right=0.98, top=0.87, bottom=0.08, hspace=0.65, wspace=0.24
    )
    fig.text(
        0.025,
        0.97,
        "FPS over time · sampled client loop",
        color=theme["text"],
        weight="bold",
        fontsize=14,
        va="top",
    )
    fig.text(
        0.025,
        0.92,
        "Approx. 1 s bins · A solid / B hatched · paired panels share a vertical scale",
        color=theme["muted"],
        fontsize=10,
    )
    limits = {
        scenario: math.ceil(
            max(
                b["fps"]
                for c in captures
                if c["scenario"] == scenario
                for b in c["cpu_loop_timeline"]
            )
            * 1.1
            / 100
        )
        * 100
        for scenario in COLORS
    }
    for ax, group, label in zip(axes.flat, groups, LABELS):
        runs = [
            c
            for c in captures
            if c["scenario"] == group["scenario"]
            and c["settings"]["effective_view_chunks"] == group["effective_view_chunks"]
        ]
        width = 0.36 if len(runs) == 2 else 0.72
        for i, run in enumerate(runs):
            bins = run["cpu_loop_timeline"]
            x = [b["second"] + 0.5 + (i - (len(runs) - 1) / 2) * width for b in bins]
            values = [b["fps"] for b in bins]
            assert (
                max(values) < limits[group["scenario"]]
            ), "Increase the plot limit for new data"
            ax.bar(
                x,
                values,
                width=width,
                color=COLORS[group["scenario"]],
                alpha=1 if i == 0 else 0.55,
                hatch=None if i == 0 else "///",
                edgecolor=theme["text"],
                linewidth=0.2,
            )
        ax.set_title(
            label + (" · A only" if len(runs) == 1 else ""),
            loc="left",
            fontsize=11,
            color=theme["text"],
            pad=8,
        )
        ax.set_xlim(0, 10)
        ax.set_ylim(0, limits[group["scenario"]])
        ax.set_xticks([0, 2, 4, 6, 8, 10])
        clean_axis(ax, theme)
    for ax in axes[:, 0]:
        ax.set_ylabel("FPS", color=theme["muted"], fontsize=10)
    for ax in axes[-1, :]:
        ax.set_xlabel("Sampled elapsed time (s)", color=theme["muted"], fontsize=10)
    save(fig, directory / ("fps-timeline" + suffix + ".png"))


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
        pie(directory, capture, theme, suffix)
        low_bars(directory, groups, theme, suffix)
        timeline(directory, groups, report["captures"], theme, suffix)


if __name__ == "__main__":
    main()
