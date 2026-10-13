package io.github.partywith8.epd.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Private layout prevents shader mods from expanding native BLOCK vertex data. */
public final class MeshFormat {
    public static final int STRIDE = 28;
    public static final VertexFormat FORMAT =
            VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Color", GpuFormat.RGBA8_UNORM)
                    .addAttribute("UV0", GpuFormat.RG32_FLOAT)
                    .addAttribute("Column", GpuFormat.RG16_SINT)
                    .build();
}
