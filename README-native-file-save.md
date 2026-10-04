# Native File Save build

This build has no WebView. It uses native Android Views and stores history as JSON in the public Downloads collection through MediaStore.

The app automatically updates:
- Download/10000-years-character-dle-save.json
- Download/10000-years-character-dle-save.backup.json

A completed round is written immediately. In-progress guesses are also saved after every guess.
