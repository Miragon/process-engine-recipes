package io.miragon.tasklist.order.payment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RetryBackoffTest {

    @Test
    fun `doubles the wait as the retries run out`() {
        // Request payment has 5 retries, so the four retries after a failure have 4, 3, 2 and 1 retries left.
        assertThat((4 downTo 1).map(::retryBackoff))
            .containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(16))
    }

    @Test
    fun `waits 2 seconds while many retries are left`() {
        assertThat(retryBackoff(10)).isEqualTo(Duration.ofSeconds(2))
    }
}
