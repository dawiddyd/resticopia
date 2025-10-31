# Design System - Resticopia

## Overview
This document outlines the design system used throughout the Resticopia Android app, ensuring consistency in typography, colors, spacing, and component design.

## Colors

### Brand Colors
- **Purple Main**: `#9322C8` - Used for FABs, buttons, and progress indicators

### Light Mode
- Primary: Material default
- Status Bar: White (`#FFFFFF`)
- Surface: White
- On Surface: Black
- Caption Text: Black with 40% opacity (`#66000000`)

### Dark Mode
- Primary: Material default
- Status Bar: Black (`#000000`)
- Surface: Dark gray
- On Surface: White
- Caption Text: White with 40% opacity

## Typography

All text uses **Plus Jakarta Sans** font family from Google Fonts.

## Settings Page Architecture

The settings page follows a modular, hierarchical structure with a main tile-based navigation:

### Main Settings Page
- **Layout**: Vertical tile list with 20dp padding
- **Tile Design**:
  - Background Color: `#E8E5EF` (settings_tile_background)
  - Corner Radius: 16dp
  - Elevation: 0dp (flat design)
  - Ripple Effect: Subtle gray (`#33000000` - 20% black opacity)
  - Spacing: 12dp between tiles, 16dp top margin
  
- **Tile Typography**:
  - Font: Plus Jakarta Sans, Semibold (Bold)
  - Size: 20sp
  - Color: `#35283C` (settings_tile_text)
  - Alignment: Left
  - Padding: 24dp horizontal, 20dp vertical

### Settings Categories

1. **Network**: Hostname and DNS configuration
2. **Notifications**: Ntfy topic and test notifications  
3. **Backup Rules**: Backup constraints and queued backups
4. **Rclone Configuration**: Rclone remote management
5. **Import/Export**: Settings backup and restore
6. **Utilities**: Download directory, unlock repos, clean cache

### Sub-Page Styling
Each category has its own dedicated fragment with consistent styling:
- Section labels use `CaptionReg` style
- Values use `Body17` style
- Edit buttons use purple (`#9322C8`) background
- Full-width buttons for primary actions
- 20dp padding throughout
- 32dp spacing between sections

### Text Styles

#### Title1
- **Weight**: Extra bold (800)
- **Size**: 34sp
- **Line Height**: 110% (37sp)
- **Letter Spacing**: -0.5px (-0.0147em)
- **Usage**: Main headings in detail views (folder name, repo name, snapshot hash)

#### Title2
- **Weight**: Bold (700)
- **Size**: 22sp
- **Line Height**: 28sp
- **Letter Spacing**: 0
- **Usage**: Section headers (Snapshots, Files)

#### Body17
- **Weight**: Semibold (600)
- **Size**: 17sp
- **Usage**: Primary list item text (folder names, file names, snapshot hashes)

#### Body15
- **Weight**: Semibold (600)
- **Size**: 15sp
- **Line Height**: 130% (19.5sp)
- **Color**: `#938AA5` (backup status color)
- **Usage**: Backup progress information (file counts, data sizes)

#### CaptionReg
- **Weight**: Medium (500)
- **Size**: 13sp
- **Color**: Black/White with 40% opacity
- **Usage**: Secondary information (timestamps, paths, file counts, sizes)

## Lists

### Folder List
**List Item Design:**
- **Icon**: Custom folder icon (22dp × 19dp, `#F2DAFD`)
- **Line 1**: Folder name (directory only) - Body17 style
- **Line 2**: Repository name + last backup timestamp - CaptionReg style
  - Format: "Repo name, last backup Mar 25, 2023" or "Repo name, no backups yet"
- **Spacing**: 16dp icon margin, 4dp between lines
- **Padding**: 20dp horizontal, 16dp vertical
- **Min Height**: 64dp

### Repository List
**List Item Design:**
- **Icon**: Custom repository icon (24dp × 24dp, `#F2DAFD` + `#FAF2FE`)
- **Line 1**: Repository name - Body17 style
- **Line 2**: Repository type - CaptionReg style
  - Types: "Local", "Amazon S3", "Backblaze B2", "REST Server", etc.
- **Spacing**: 16dp icon margin, 4dp between lines
- **Padding**: 20dp horizontal, 16dp vertical
- **Min Height**: 64dp

### Dividers
- **Color**: `#E0E0E0` (light) / `#424242` (dark)
- **Height**: 1dp
- **Padding**: 16dp left and right insets
- **Hidden**: When list has only one item

## Detail Views

#### Folder Details Header
**Design:**
- **Line 1**: Folder name (directory only) - Title1 style
- **Line 2**: Full folder path - CaptionReg style
- **Line 3**: Last backup timestamp - CaptionReg style
  - Format: "Last Backup on HH:mm MMM dd, yyyy"
- **Line 4**: Schedule and retain information - CaptionReg style
  - Format: "Schedule: [schedule] Retain: [retain]"
- **Padding**: 20dp horizontal, 24dp top margin
- **Text Alignment**: Center-aligned
- **Spacing**: 8dp after folder name, 4dp between subsequent lines

#### Folder Snapshots List
**Section Header:**
- **Label**: "Snapshots" - Title2 style
- **Padding**: 20dp horizontal
- **Top Margin**: 40dp from header section

**List Item Design:**
- **Line 1**: Snapshot hash (short) - Body17 style
- **Line 2**: Date and repository name - CaptionReg style
  - Format: "HH:mm MMM dd, yyyy repo_name"
- **Spacing**: 4dp between lines
- **Padding**: 20dp horizontal, 16dp vertical
- **Min Height**: 64dp
- **Dividers**: Hidden when only one snapshot is present

#### Repository Details Header
**Design:**
- **Line 1**: Repository name - Title1 style (extra bold, 34sp, line height 110%, letter spacing -0.5px)
- **Line 2**: Repository path/URL - CaptionReg style (medium, 13sp, black 40% opacity)
- **Padding**: 20dp horizontal, 24dp top margin
- **Text Alignment**: Center-aligned
- **Spacing**: 8dp after title, 4dp before path

#### Repository Snapshots List
**Section Header:**
- **Label**: "Snapshots" - Title2 style (bold, 22sp, line height 28sp)
- **Padding**: 20dp horizontal
- **Top Margin**: 40dp from header section

**List Item Design:**
- **Line 1**: Folder name (directory name only, not full path) - Body17 style (semibold, 17sp)
- **Line 2**: Date and snapshot hash - CaptionReg style (medium, 13sp, black 40% opacity)
  - Format: "HH:mm MMM dd, yyyy hash" (e.g., "06:34 Oct 25, 2025 a3f9c2b1")
- **Spacing**: 4dp between lines
- **Padding**: 20dp horizontal, 16dp vertical
- **Min Height**: 64dp
- **Dividers**: Hidden when only one snapshot is present

#### Snapshot Details Header
**Design:**
- **Line 1**: Snapshot hash (short) - Title1 style (extra bold, 34sp, line height 110%, letter spacing -0.5px)
- **Line 2**: Hostname and full folder path - CaptionReg style (medium, 13sp, black 40% opacity)
  - Format: "hostname /full/path/to/folder"
- **Line 3**: Creation date - CaptionReg style (medium, 13sp, black 40% opacity)
  - Format: "Created on HH:mm MMM dd, yyyy" (e.g., "Created on 06:34 Oct 25, 2025")
- **Padding**: 20dp horizontal, 24dp top margin
- **Text Alignment**: Center-aligned
- **Spacing**: 8dp after hash, 4dp between subsequent lines

#### Snapshot Files List
**Section Header:**
- **Label**: "Files" - Title2 style (bold, 22sp, line height 28sp)
- **Padding**: 20dp horizontal
- **Top Margin**: 40dp from header section

**List Item Design:**
- **Single Line**: File name with relative path - Body17 style (semibold, 17sp)
- **No Date**: Date information removed for cleaner look
- **Padding**: 20dp horizontal, 16dp vertical
- **Min Height**: 56dp
- **Ellipsize**: End (truncate long file names with "...")
- **Skeleton**: Uses `skeleton_item_file` (no icon) with 5 items during loading

### Toolbar Style
Style: `Widget.App.Toolbar`
- Zero elevation for modern flat design
- Title automatically styled with bold Plus Jakarta Sans

## Backup Progress Section

### Overview
The backup progress section appears at the bottom of the folder detail view, providing real-time feedback on backup operations with a modern, clean design.

### Progress Bar
- **Height**: 14dp
- **Color**: Purple (`#9322C8`) for active progress
- **Background**: `#E0E0E0` (light) / `#424242` (dark)
- **Corner Radius**: 7dp (fully rounded ends)
- **Behavior**: Animates smoothly from 0-100%

### Button
- **Style**: `MaterialButton` with purple background
- **Color**: 
  - Enabled: `#9322C8` (purple_main)
  - Disabled: `#CCCCCC` (light gray) / `#666666` (dark gray for dark mode)
- **Text**: "Backup Now" in white
- **Text Size**: 16sp, semibold (bold)
- **Shape**: Fully rounded (24dp corner radius)
- **Padding**: 32dp horizontal, 14dp vertical, 48dp minimum height
- **Text Style**: Sentence case (not all caps)
- **State**: Automatically grays out when disabled during backup

### States

#### 1. Idle State
**Appearance:**
- Progress bar at 0%
- Purple "Backup Now" button (enabled)
- No status information displayed

**Behavior:**
- Button click starts backup
- Long press on button does nothing

#### 2. Starting State
**Appearance:**
- Progress bar at 0%
- Disabled "Backup Now" button
- Small loading spinner (20dp) shown to the left of button
- Loading spinner tint: White

**Behavior:**
- All interactions disabled
- Brief state before progress begins

#### 3. In Progress State
**Appearance:**
- Progress bar animates from 0-100% based on actual progress
- Disabled "Backup Now" button
- Status container visible on left side (horizontal layout):
  - **Icon**: Upload cloud icon (20dp)
    - Icon: `ic_backup_upload` (`#938AA5` color)
    - Icon-to-text spacing: 8dp
  - **Text (two lines stacked vertically under icon)**:
    - **Line 1**: "13 / 25 Files" (Body15 style)
    - **Line 2**: "7.83MB / 56.23MB" (Body15 style)
    - Both lines: 15sp, semibold, `#938AA5` color, 130% line height

**Behavior:**
- Button disabled
- Long press on button shows cancel confirmation dialog
- Progress updates in real-time

#### 4. Completed State
**Appearance:**
- Progress bar at 100%
- Enabled "Backup Now" button
- Status container visible on left side (horizontal layout):
  - **Icon**: Checkmark icon (20dp)
    - Icon: `ic_backup_complete` (`#938AA5` color)
    - Icon-to-text spacing: 8dp
  - **Text (two lines stacked vertically under icon)**:
    - **Line 1**: "15 / 15 Files" (Body15 style)
    - **Line 2**: "56.23MB / 56.23MB" (Body15 style)
    - Both lines: 15sp, semibold, `#938AA5` color, 130% line height

**Behavior:**
- Button enabled for next backup
- Status persists until next backup starts

#### 5. Error State
**Appearance:**
- Progress bar at 0%
- Enabled "Backup Now" button
- Error message shown below button (CaptionReg, red color)
- No status container

**Behavior:**
- Button enabled to retry
- Clicking error message shows full error dialog

### Layout Structure
```
[===========] ← Progress bar (14dp height)

[☁] 13 / 25 Files    [Backup Now] ← Icon + Status + Button
    7.83MB / 56.23MB

Error message here (if any)
```

### Spacing
- **Container Padding**: 20dp horizontal, 16dp top, 20dp bottom
- **Progress to Content**: 16dp margin
- **Status to Button**: 16dp margin
- **Error Message**: 12dp top margin

### Icons
1. **Upload Icon** (`ic_backup_upload`):
   - Size: 20dp × 20dp
   - Color: `#938AA5` (backup_status_color)
   - Usage: During backup progress

2. **Complete Icon** (`ic_backup_complete`):
   - Size: 20dp × 20dp
   - Color: `#938AA5` (backup_status_color)
   - Usage: After backup completes

### Typography
- **Files Count**: Body15 (15sp, semibold, `#938AA5`, 130% line height)
- **Size Progress**: Body15 (15sp, semibold, `#938AA5`, 130% line height)
- **Button Text**: 16sp, semibold (bold), white
- **Error Text**: CaptionReg in error red

## Loading States

### Skeleton Loader
**Component**: `SkeletonLoaderView`
- **Generic and Reusable**: Can be used throughout the app with custom layouts
- **Animation**: Soft pulse animation (750ms cycle) between two shades
  - Light mode: `#E0E0E0` ↔ `#F5F5F5`
  - Dark mode: `#424242` ↔ `#616161`
- **Shape**: Rounded corners (4dp radius)
- **Customization**: Configurable item count and layout via XML attributes

**Available Skeleton Layouts**:

1. **Snapshot List** (`skeleton_item_snapshot.xml`):
   - Line 1: 120dp × 17dp (folder/hash name)
   - Line 2: 220dp × 13dp (date and details)
   - Spacing: 4dp between lines
   - Padding: 20dp horizontal, 16dp vertical

2. **Two-Line with Icon** (`skeleton_item_two_line.xml`):
   - Icon: 24dp × 24dp
   - Line 1: 160dp × 17dp (title)
   - Line 2: 200dp × 13dp (details)
   - Icon margin: 16dp to text
   - Use for: Folder lists, repository lists

3. **Single-Line with Icon** (`skeleton_item_single_line.xml`):
   - Icon: 24dp × 24dp
   - Line: 180dp × 17dp (title)
   - Icon margin: 16dp to text
   - Min height: 56dp
   - Use for: Simple list items

4. **Single-Line (No Icon)** (`skeleton_item_file.xml`):
   - Line: 200dp × 17dp (filename)
   - No icon placeholder
   - Min height: 56dp
   - Padding: 20dp horizontal, 16dp vertical
   - Use for: File lists

**Usage Example**:
```xml
<org.dydlakcloud.resticopia.ui.common.SkeletonLoaderView
    android:id="@+id/skeleton_loader"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    app:itemCount="3"
    app:itemLayout="@layout/skeleton_item_snapshot" />
```
- Consistent across all activities

## Floating Action Buttons (FABs)

### Style
- **Background Color**: Purple (`#9322C8`)
- **Icon Color**: White (`#FFFFFF`)
- **Size**: Standard (56dp)
- **Margin**: 16dp from edges
- **Elevation**: Material default
- **Usage**: Add actions in folder and repository lists

## Implementation

### Activities with Toolbar
All major views (MainActivity, RepoActivity, FolderActivity, SnapshotActivity) implement the custom toolbar with:
- White/black background (theme-dependent)
- Bold title (TextAppearance.App.Toolbar.Title)
- Black/white navigation icons
- Zero elevation
- Back button where applicable

### Font Application
Fonts are applied globally via theme (`android:fontFamily` and `fontFamily` attributes) and specific text appearance styles for different contexts.
