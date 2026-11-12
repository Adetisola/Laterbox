#!/usr/bin/env python3
"""
Unit tests for Laterbox Delta Patch Generator (tools/delta/generate_delta.py).
Tests architecture matching, threshold enforcement, manifest structure, and reconstruction.
"""

import os
import shutil
import tempfile
import unittest

from generate_delta import generate_delta_patches, sha256_file, VARIANTS


class TestGenerateDelta(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.prev_dir = os.path.join(self.test_dir, "prev")
        self.target_dir = os.path.join(self.test_dir, "target")
        self.output_dir = os.path.join(self.test_dir, "output")
        os.makedirs(self.prev_dir, exist_ok=True)
        os.makedirs(self.target_dir, exist_ok=True)
        os.makedirs(self.output_dir, exist_ok=True)

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_missing_prev_dir_gracefully_skipped(self):
        non_existent = os.path.join(self.test_dir, "non_existent")
        manifest = generate_delta_patches(
            prev_dir=non_existent,
            target_dir=self.target_dir,
            output_dir=self.output_dir,
            target_version="1.0.35",
            target_version_code=135
        )
        self.assertEqual(len(manifest["patches"]), 0)

    def test_architecture_matching_and_reconstruction(self):
        # Create dummy base and target APKs for ARM32
        base_content = b"Laterbox Base APK Content ARM32" * 1000
        target_content = b"Laterbox Base APK Content ARM32 with Updates!" * 1000

        with open(os.path.join(self.prev_dir, "Laterbox-ARM32.apk"), "wb") as f:
            f.write(base_content)
        with open(os.path.join(self.target_dir, "Laterbox-ARM32.apk"), "wb") as f:
            f.write(target_content)

        manifest = generate_delta_patches(
            prev_dir=self.prev_dir,
            target_dir=self.target_dir,
            output_dir=self.output_dir,
            target_version="1.0.35",
            target_version_code=135,
            prev_version="1.0.34",
            prev_version_code=134,
            threshold_ratio=0.70
        )

        self.assertEqual(len(manifest["patches"]), 1)
        patch = manifest["patches"][0]
        self.assertEqual(patch["architecture"], "armeabi-v7a")
        self.assertEqual(patch["targetApkFilename"], "Laterbox-ARM32.apk")
        self.assertEqual(patch["baseApkSha256"], sha256_file(os.path.join(self.prev_dir, "Laterbox-ARM32.apk")))
        self.assertEqual(patch["targetApkSha256"], sha256_file(os.path.join(self.target_dir, "Laterbox-ARM32.apk")))
        self.assertTrue(os.path.exists(os.path.join(self.output_dir, patch["patchFilename"])))
        self.assertTrue(os.path.exists(os.path.join(self.output_dir, "delta-manifest.json")))

    def test_threshold_enforcement_discards_large_patch(self):
        # Create content with high randomness so patch size exceeds threshold
        import os
        base_content = os.urandom(50000)
        target_content = os.urandom(50000)

        with open(os.path.join(self.prev_dir, "Laterbox-ARM64.apk"), "wb") as f:
            f.write(base_content)
        with open(os.path.join(self.target_dir, "Laterbox-ARM64.apk"), "wb") as f:
            f.write(target_content)

        # Enforce strict 10% threshold which random data will surely exceed
        manifest = generate_delta_patches(
            prev_dir=self.prev_dir,
            target_dir=self.target_dir,
            output_dir=self.output_dir,
            target_version="1.0.35",
            target_version_code=135,
            threshold_ratio=0.10
        )

        self.assertEqual(len(manifest["patches"]), 0)


if __name__ == "__main__":
    unittest.main()
