package thwiply.elopenmike.com.testing

import java.io.File
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.model.ModelManager

/** The installed file of a manager whose artifact has already verified. */
internal fun ModelManager.verifiedArtifactFile(): File =
    verifiedFile(state.value as ModelArtifactState.Ready)
