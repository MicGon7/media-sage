# MS-156: Rename App from "The Media Sage" to "The Courage Post"

## What Changed

Replaced every user-visible occurrence of "The Media Sage" with "The Courage Post" across string resources and the iOS Info.plist.

## Files Updated

| File | Change |
|------|--------|
| `composeApp/src/commonMain/composeResources/values/strings.xml` | `app_name` and `title_home` strings |
| `composeApp/src/androidMain/res/values/strings.xml` | Android `app_name` string |
| `iosApp/iosApp/Info.plist` | Added `CFBundleDisplayName` key with "The Courage Post" |

## Scope Notes

- No package names, class names, or code identifiers changed — purely user-visible strings.
- iOS previously had no `CFBundleDisplayName` entry in `Info.plist`, meaning the home screen label defaulted to the Xcode `PRODUCT_NAME` (`MediaSage`). Added `CFBundleDisplayName` explicitly to show "The Courage Post" on the iOS home screen.
- CLAUDE.md project name references were intentionally left unchanged (internal documentation).
