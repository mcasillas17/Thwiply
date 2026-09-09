package thwiply.elopenmike.com.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.llm.model.ArtifactDefect
import thwiply.elopenmike.com.llm.model.ArtifactRemovalFailure
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.model.UnusableActivationRecord
import thwiply.elopenmike.com.llm.provider.FailureKind
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.InferenceFailure
import thwiply.elopenmike.com.llm.provider.ProviderReadiness
import thwiply.elopenmike.com.llm.provider.RetryAction
import thwiply.elopenmike.com.llm.provider.rejectionKind
import thwiply.elopenmike.com.llm.provider.retryAction

class ProviderUiTest {
    @Test fun `checking copy is shared and names only the selected provider`() {
        val checking = ProviderReadiness.Checking
        assertEquals(R.string.provider_loading, checking.message(null, busy = true))
        assertEquals(R.string.qwen_verifying, checking.message(ModelProvider.QWEN, busy = true))
        assertEquals(R.string.nano_checking, checking.message(ModelProvider.GEMINI_NANO, busy = true))
        assertEquals(R.string.nano_check_needed, checking.message(ModelProvider.GEMINI_NANO, busy = false))
    }

    @Test fun `every artifact state has its own safe user message`() {
        val preset = ModelPreset.QWEN_2_5_1_5B
        assertEquals(R.string.qwen_verifying, ModelArtifactState.Verifying.message())
        assertEquals(R.string.settings_no_qwen, ModelArtifactState.Missing.message())
        assertEquals(R.string.qwen_verified, ModelArtifactState.Ready(preset).message())
        assertEquals(
            R.string.qwen_corrupt,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH).message(),
        )
        assertEquals(
            R.string.qwen_corrupt,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH).message(),
        )
        // A record whose file is gone must not be described as damaged bytes to remove.
        assertEquals(
            R.string.qwen_corrupt_file_missing,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING).message(),
        )
        assertEquals(R.string.qwen_discard, ArtifactDefect.DIGEST_MISMATCH.discardLabel())
        assertEquals(R.string.qwen_discard_record, ArtifactDefect.FILE_MISSING.discardLabel())
        assertEquals(R.string.qwen_removing, ModelArtifactState.Removing.message())
        assertEquals(
            R.string.qwen_read_failed,
            ModelArtifactState.Failed(java.io.IOException("unreadable")).message(),
        )
        // A record that read fine but names nothing approved is never called unreadable.
        val record = ModelArtifactState.Failed(UnusableActivationRecord("unapproved id"))
        assertEquals(R.string.qwen_record_unusable, record.message())
        assertNotEquals(R.string.qwen_read_failed, record.message())
    }

    @Test fun `corruption and storage failure never share the not-installed copy`() {
        val corrupt = ProviderReadiness.Failed(InferenceFailure(FailureKind.MODEL_CORRUPT))
        val storage = ProviderReadiness.Failed(InferenceFailure(FailureKind.MODEL_STORAGE))
        assertEquals(R.string.inference_model_corrupt, corrupt.message(ModelProvider.QWEN, busy = false))
        assertEquals(R.string.qwen_read_failed, storage.message(ModelProvider.QWEN, busy = false))
        assertEquals(
            R.string.qwen_removing,
            ProviderReadiness.Removing.message(ModelProvider.QWEN, busy = false),
        )
        assertNotEquals(R.string.lab_missing, corrupt.message(ModelProvider.QWEN, busy = false))
        assertNotEquals(R.string.settings_no_qwen, corrupt.message(ModelProvider.QWEN, busy = false))
        val unusableRecord = ProviderReadiness.Failed(InferenceFailure(FailureKind.MODEL_RECORD))
        assertEquals(R.string.qwen_record_unusable, unusableRecord.message(ModelProvider.QWEN, busy = false))
        assertNotEquals(
            R.string.qwen_read_failed,
            unusableRecord.message(ModelProvider.QWEN, busy = false),
        )
    }

    @Test fun `the settings storage label never calls absent weights installed`() {
        val preset = ModelPreset.QWEN_2_5_1_5B
        assertEquals(
            R.string.settings_model_record_only,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING).storageLabel(),
        )
        assertEquals(
            R.string.settings_model_damaged,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH).storageLabel(),
        )
        assertEquals(
            R.string.settings_model_damaged,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH).storageLabel(),
        )
        assertEquals(R.string.settings_model_installed, ModelArtifactState.Ready(preset).storageLabel())
        assertEquals(R.string.settings_no_qwen, ModelArtifactState.Missing.storageLabel())
        assertEquals(R.string.settings_model_verifying, ModelArtifactState.Verifying.storageLabel())
        assertEquals(R.string.settings_model_removing, ModelArtifactState.Removing.storageLabel())
        // Settings states the condition tersely; it hosts no recovery control to point at.
        assertEquals(
            R.string.settings_model_record_unusable,
            ModelArtifactState.Failed(UnusableActivationRecord("unapproved id")).storageLabel(),
        )
        assertEquals(
            R.string.settings_model_removal_incomplete,
            ModelArtifactState.Failed(ArtifactRemovalFailure("busy")).storageLabel(),
        )
        assertEquals(
            R.string.settings_model_unreadable,
            ModelArtifactState.Failed(java.io.IOException("unreadable")).storageLabel(),
        )
        assertNotEquals(
            R.string.settings_model_installed,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING).storageLabel(),
        )
    }

    @Test fun `only failures a retry can clear offer a retry control`() {
        val preset = ModelPreset.QWEN_2_5_1_5B
        // Artifact rejections are cleared by revalidating.
        assertEquals(
            RetryAction.REVALIDATE,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH).rejectionKind()!!.retryAction,
        )
        assertEquals(
            RetryAction.REVALIDATE,
            ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING).rejectionKind()!!.retryAction,
        )
        assertEquals(
            RetryAction.REVALIDATE,
            ModelArtifactState.Failed(java.io.IOException("unreadable")).rejectionKind()!!.retryAction,
        )
        // These two cannot be cleared by any retry, so no surface may offer one.
        assertEquals(
            RetryAction.NONE,
            ModelArtifactState.Failed(UnusableActivationRecord("unapproved")).rejectionKind()!!.retryAction,
        )
        assertEquals(RetryAction.NONE, FailureKind.PREFERENCE.retryAction)
        assertEquals(RetryAction.PREPARE, FailureKind.RUNTIME.retryAction)
    }

    @Test fun `a revalidation control never borrows the initialization label`() {
        assertEquals(R.string.qwen_verify_again, FailureKind.MODEL_CORRUPT.retryLabel())
        assertEquals(R.string.qwen_verify_again, FailureKind.MODEL_FILE_MISSING.retryLabel())
        assertEquals(R.string.qwen_verify_again, FailureKind.MODEL_STORAGE.retryLabel())
        assertEquals(R.string.lab_retry, FailureKind.RUNTIME.retryLabel())
        assertNotEquals(R.string.lab_retry, FailureKind.MODEL_CORRUPT.retryLabel())
    }

    @Test fun `healthy artifact states are not failures at all`() {
        val preset = ModelPreset.QWEN_2_5_1_5B
        assertNull(ModelArtifactState.Verifying.rejectionKind())
        assertNull(ModelArtifactState.Missing.rejectionKind())
        assertNull(ModelArtifactState.Ready(preset).rejectionKind())
        assertNull(ModelArtifactState.Removing.rejectionKind())
        // A discard that could not finish is neither a read failure nor an unusable record.
        assertEquals(
            FailureKind.MODEL_REMOVAL,
            ModelArtifactState.Failed(ArtifactRemovalFailure("busy")).rejectionKind(),
        )
        assertEquals(
            R.string.qwen_removal_failed,
            ModelArtifactState.Failed(ArtifactRemovalFailure("busy")).message(),
        )
    }
}
