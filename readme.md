# Vesper

Vesper is a from-scratch PlayStation Portable emulator, written primarily in Kotlin.

It exists mostly as a personal systems-programming project: an excuse to work through CPU emulation, kernel/syscall design, ELF loading, and GPU command translation, independent of whether the end result ever competes with existing PSP emulators on compatibility or performance. It isn't trying to.

## Why the PSP

The PSP sits in a useful spot: its MIPS CPU, ELF-based executables, and syscall-driven kernel make it feel like emulating a small real operating system rather than a fixed-function piece of retro hardware, while still being tractable for one person working on it in their spare time.

## Pace

This project has no roadmap with dates and no finish line it's racing toward. It gets worked on when there's time and interest, which means long stretches of inactivity are expected and not a sign the project is abandoned.

If you're browsing this repo and it's been quiet for a while, that's normal.

## Contributing

Contributions, questions, and issues are welcome, but keep the pace of the project in mind, reviews and responses may not be fast. If you want to dig in, the codebase is organized around fairly clear boundaries between the CPU, memory, kernel, and GPU subsystems, which should make it possible to poke at one piece without needing to understand the whole thing first.

One hard rule for any contribution: no copyrighted PSP firmware, BIOS files, commercial game dumps, or ISOs anywhere in this repository, in an issue, or in CI. Test content must be freely redistributable homebrew only.

## License

No license has been chosen yet, so treat this code as "all rights reserved" for the time being. The likely direction is a GPL license (GPL-2.0 or GPL-3.0), in line with the norms of the emulation community. MIT has been considered too, but it's an open question whether a permissive license is actually the right fit for this kind of project — that'll get settled before this reaches a state where it matters much in practice.