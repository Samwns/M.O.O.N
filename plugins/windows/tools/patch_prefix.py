#!/usr/bin/env python3
"""Troca o prefixo fixo do rootfs do Winlator por outro de MESMO comprimento.

O rootfs do Winlator 10 (glibc 2.39, Wine 10.10) foi compilado com o prefixo
/data/data/com.winlator/files/rootfs (36 bytes), gravado no PT_INTERP, no
ld.so, no ld.so.cache, em RUNPATHs e em arquivos de config. Um caminho novo
com o mesmo comprimento pode ser obtido repetindo barras ("a//b" == "a/b").

uso: python3 -I patch_prefix.py <dir_rootfs> <novo_prefixo_absoluto>
"""
import os, sys

OLD = b"/data/data/com.winlator/files/rootfs"

def pad(new: str) -> bytes:
    n = new.rstrip("/").encode()
    if len(n) > len(OLD):
        sys.exit(f"prefixo novo tem {len(n)} bytes; o maximo e {len(OLD)}")
    # insere barras extras logo depois da primeira barra: "//////data/..."
    return b"/" * (len(OLD) - len(n)) + n

def main():
    root, new = sys.argv[1], pad(sys.argv[2])
    assert len(new) == len(OLD)
    files = links = 0
    for d, dirs, names in os.walk(root):
        for name in names + dirs:
            p = os.path.join(d, name)
            if os.path.islink(p):
                t = os.readlink(p).encode()
                if OLD in t:
                    os.unlink(p); os.symlink(t.replace(OLD, new), p); links += 1
                continue
            if not os.path.isfile(p):
                continue
            with open(p, "rb") as f:
                b = f.read()
            if OLD in b:
                st = os.stat(p)
                with open(p, "wb") as f:
                    f.write(b.replace(OLD, new))
                os.chmod(p, st.st_mode)
                files += 1
    print(f"prefixo -> {new.decode()} | arquivos: {files} | links: {links}")

if __name__ == "__main__":
    main()
