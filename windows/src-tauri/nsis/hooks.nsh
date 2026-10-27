; Uninstall hooks for the NSIS installer.
;
; The app keeps its inbox and log in %LOCALAPPDATA%\Tokkip; the installer never
; recorded them, so the default uninstaller would leave them behind.
;
; Claude Code's own settings.json is deliberately NOT touched here: it belongs to
; the user, it may contain hooks from other tools, and rewriting somebody's
; config from an uninstaller with no diff and no consent is exactly what the rest
; of this app goes out of its way not to do. A relay that is gone exits 0 without
; printing anything, so a leftover entry costs nothing beyond a dead path.

!macro NSIS_HOOK_PREUNINSTALL
  RMDir /r "$LOCALAPPDATA\Tokkip\bin"
  RMDir /r "$LOCALAPPDATA\Tokkip\inbox"
  Delete "$LOCALAPPDATA\Tokkip\tokkip.log"
!macroend
