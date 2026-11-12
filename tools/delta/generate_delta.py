#!/usr/bin/env python3
"""
Laterbox Delta Patch Generator.

Generates BSDIFF40 differential patches between consecutive Laterbox APK releases,
validates that reconstructed APKs match the target APKs byte-for-byte, enforces
size threshold policies (default: patch must be < 70% of full APK size), and
produces a machine-readable delta-manifest.json for GitHub Releases.
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
import time

try:
    import bsdiff4
except ImportError:
    print("Error: 'bsdiff4' module is required. Install with: pip install bsdiff4", file=sys.stderr)
    sys.exit(1)

# Default size threshold: patch size must be less than 70% of full APK size
DEFAULT_THRESHOLD_RATIO = 0.70

# Architecture variants configuration: (label, filename, architecture_id, human_label)
VARIANTS = [
    ("ARM32", "Laterbox-ARM32.apk", "armeabi-v7a", "ARM32 (armeabi-v7a)"),
    ("ARM64", "Laterbox-ARM64.apk", "arm64-v8a", "ARM64 (arm64-v8a)"),
    ("Universal", "Laterbox-Universal.apk", "universal", "Universal"),
]


def sha256_file(filepath: str) -> str:
    """Computes SHA-256 digest of a file in 64 KB streaming chunks."""
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().lower()


def generate_delta_patches(
    prev_dir: str,
    target_dir: str,
    output_dir: str,
    target_version: str,
    target_version_code: int,
    prev_version: str = "",
    prev_version_code: int = 0,
    threshold_ratio: float = DEFAULT_THRESHOLD_RATIO,
) -> dict:
    """
    Generates and verifies delta patches between corresponding architecture APKs.
    Returns the generated manifest dictionary.
    """
    os.makedirs(output_dir, exist_ok=True)
    manifest = {
        "schemaVersion": 1,
        "targetVersion": target_version,
        "targetVersionCode": target_version_code,
        "generatedAt": int(time.time() * 1000),
        "patches": [],
    }

    if not prev_dir or not os.path.isdir(prev_dir):
        print(f"Notice: Previous release directory '{prev_dir}' is missing or not a directory. Skipping delta generation.")
        return manifest

    print(f"=== Generating Delta Patches for Laterbox {target_version} (threshold: {threshold_ratio * 100:.0f}%) ===")

    for label, apk_filename, arch_id, human_label in VARIANTS:
        prev_apk = os.path.join(prev_dir, apk_filename)
        target_apk = os.path.join(target_dir, apk_filename)

        if not os.path.isfile(prev_apk):
            print(f"[{label}] Previous APK not found at '{prev_apk}'. Skipping delta for this variant.")
            continue
        if not os.path.isfile(target_apk):
            print(f"[{label}] Target APK not found at '{target_apk}'. Skipping delta for this variant.")
            continue

        base_sha = sha256_file(prev_apk)
        target_sha = sha256_file(target_apk)
        target_size = os.path.getsize(target_apk)
        prev_size = os.path.getsize(prev_apk)

        if base_sha == target_sha:
            print(f"[{label}] Base APK and Target APK are identical (SHA-256: {base_sha}). No patch needed.")
            continue

        patch_filename = f"Laterbox-{label}-{prev_version or 'prev'}-to-{target_version}.patch"
        patch_path = os.path.join(output_dir, patch_filename)

        print(f"[{label}] Generating patch: {prev_size / (1024*1024):.2f} MB -> {target_size / (1024*1024):.2f} MB...")
        t0 = time.time()
        try:
            bsdiff4.file_diff(prev_apk, target_apk, patch_path)
        except Exception as e:
            print(f"[{label}] Error generating patch: {e}", file=sys.stderr)
            if os.path.exists(patch_path):
                os.remove(patch_path)
            continue

        diff_time = time.time() - t0
        patch_size = os.path.getsize(patch_path)
        patch_ratio = patch_size / target_size if target_size > 0 else 1.0
        savings = (1.0 - patch_ratio) * 100

        print(f"[{label}] Patch generated in {diff_time:.2f}s: {patch_size / (1024*1024):.2f} MB ({patch_ratio * 100:.1f}% of full APK size, {savings:.1f}% savings)")

        # Threshold check
        if patch_ratio >= threshold_ratio:
            print(f"[{label}] Patch size ({patch_size} bytes) exceeds threshold ({threshold_ratio * 100:.0f}% of {target_size} bytes). Discarding patch.")
            os.remove(patch_path)
            continue

        # Verification step: reconstruct and verify byte-for-byte SHA-256 match
        print(f"[{label}] Verifying patch reconstruction...")
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as recon_tmp:
            recon_path = recon_tmp.name

        try:
            t1 = time.time()
            bsdiff4.file_patch(prev_apk, recon_path, patch_path)
            recon_time = time.time() - t1
            recon_sha = sha256_file(recon_path)

            if recon_sha != target_sha:
                print(f"[{label}] CRITICAL: Reconstructed APK SHA-256 ({recon_sha}) does not match target APK SHA-256 ({target_sha})!", file=sys.stderr)
                os.remove(patch_path)
                continue
            else:
                print(f"[{label}] Verified byte-for-byte match in {recon_time:.2f}s! (SHA-256: {recon_sha})")
        finally:
            if os.path.exists(recon_path):
                os.remove(recon_path)

        patch_sha = sha256_file(patch_path)
        patch_entry = {
            "architecture": arch_id,
            "architectureLabel": human_label,
            "baseVersion": prev_version or "previous",
            "baseVersionCode": prev_version_code,
            "baseApkSha256": base_sha,
            "targetApkFilename": apk_filename,
            "targetApkSha256": target_sha,
            "targetApkSize": target_size,
            "patchFilename": patch_filename,
            "patchSha256": patch_sha,
            "patchSize": patch_size,
            "patchAlgorithm": "BSDIFF40",
        }
        manifest["patches"].append(patch_entry)
        print(f"[{label}] Added patch to manifest: {patch_filename} ({patch_size} bytes)")

    # Write delta-manifest.json
    manifest_path = os.path.join(output_dir, "delta-manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"\nSuccessfully wrote manifest with {len(manifest['patches'])} eligible patch(es) to {manifest_path}")
    return manifest


def main():
    parser = argparse.ArgumentParser(description="Generate and verify Laterbox delta APK patches")
    parser.add_argument("--prev-dir", required=True, help="Directory containing previous release APKs")
    parser.add_argument("--target-dir", required=True, help="Directory containing target release APKs")
    parser.add_argument("--output-dir", required=True, help="Directory where patches and manifest will be written")
    parser.add_argument("--target-version", required=True, help="Target release version name (e.g. 1.0.35)")
    parser.add_argument("--target-version-code", type=int, default=1, help="Target release version code")
    parser.add_argument("--prev-version", default="", help="Previous release version name (e.g. 1.0.34)")
    parser.add_argument("--prev-version-code", type=int, default=0, help="Previous release version code")
    parser.add_argument("--threshold-ratio", type=float, default=DEFAULT_THRESHOLD_RATIO, help="Patch size threshold ratio (default: 0.70)")

    args = parser.parse_args()

    generate_delta_patches(
        prev_dir=args.prev_dir,
        target_dir=args.target_dir,
        output_dir=args.output_dir,
        target_version=args.target_version,
        target_version_code=args.target_version_code,
        prev_version=args.prev_version,
        prev_version_code=args.prev_version_code,
        threshold_ratio=args.threshold_ratio,
    )


if __name__ == "__main__":
    main()
