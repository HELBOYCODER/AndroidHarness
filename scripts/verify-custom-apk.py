#!/usr/bin/env python3
"""Verify the bounded ARM64 APK/runtime contract; not a device or signature test."""
import gzip
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP entry'
    abis = sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})
    assert abis == ['arm64-v8a'], f'Unexpected APK ABI set: {abis}'
    required = ['libharness_local.so', 'libggml.so', 'libggml-base.so', 'libggml-cpu.so', 'libllama.so', 'libllama-common.so', 'libmtmd.so']
    for library in required:
        content = archive.read(f'lib/arm64-v8a/{library}')
        assert content[:6] == b'\x7fELF\x02\x01', f'Not 64-bit little-endian ELF: {library}'
        assert int.from_bytes(content[18:20], 'little') == 183, f'Not AArch64: {library}'
    spec = json.loads(archive.read('assets/gpu-runtime/arm64-v8a.json'))
    assert spec['schema'] == 1 and spec['abi'] == 'arm64-v8a'
    assert 0 < spec['bytes'] <= 134217728 and 0 < spec['downloadBytes'] <= 134217728
    # AAPT inflates .gz assets automatically; .bin must preserve the actual gzip representation.
    path = f"assets/gpu-runtime/packages/{spec['asset']}.bin"
    packed = archive.read(path)
    assert packed[:2] == b'\x1f\x8b', 'Bundled runtime is not gzip'
    assert len(packed) == spec['downloadBytes'], 'Compressed size mismatch'
    digest = hashlib.sha256()
    size = 0
    prefix = b''
    with gzip.GzipFile(fileobj=io.BytesIO(packed)) as source:
        while block := source.read(65536):
            size += len(block)
            assert size <= spec['bytes'], 'Runtime exceeds manifest size'
            digest.update(block)
            if not prefix:
                prefix = block[:64]
    assert size == spec['bytes'] and digest.hexdigest() == spec['sha256'], 'Runtime checksum/size mismatch'
    assert prefix[:6] == b'\x7fELF\x02\x01' and int.from_bytes(prefix[18:20], 'little') == 183
print(json.dumps({'apk': apk.name, 'bytes': apk.stat().st_size,
    'sha256': hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(),
    'abis': abis, 'required_native_libraries': len(required), 'gpu_runtime_sha256': spec['sha256'],
    'gpu_runtime_gzip_bytes': len(packed), 'gpu_runtime_bytes': size, 'status': 'PASS'}, indent=2))
