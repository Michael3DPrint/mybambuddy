package io.github.michael3dprint.mybambuddy.ui.filament

/** The AMS slot actions the slot sheet can run, kept together so the activity passes one value to every screen that shows AMS slots. */
class SlotActions(val feed: AmsFilamentViewModel, val scan: SlotNfcScanViewModel, val sync: SlotWeightSyncViewModel)
