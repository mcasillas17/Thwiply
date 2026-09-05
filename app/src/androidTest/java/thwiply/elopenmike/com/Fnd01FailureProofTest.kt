package thwiply.elopenmike.com

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Fnd01FailureProofTest {
    @Test
    fun deliberateInstrumentationAssertionFailsJob() {
        fail("FND-01 controlled negative proof: intentional instrumentation assertion failure")
    }
}
