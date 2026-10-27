// Tokkip runs without a console window: Tokkip is the whole UI.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    tokkip_lib::run()
}
