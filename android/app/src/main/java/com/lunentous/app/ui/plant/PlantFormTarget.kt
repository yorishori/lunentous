package com.lunentous.app.ui.plant

import com.lunentous.app.data.local.entity.PlantEntity

/** Which mode the shared PlantFormSheet is open in -- owned by MainScaffold
 * since both the dashboard's "Add plant" FAB and the (future) plant detail
 * screen's edit action need to open the same sheet instance. */
sealed interface PlantFormTarget {
    data object Create : PlantFormTarget
    data class Edit(val plant: PlantEntity) : PlantFormTarget
}
