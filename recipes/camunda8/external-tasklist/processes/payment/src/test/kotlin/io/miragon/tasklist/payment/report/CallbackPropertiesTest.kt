package io.miragon.tasklist.payment.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CallbackPropertiesTest {

    private val callbacks = CallbackProperties(allowedHosts = setOf("order-app", "localhost"))

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://order-app:8081/api/v1/orders/ORD-1001/payment-result",
            "https://ORDER-APP/api/v1/orders/ORD-1001/payment-result",
            "http://localhost:8081/api/v1/orders/ORD-1001/payment-result",
        ],
    )
    fun `allows http and https URLs on an allowed host`(url: String) {
        assertThat(callbacks.allows(url)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://payment-db:5432/",
            "http://169.254.169.254/latest/meta-data/",
            "http://order-app.attacker.example/",
            "http://localhost@attacker.example/",
            "http://user@order-app:8081/",
            "ftp://order-app/result",
            "file:///etc/passwd",
            "//order-app/result",
            "/api/v1/orders/ORD-1001/payment-result",
            "not a url",
        ],
    )
    fun `refuses every other URL`(url: String) {
        assertThat(callbacks.allows(url)).isFalse()
    }
}
