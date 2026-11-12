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

; ── Tokkip's own setup window ─────────────────────────────────────────────────
;
; Instead of the classic pages, the installer runs Tokkip's setup window
; (src-tauri/src/setup.rs, setup.html), like Tokkip Phone's: tokkip.exe is
; unpacked to a temporary folder as "Tokkip Setup.exe" and run with
; `--setup --installer <this setup.exe>`; its Install button runs this same
; setup.exe again with /S. The uninstaller does the same with a copy of the
; installed tokkip.exe and `--remove`. The classic pages are still there for
; /P, /UPDATE, a PC without WebView2 (they install it), or if the window can't
; open (it exits with 3).
;
; This file is included before the template defines its own names, so the
; built tokkip.exe is found from here, and /P and /UPDATE are read again.

!define TOKKIP_BUILT_EXE "${__FILEDIR__}\..\..\target\release\tokkip.exe"
!define MUI_CUSTOMFUNCTION_GUIINIT TokkipSetupWindow
!define MUI_CUSTOMFUNCTION_UNGUIINIT un.TokkipRemoveWindow

; Leaves "1" on the stack when WebView2 is there to draw the window.
!macro TokkipHasWebView2
  ReadRegStr $0 HKLM "SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}" "pv"
  ${If} $0 == ""
  ${OrIf} $0 == "0.0.0.0"
    ReadRegStr $0 HKCU "Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}" "pv"
  ${EndIf}
  ${If} $0 == ""
  ${OrIf} $0 == "0.0.0.0"
    Push "0"
  ${Else}
    Push "1"
  ${EndIf}
!macroend

Function TokkipSetupWindow
  ClearErrors
  ${GetOptions} $CMDLINE "/P" $0
  ${IfNot} ${Errors}
    Return
  ${EndIf}
  ClearErrors
  ${GetOptions} $CMDLINE "/UPDATE" $0
  ${IfNot} ${Errors}
    Return
  ${EndIf}
  !insertmacro TokkipHasWebView2
  Pop $0
  ${If} $0 != "1"
    Return
  ${EndIf}
  InitPluginsDir
  SetOutPath "$PLUGINSDIR"
  File "/oname=$PLUGINSDIR\Tokkip Setup.exe" "${TOKKIP_BUILT_EXE}"
  ClearErrors
  ExecWait '"$PLUGINSDIR\Tokkip Setup.exe" --setup --installer "$EXEPATH"' $0
  SetOutPath "$TEMP"
  ${If} ${Errors}
  ${OrIf} $0 = 3
    Return
  ${EndIf}
  Quit
FunctionEnd

Function un.TokkipRemoveWindow
  ClearErrors
  ${GetOptions} $CMDLINE "/P" $0
  ${IfNot} ${Errors}
    Return
  ${EndIf}
  ClearErrors
  ${GetOptions} $CMDLINE "/UPDATE" $0
  ${IfNot} ${Errors}
    Return
  ${EndIf}
  ${IfNot} ${FileExists} "$INSTDIR\tokkip.exe"
    Return
  ${EndIf}
  !insertmacro TokkipHasWebView2
  Pop $0
  ${If} $0 != "1"
    Return
  ${EndIf}
  InitPluginsDir
  CopyFiles /SILENT "$INSTDIR\tokkip.exe" "$PLUGINSDIR\Tokkip Uninstall.exe"
  ClearErrors
  ExecWait '"$PLUGINSDIR\Tokkip Uninstall.exe" --remove --uninstaller "$INSTDIR\uninstall.exe"' $0
  ${If} ${Errors}
  ${OrIf} $0 = 3
    Return
  ${EndIf}
  Quit
FunctionEnd
