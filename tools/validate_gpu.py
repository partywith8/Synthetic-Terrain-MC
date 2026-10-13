"""Compile and compare actual STM GLSL in a hidden disposable Windows OpenGL context."""

import ctypes as c
import json
import sys
import zipfile
from ctypes import wintypes as w
from pathlib import Path

R = Path(__file__).resolve().parents[1]
u = c.WinDLL("user32")
g = c.WinDLL("gdi32")
gl = c.WinDLL("opengl32")


class PFD(c.Structure):
    _fields_ = [
        ("nSize", w.WORD),
        ("nVersion", w.WORD),
        ("dwFlags", w.DWORD),
        ("iPixelType", c.c_ubyte),
        ("cColorBits", c.c_ubyte),
        ("cRedBits", c.c_ubyte),
        ("cRedShift", c.c_ubyte),
        ("cGreenBits", c.c_ubyte),
        ("cGreenShift", c.c_ubyte),
        ("cBlueBits", c.c_ubyte),
        ("cBlueShift", c.c_ubyte),
        ("cAlphaBits", c.c_ubyte),
        ("cAlphaShift", c.c_ubyte),
        ("cAccumBits", c.c_ubyte),
        ("cAccumRedBits", c.c_ubyte),
        ("cAccumGreenBits", c.c_ubyte),
        ("cAccumBlueBits", c.c_ubyte),
        ("cAccumAlphaBits", c.c_ubyte),
        ("cDepthBits", c.c_ubyte),
        ("cStencilBits", c.c_ubyte),
        ("cAuxBuffers", c.c_ubyte),
        ("iLayerType", c.c_ubyte),
        ("bReserved", c.c_ubyte),
        ("dwLayerMask", w.DWORD),
        ("dwVisibleMask", w.DWORD),
        ("dwDamageMask", w.DWORD),
    ]


u.CreateWindowExW.argtypes = [
    w.DWORD,
    w.LPCWSTR,
    w.LPCWSTR,
    w.DWORD,
    c.c_int,
    c.c_int,
    c.c_int,
    c.c_int,
    w.HWND,
    w.HMENU,
    w.HINSTANCE,
    c.c_void_p,
]
u.CreateWindowExW.restype = w.HWND
u.GetDC.argtypes = [w.HWND]
u.GetDC.restype = w.HDC
u.ReleaseDC.argtypes = [w.HWND, w.HDC]
u.DestroyWindow.argtypes = [w.HWND]
g.ChoosePixelFormat.argtypes = [w.HDC, c.POINTER(PFD)]
g.SetPixelFormat.argtypes = [w.HDC, c.c_int, c.POINTER(PFD)]
gl.wglCreateContext.argtypes = [w.HDC]
gl.wglCreateContext.restype = c.c_void_p
gl.wglMakeCurrent.argtypes = [w.HDC, c.c_void_p]
gl.wglDeleteContext.argtypes = [c.c_void_p]
gl.wglGetProcAddress.argtypes = [c.c_char_p]
gl.wglGetProcAddress.restype = c.c_void_p
gl.glGetString.argtypes = [c.c_uint]
gl.glGetString.restype = c.c_char_p


def fn(name, ret, *args):
    address = gl.wglGetProcAddress(name.encode())
    assert address and address not in [1, 2, 3, c.c_void_p(-1).value], name
    return c.WINFUNCTYPE(ret, *args)(address)


class Vertex(c.Structure):
    _fields_ = [
        ("position", c.c_float * 3),
        ("color", c.c_ubyte * 4),
        ("uv", c.c_float * 2),
        ("column", c.c_short * 2),
    ]


def main():
    hwnd = u.CreateWindowExW(
        0,
        "STATIC",
        "STM shader validation",
        0x80000000,
        0,
        0,
        16,
        16,
        None,
        None,
        None,
        None,
    )
    assert hwnd
    dc = u.GetDC(hwnd)
    ctx = None
    program = None
    shaders = []
    buffers = (c.c_uint * 5)()
    vao = c.c_uint()
    try:
        p = PFD()
        p.nSize = c.sizeof(PFD)
        p.nVersion = 1
        p.dwFlags = 4 | 32
        p.cColorBits = 24
        p.cDepthBits = 24
        pf = g.ChoosePixelFormat(dc, c.byref(p))
        assert pf and g.SetPixelFormat(dc, pf, c.byref(p))
        ctx = gl.wglCreateContext(dc)
        assert ctx and gl.wglMakeCurrent(dc, ctx)
        program = fn("glCreateProgram", c.c_uint)()
        for ext, kind in [("vsh", 0x8B31), ("fsh", 0x8B30)]:
            shader = fn("glCreateShader", c.c_uint, c.c_uint)(kind)
            shaders.append(shader)
            data = c.c_char_p(
                (
                    R / f"src/client/resources/assets/epd/shaders/core/terrain.{ext}"
                ).read_bytes()
            )
            fn(
                "glShaderSource",
                None,
                c.c_uint,
                c.c_int,
                c.POINTER(c.c_char_p),
                c.POINTER(c.c_int),
            )(shader, 1, c.byref(data), None)
            fn("glCompileShader", None, c.c_uint)(shader)
            ok = c.c_int()
            fn("glGetShaderiv", None, c.c_uint, c.c_uint, c.POINTER(c.c_int))(
                shader, 0x8B81, c.byref(ok)
            )
            log = c.create_string_buffer(8192)
            fn(
                "glGetShaderInfoLog",
                None,
                c.c_uint,
                c.c_int,
                c.POINTER(c.c_int),
                c.c_char_p,
            )(shader, 8192, None, log)
            assert ok.value, (ext, log.value)
            fn("glAttachShader", None, c.c_uint, c.c_uint)(program, shader)
        names = (c.c_char_p * 1)(b"gl_Position")
        fn(
            "glTransformFeedbackVaryings",
            None,
            c.c_uint,
            c.c_int,
            c.POINTER(c.c_char_p),
            c.c_uint,
        )(program, 1, names, 0x8C8C)
        fn("glLinkProgram", None, c.c_uint)(program)
        ok = c.c_int()
        fn("glGetProgramiv", None, c.c_uint, c.c_uint, c.POINTER(c.c_int))(
            program, 0x8B82, c.byref(ok)
        )
        log = c.create_string_buffer(8192)
        fn(
            "glGetProgramInfoLog",
            None,
            c.c_uint,
            c.c_int,
            c.POINTER(c.c_int),
            c.c_char_p,
        )(program, 8192, None, log)
        assert ok.value, log.value
        fn("glUseProgram", None, c.c_uint)(program)
        gen = fn("glGenBuffers", None, c.c_int, c.POINTER(c.c_uint))
        gen(5, buffers)
        bind = fn("glBindBuffer", None, c.c_uint, c.c_uint)
        data = fn("glBufferData", None, c.c_uint, c.c_ssize_t, c.c_void_p, c.c_uint)
        base = fn("glBindBufferBase", None, c.c_uint, c.c_uint, c.c_uint)
        identity = [1.0 if i % 5 == 0 else 0.0 for i in range(16)]
        for name, index, buffer, values in [
            (b"Projection", 1, buffers[2], identity),
            (
                b"DynamicTransforms",
                2,
                buffers[3],
                identity + [1.0] * 4 + [0.0] * 4 + identity,
            ),
        ]:
            bind(0x8A11, buffer)
            arr = (c.c_float * len(values))(*values)
            data(0x8A11, c.sizeof(arr), arr, 0x88E8)
            base(0x8A11, index, buffer)
            block = fn("glGetUniformBlockIndex", c.c_uint, c.c_uint, c.c_char_p)(
                program, name
            )
            fn("glUniformBlockBinding", None, c.c_uint, c.c_uint, c.c_uint)(
                program, block, index
            )
        block = fn("glGetUniformBlockIndex", c.c_uint, c.c_uint, c.c_char_p)(
            program, b"MotionParams"
        )
        fn("glUniformBlockBinding", None, c.c_uint, c.c_uint, c.c_uint)(
            program, block, 0
        )
        fn("glGenVertexArrays", None, c.c_int, c.POINTER(c.c_uint))(1, c.byref(vao))
        fn("glBindVertexArray", None, c.c_uint)(vao.value)
        bind(0x8892, buffers[0])
        for name, count, kind, normal, offset in [
            (b"Position", 3, 0x1406, 0, 0),
            (b"Color", 4, 0x1401, 1, 12),
            (b"UV0", 2, 0x1406, 0, 16),
            (b"Column", 2, 0x1402, 0, 24),
        ]:
            loc = fn("glGetAttribLocation", c.c_int, c.c_uint, c.c_char_p)(
                program, name
            )
            if loc < 0:
                continue
            fn("glEnableVertexAttribArray", None, c.c_uint)(loc)
            if name == b"Column":
                fn(
                    "glVertexAttribIPointer",
                    None,
                    c.c_uint,
                    c.c_int,
                    c.c_uint,
                    c.c_int,
                    c.c_void_p,
                )(loc, count, kind, 28, c.c_void_p(offset))
            else:
                fn(
                    "glVertexAttribPointer",
                    None,
                    c.c_uint,
                    c.c_int,
                    c.c_uint,
                    c.c_ubyte,
                    c.c_int,
                    c.c_void_p,
                )(loc, count, kind, normal, 28, c.c_void_p(offset))
        bind(0x8C8E, buffers[1])
        data(0x8C8E, 64, None, 0x88E8)
        base(0x8C8E, 0, buffers[1])
        gl.glEnable(0x8C89)
        worst = 0
        total = 0
        for case in json.loads((R / "build/gpu-cases.json").read_text()):
            values = case["params"]
            arr = (c.c_float * 68)(*values)
            bind(0x8A11, buffers[4])
            data(0x8A11, c.sizeof(arr), arr, 0x88E8)
            base(0x8A11, 0, buffers[4])
            x, z = case["column"]
            verts = (Vertex * 4)()
            for v, (dx, dz) in zip(verts, [(0, 0), (1, 0), (1, 1), (0, 1)]):
                v.position[:] = [x + dx, 64, z + dz]
                v.color[:] = [255] * 4
                v.uv[:] = [dx, dz]
                v.column[:] = [x, z]
            bind(0x8892, buffers[0])
            data(0x8892, c.sizeof(verts), verts, 0x88E8)
            fn("glBeginTransformFeedback", None, c.c_uint)(0)
            gl.glDrawArrays(0, 0, 4)
            fn("glEndTransformFeedback", None)()
            output = (c.c_float * 16)()
            bind(0x8C8E, buffers[1])
            fn(
                "glGetBufferSubData",
                None,
                c.c_uint,
                c.c_ssize_t,
                c.c_ssize_t,
                c.c_void_p,
            )(0x8C8E, 0, 64, output)
            for i in range(4):
                error = abs(output[i * 4 + 1] - 64 - case["height"])
                worst = max(worst, error)
                assert error < 0.00015, (case, output[:], error)
                assert (
                    output[i * 4] == verts[i].position[0]
                    and output[i * 4 + 2] == verts[i].position[2]
                )
                total += 1
        report = {
            "renderer": gl.glGetString(0x1F01).decode(),
            "vertices": total,
            "maximum_java_gpu_error": worst,
            "rigid_blocks": "passed",
            "live_minecraft_rendering": "not established by this harness",
        }
        (R / "build/gpu-validation.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report))
    finally:
        if ctx:
            gl.glDisable(0x8C89)
            if vao.value:
                fn("glDeleteVertexArrays", None, c.c_int, c.POINTER(c.c_uint))(
                    1, c.byref(vao)
                )
            if buffers[0]:
                fn("glDeleteBuffers", None, c.c_int, c.POINTER(c.c_uint))(5, buffers)
            if program:
                fn("glDeleteProgram", None, c.c_uint)(program)
            for shader in shaders:
                fn("glDeleteShader", None, c.c_uint)(shader)
            gl.wglMakeCurrent(None, None)
            gl.wglDeleteContext(ctx)
        u.ReleaseDC(hwnd, dc)
        u.DestroyWindow(hwnd)


if __name__ == "__main__":
    main()
