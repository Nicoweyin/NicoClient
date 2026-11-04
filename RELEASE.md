# Future Windows releases

Do not upload or publish without the project owner's approval. `npm.cmd run package:win` uses `--publish never` and only builds locally. The updater targets public GitHub Releases at `Nicoweyin/NicoClient`; no GitHub token is embedded. A private repository would require a separate authenticated distribution design.

## Initial 0.1.0 release

The corrected-owner build is in `release-corrected/`; it was built separately to preserve the earlier `release/` artifacts, which target the old GitHub owner. The corrected installer, blockmap, and `latest.yml` are a matching set. The build is unsigned and neither installation nor updater delivery has been verified. Do not publish until the owner approves the source, risks, and release.

The local repository is initialized with `origin` set to `https://github.com/Nicoweyin/NicoClient.git`. Review the source and ignore rules and make the local initial commit using the owner's chosen author identity. Inspect the remote before pushing: an existing README or other history may require reconciliation rather than an initial push. Ask before pushing.

Only after approval and after the intended source commit is on GitHub, open `https://github.com/Nicoweyin/NicoClient/releases/new`. Choose a new tag `v0.1.0` targeting that commit, set a release title and notes that describe the early-development status, and attach these three files from `release-corrected/`:

- `NicoClient-Setup-0.1.0.exe`
- `NicoClient-Setup-0.1.0.exe.blockmap`
- `latest.yml`

Verify the tag, target commit, notes, filenames and metadata, then publish only with explicit approval. Do not include `win-unpacked` or the old `release/` artifacts. The first published version provides a baseline; verifying automatic upgrades requires a later, higher-version release.

1. Pick a new version greater than the currently published version. Update the package version and lockfile together (for example, `npm.cmd version patch --no-git-tag-version`); review the resulting changes. Never reuse a release version for different binaries.
2. Run the build, automated tests and smoke test documented in `TEST_RESULTS.md`. Run `npm.cmd run package:win` on Windows. Signing is a separate release-owner operation; keep certificates, passwords and tokens outside the repository.
3. Inspect `release/latest.yml` and verify that its version, filename, size and SHA-512 match `release/NicoClient-Setup-<version>.exe`. Prepare that installer, its `.exe.blockmap`, and `latest.yml` from the same build. Do not upload `win-unpacked`, local data or debug files.
4. After obtaining permission, create a GitHub Release with a matching version tag and meaningful release notes describing changes, migration considerations, known issues and rollback. Upload the three matching artifacts. A draft is useful for inspection, but update discovery normally targets a published release. Do not publish until approved.
5. Test from a separately installed older version using a test Windows user: check startup and manual checks, version and notes, explicit download and progress, prompt cancellation, install, restart, and offline/error behavior. Confirm `%APPDATA%\NicoClient` mods, config and settings remain intact. Test with real signed builds and published releases before claiming updates work.
6. If a release is broken, stop distribution and notify users. Ship the last known-good code as a **new, higher version** with fresh signed artifacts and metadata; ordinary updater behavior does not automatically downgrade. Test recovery from the broken version. Avoid deleting game data or overwriting user mods. If users cannot update, provide an approved manual installer and instructions.

## Windows signing and update trust

Current local builds are **unsigned**; Windows may show SmartScreen warnings, and unsigned installers cannot provide publisher identity or reliable Authenticode-based publisher verification. `electron-updater` signature checks have not been disabled, but an unsigned build and update metadata alone do not establish publisher authenticity. Before production, arrange a Windows code-signing certificate, configure electron-builder signing and a stable publisher identity, and test updater signature validation using valid, unsigned and wrong-publisher test artifacts. Do not distribute secrets in the application or repository. GitHub Release metadata and downloaded artifact hashes help detect corruption, not a malicious replacement of both metadata and installer.
