# Resticopia

[![F-Droid](https://img.shields.io/f-droid/v/org.dydlakcloud.resticopia.svg)](https://f-droid.org/packages/org.dydlakcloud.resticopia/)
[![GNU General Public License, Version 2](https://img.shields.io/github/license/lhns/restic-android.svg?maxAge=3600)](https://www.gnu.org/licenses/gpl-2.0.html)
[![Build Status](https://codeberg.org/dawdyd/resticopia/badges/workflows/build.yml/badge.svg)](https://codeberg.org/dawdyd/resticopia/actions)
[![Code Quality](https://codeberg.org/dawdyd/resticopia/badges/workflows/code-quality.yml/badge.svg)](https://codeberg.org/dawdyd/resticopia/actions)

 <a href="https://f-droid.org/packages/org.dydlakcloud.resticopia/">
    <img src="docs/badges/fdroid-get-it-on.svg" alt="Get it on F-Droid" width="160"/>
  </a>
 <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://codeberg.org/dawdyd/resticopia">
    <img src="docs/badges/obtainium.png" alt="Obtainium" width="160"/>
  </a>

## Screenshots

<p>
  <img src="screenshots/2.png" width="175" alt="Repository Setup">
  <img src="screenshots/3.png" width="175" alt="Encrypted Backups">
  <img src="screenshots/4.png" width="175" alt="Folder Snapshots">
  <img src="screenshots/5.png" width="175" alt="Scheduled Backups">
  <img src="screenshots/6.png" width="175" alt="Browse & Restore">
</p>

## About

A mobile Android application that enables efficient and straightforward data backups powered by [Restic](https://restic.net) backup software.

The application uses a custom-built [PRoot](https://codeberg.org/dawdyd/build-proot-android) environment (based on the [build-proot-android](https://github.com/green-green-avk/build-proot-android) project) to run native [Restic](https://restic.net) and [Rclone](https://rclone.org) binaries directly on Android devices.

### Disclaimer
This is an **unofficial** application and is not developed or endorsed by the official Restic project team.

## Key Capabilities
- Repository Management: Create and configure Restic repositories (supports S3, B2, Rest, Local, and Rclone protocols)
- Snapshot Control: Browse and manage your backup snapshots
- Folder Selection: Choose which directories to include in backups
- Automated Scheduling: Set up recurring backup tasks
- Retention Policies: Define cleanup rules for individual folders
- Live Progress: Monitor backup operations through system notifications
- Rclone Integration: Access 40+ cloud storage providers via Rclone backend

## Donate

<a href="https://buymeacoffee.com/dawdyd">
  <img src="docs/badges/bmc_button.png" alt="Buy Me a Coffee" width="180"/>
</a>

### This Project
- All modifications and additions are also licensed under GNU General Public License v2.0
- See git commit history for detailed changes

### Original Work
- **Original Project**: [restic-android](https://github.com/lhns/restic-android) by [lhns](https://github.com/lhns)
- **Original License**: GNU General Public License v2.0

## Notice
See the file called NOTICE.

## License
This project uses the GNU General Public License, Version 2. See the file called LICENSE.
