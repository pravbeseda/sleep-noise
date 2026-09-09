#!/usr/bin/env python3
"""Tests for check_release_readiness.py, run by release.yml before the check.

Nothing else on CI exercises them, and the check fails silently when it is
wrong: a note it should have called stale goes to testers under a green run.
Each case builds a throwaway repository with a base commit and a head commit,
so the script runs against real revisions rather than a mocked tree.
"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "check_release_readiness.py")
NOTES = "app/src/main/play/release-notes"
VERSION = "app/version.properties"

BASE_NOTES = {"en-US": "Fixes the timer.", "de-DE": "Behebt den Timer."}
NEW_NOTES = {"en-US": "Adds surf.", "de-DE": "Fügt Brandung hinzu."}


class Repo:
    def __init__(self):
        self.dir = tempfile.mkdtemp()
        self.git("init", "--quiet")
        self.git("config", "user.email", "t@example.com")
        self.git("config", "user.name", "test")

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.dir, capture_output=True, text=True, check=True
        ).stdout.strip()

    def write(self, path, text):
        full = os.path.join(self.dir, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(text)

    def remove(self, path):
        os.remove(os.path.join(self.dir, path))

    def version(self, name):
        self.write(VERSION, f"# comment\nversionName={name}\n")

    def notes(self, texts, file="default.txt"):
        for locale, text in texts.items():
            self.write(f"{NOTES}/{locale}/{file}", text + "\n")

    def commit(self, message):
        self.git("add", "-A")
        self.git("commit", "--quiet", "--allow-empty", "-m", message)
        return self.git("rev-parse", "HEAD")

    def check(self, *revs):
        return subprocess.run(
            [sys.executable, SCRIPT, *revs], cwd=self.dir, capture_output=True, text=True
        )


def base_release(repo):
    """The previous release: two locales and versionName 1.0.4."""
    repo.version("1.0.4")
    repo.notes(BASE_NOTES)
    return repo.commit("base")


class CheckReleaseReadiness(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()
        self.base = base_release(self.repo)

    def assertPasses(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def assertFails(self, result, *fragments):
        self.assertNotEqual(result.returncode, 0, result.stdout)
        for fragment in fragments:
            self.assertIn(fragment, result.stdout + result.stderr)

    def test_new_notes_and_a_bumped_version_pass(self):
        self.repo.version("1.1.0")
        self.repo.notes(NEW_NOTES)
        head = self.repo.commit("release")
        self.assertPasses(self.repo.check(self.base, head))

    def test_a_locale_repeating_the_previous_note_fails_and_is_named(self):
        # The English is new, the German is last release's words: exactly the
        # release that a check over en-US alone would have let through.
        self.repo.version("1.1.0")
        self.repo.notes({"en-US": NEW_NOTES["en-US"], "de-DE": BASE_NOTES["de-DE"]})
        head = self.repo.commit("release")
        self.assertFails(self.repo.check(self.base, head), "::error::", "de-DE")

    def test_a_missing_default_note_fails(self):
        self.repo.version("1.1.0")
        self.repo.notes(NEW_NOTES)
        self.repo.remove(f"{NOTES}/de-DE/default.txt")
        # The directory must survive the deletion for the case to be a missing
        # file rather than a missing locale.
        self.repo.write(f"{NOTES}/de-DE/beta.txt", "Only for testers.\n")
        head = self.repo.commit("release")
        self.assertFails(self.repo.check(self.base, head), "de-DE/default.txt is missing")

    def test_an_unchanged_version_name_fails(self):
        self.repo.notes(NEW_NOTES)
        head = self.repo.commit("release")
        self.assertFails(self.repo.check(self.base, head), "versionName is still 1.0.4")

    def test_a_base_with_no_notes_passes_on_the_version_alone(self):
        repo = Repo()
        repo.version("1.0.4")
        base = repo.commit("before the store texts landed")
        repo.version("1.1.0")
        repo.notes(NEW_NOTES)
        head = repo.commit("release")
        self.assertPasses(repo.check(base, head))

    def test_a_base_with_no_notes_still_needs_a_version_bump(self):
        repo = Repo()
        repo.version("1.0.4")
        base = repo.commit("before the store texts landed")
        repo.notes(NEW_NOTES)
        head = repo.commit("release")
        self.assertFails(repo.check(base, head), "versionName is still 1.0.4")

    def test_a_renamed_note_with_the_same_text_fails(self):
        # A path comparison calls this a change; the text is what testers read.
        repo = Repo()
        repo.version("1.0.4")
        repo.notes(BASE_NOTES, file="production.txt")
        base = repo.commit("old layout")
        repo.version("1.1.0")
        for locale in BASE_NOTES:
            repo.remove(f"{NOTES}/{locale}/production.txt")
        repo.notes(BASE_NOTES)
        head = repo.commit("renamed")
        self.assertFails(repo.check(base, head), "de-DE", "en-US")

    def test_a_repeated_note_beside_a_new_locale_fails(self):
        # "Some text is new" is true here — the new locale's is — and the check
        # must still see the two that were left on the previous release's words.
        self.repo.version("1.1.0")
        self.repo.notes({**BASE_NOTES, "ru-RU": "Добавлен прибой."})
        head = self.repo.commit("release")
        self.assertFails(self.repo.check(self.base, head), "de-DE", "en-US")

    def test_an_empty_base_is_the_first_release_and_passes(self):
        # No v*+* tag exists yet, so release.yml hands over an empty base:
        # there is nothing to repeat and no previous name to differ from.
        self.repo.notes(NEW_NOTES)
        head = self.repo.commit("first release")
        result = self.repo.check("", head)
        self.assertPasses(result)
        self.assertIn("first release", result.stdout)

    def test_a_missing_base_argument_is_refused(self):
        # An empty string is a decision the caller made; no argument at all is
        # a workflow that lost its variable, and must not read as a first release.
        result = self.repo.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("usage", (result.stdout + result.stderr).lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
