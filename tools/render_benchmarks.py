"""Generate README figures from the published aggregate data (requires matplotlib)."""

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

BACKGROUND = "#101820"
TEXT = "#edf3f8"
MUTED = "#acbbc9"
TEAL = "#59d8bd"
AMBER = "#ffcc75"
BLUE = "#7aaefe"
GRAY = "#667b91"


def style_axis(ax):
    ax.set_facecolor(BACKGROUND)
    ax.set_axisbelow(True)
    ax.grid(axis="y", color="#334453", linewidth=0.6)
    ax.tick_params(colors=MUTED, length=0, pad=8)
    for spine in ax.spines.values():
        spine.set_visible(False)


def save(fig, path):
    fig.savefig(
        path,
        dpi=160,
        facecolor=BACKGROUND,
        metadata={"Title": "Synthetic Terrain MC benchmark", "Software": "Matplotlib"},
    )
    plt.close(fig)


def main():
    directory = ROOT / "docs" / "benchmarks"
    report = json.loads(
        (directory / "stress-2026-10-14.json").read_text(encoding="utf-8")
    )
    captures = report["captures"]
    assert [c["capture"] for c in captures] == ["10-A", "10-B", "25-A", "25-B"]
    plt.rcParams.update(
        {
            "font.family": "DejaVu Sans",
            "font.size": 11,
            "text.color": TEXT,
            "axes.labelcolor": MUTED,
            "axes.titlecolor": TEXT,
        }
    )

    fig, axes = plt.subplots(1, 2, figsize=(13, 5.8), facecolor=BACKGROUND)
    fig.subplots_adjust(left=0.065, right=0.97, bottom=0.26, top=0.74, wspace=0.25)
    fig.text(0.065, 0.92, "STM / TERRAIN STRESS", fontsize=21, weight="bold")
    fig.text(
        0.065,
        0.86,
        "2560 × 1440  ·  RX 9070 XT + Ryzen 9 5900X  ·  Unlimited FPS",
        color=MUTED,
        fontsize=11,
    )
    labels = [c["capture"] for c in captures]
    for ax in axes:
        style_axis(ax)
        ax.set_xticks(range(4), labels)
    fps = [c["average_fps"] for c in captures]
    bars = axes[0].bar(range(4), fps, width=0.6, color=[TEAL, TEAL, AMBER, AMBER])
    axes[0].bar_label(
        bars, labels=[f"{v:.1f}" for v in fps], padding=8, color=TEXT, weight="bold"
    )
    axes[0].set_title("Average FPS", loc="left", fontsize=14, pad=14)
    axes[0].set_ylabel("FPS · higher is better")
    axes[0].set_ylim(0, 520)
    axes[0].yaxis.set_major_locator(MultipleLocator(100))

    width = 0.23
    for offset, key, label, color in [
        (-1, "p50", "p50", TEAL),
        (0, "p95", "p95", BLUE),
        (1, "p99", "p99", AMBER),
    ]:
        values = [c["sampled_client_loop_ms"][key] for c in captures]
        axes[1].bar(
            [x + offset * width for x in range(4)],
            values,
            width,
            label=label,
            color=color,
        )
    axes[1].set_title("Client loop timing", loc="left", fontsize=14, pad=14)
    axes[1].set_ylabel("ms · lower is better")
    axes[1].set_ylim(0, 9.2)
    axes[1].yaxis.set_major_locator(MultipleLocator(2))
    axes[1].legend(
        frameon=False, labelcolor=TEXT, ncol=3, loc="upper left", fontsize=10
    )
    fig.text(
        0.065,
        0.135,
        "10-A / 10-B: 10 chunks, STM 0.2.2     •     25-A / 25-B: 25 chunks, dev 0.3.0",
        color=MUTED,
        fontsize=10,
    )
    fig.text(
        0.065,
        0.085,
        "Four ~10 s F3+L captures during stress. Different builds; no idle baseline.",
        color=MUTED,
        fontsize=10,
    )
    fig.text(
        0.065,
        0.04,
        "Timing percentiles are CPU-side loop samples, not GPU or display-presentation times.",
        color=MUTED,
        fontsize=10,
    )
    save(fig, directory / "stress-performance.png")

    # Render is inclusive of the STM pass. Subtract the nested scope so that
    # the pie contains disjoint slices instead of counting that work twice.
    stages = captures[-1]["cpu_profiler_stages"]
    swap = stages["swapBuffers"]["percent_of_profiled_root"]
    render = stages["render"]["percent_of_profiled_root"]
    terrain = stages["epd_terrain"]["percent_of_profiled_root"]
    shares = [swap, terrain, render - terrain, 100 - swap - render]
    assert all(math.isfinite(v) and v >= 0 for v in shares)
    assert math.isclose(sum(shares), 100, abs_tol=1e-8)
    fig = plt.figure(figsize=(10, 5.4), facecolor=BACKGROUND)
    fig.text(0.06, 0.91, "WHERE THE PROFILED TIME GOES", fontsize=19, weight="bold")
    fig.text(
        0.06,
        0.84,
        "25-chunk stress · Run 25-B · CPU profiler root time",
        color=MUTED,
        fontsize=11,
    )
    ax = fig.add_axes((0.035, 0.15, 0.48, 0.64))
    wedges, _ = ax.pie(
        shares,
        colors=[BLUE, TEAL, AMBER, GRAY],
        startangle=90,
        counterclock=False,
        wedgeprops={"edgecolor": BACKGROUND, "linewidth": 2},
    )
    labels = [
        "Swap / presentation",
        "STM render pass",
        "Other rendering",
        "Other profiled work",
    ]
    fig.legend(
        wedges,
        [f"{name}   {value:.2f}%" for name, value in zip(labels, shares)],
        loc="center left",
        bbox_to_anchor=(0.50, 0.49),
        frameon=False,
        labelcolor=TEXT,
        fontsize=12,
        labelspacing=1.5,
    )
    fig.text(
        0.06,
        0.085,
        "Swap includes driver/presentation waiting. Percentages are not GPU utilization.",
        color=MUTED,
        fontsize=10,
    )
    fig.text(
        0.06,
        0.04,
        "STM is subtracted from total rendering; the four slices sum to 100%.",
        color=MUTED,
        fontsize=10,
    )
    save(fig, directory / "stress-cpu-profile.png")


if __name__ == "__main__":
    main()
