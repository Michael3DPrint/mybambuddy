package io.github.michael3dprint.mybambuddy.domain.model

/** A command the app can send to a printer; one subclass per command, each sent as a single attempt. */
sealed class PrinterControlCommand {
    data object Pause : PrinterControlCommand()
    data object Resume : PrinterControlCommand()

    /** Server route: print/stop. The confirmation dialog lives in the UI. */
    data object Cancel : PrinterControlCommand()
    data object RefreshStatus : PrinterControlCommand()
    data object ClearHms : PrinterControlCommand()
    data object ClearPlate : PrinterControlCommand()

    /** 1=Silent, 2=Standard, 3=Sport, 4=Ludicrous */
    data class SetPrintSpeed(val mode: Int) : PrinterControlCommand()
    data class SetChamberLight(val on: Boolean) : PrinterControlCommand()

    /** Feeds filament from an AMS slot into the printer (design 33). [trayId] is the printer's own tray number for the slot. */
    data class AmsLoadFilament(val trayId: Int) : PrinterControlCommand()

    /** Unloads the filament fed from an AMS slot (design 33). [trayId] is the printer's own tray number for the slot. */
    data class AmsUnloadFilament(val trayId: Int) : PrinterControlCommand()
}
